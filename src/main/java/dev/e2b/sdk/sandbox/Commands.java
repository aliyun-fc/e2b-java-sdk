package dev.e2b.sdk.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.CommandExitException;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.ProcessInfo;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Commands module: run shell commands inside the sandbox.
 *
 * <p>Communicates with the sandbox envd over the Connect protocol
 * ({@code /process.Process/*}). {@code Start} is a server-streaming RPC that returns
 * enveloped {@code ProcessEvent} frames (start / data / end); the other methods are unary.
 */
@RequiredArgsConstructor
public class Commands {

    /** Unary Connect calls use plain JSON. */
    private static final MediaType CONNECT_JSON = MediaType.get("application/json");
    /** Server-streaming Connect calls use enveloped connect+json frames. */
    private static final MediaType CONNECT_STREAM_JSON = MediaType.get("application/connect+json");

    private static final int ENVELOPE_HEADER_LEN = 5;
    private static final int FLAG_END_STREAM = 0x02;

    private final E2bApiClient api;
    private final String envdUrl;
    private final String accessToken;

    /**
     * Run a shell command in the foreground (waits for completion).
     *
     * @param cmd            Shell command string
     * @param envs           Additional environment variables
     * @param user           Username to run as (default: envd default user)
     * @param cwd            Working directory
     * @param timeoutSeconds Command timeout in seconds (default: 60)
     * @param throwOnError   If true, throws CommandExitException on non-zero exit
     * @return CommandResult with stdout, stderr, exitCode
     */
    public CommandResult run(String cmd, Map<String, String> envs, String user,
                             String cwd, Integer timeoutSeconds, boolean throwOnError) {
        byte[] body = encodeEnvelope(serialize(buildStartRequest(cmd, envs, cwd)));

        Request.Builder builder = new Request.Builder()
                .url(envdUrl + "/process.Process/Start")
                .post(RequestBody.create(body, CONNECT_STREAM_JSON))
                .header("Connect-Protocol-Version", "1")
                .header("connect-content-encoding", "identity");
        applyAuth(builder, user);

        OkHttpClient client = api.httpClient().newBuilder()
                .readTimeout(timeoutSeconds != null ? timeoutSeconds : 60, TimeUnit.SECONDS)
                .build();

        try (Response resp = client.newCall(builder.build()).execute()) {
            if (!resp.isSuccessful()) {
                String err = resp.body() != null ? resp.body().string() : "";
                throw new SandboxException("Command start failed (" + resp.code() + "): " + err);
            }
            byte[] raw = resp.body() != null ? resp.body().bytes() : new byte[0];
            CommandResult result = parseProcessStream(raw);
            if (throwOnError && result.getExitCode() != 0) {
                throw new CommandExitException(result);
            }
            return result;
        } catch (IOException e) {
            throw new SandboxException("Command execution failed", e);
        }
    }

    /** Run a command with defaults (no throw on non-zero exit). */
    public CommandResult run(String cmd) {
        return run(cmd, null, null, null, 60, false);
    }

    /** Run a command, throwing CommandExitException if exit code != 0. */
    public CommandResult runOrThrow(String cmd) {
        return run(cmd, null, null, null, 60, true);
    }

    /**
     * Start a command in the background and return immediately with a {@link CommandHandle}.
     *
     * <p>Unlike {@link #run}, this does not wait for the process to exit. The returned handle exposes
     * the envd-assigned {@code pid} (so the process can be {@link #list() listed}, {@link #kill(int)
     * killed}, or fed via {@link #sendStdin(int, String)}) and lets callers await the final result.
     * The process keeps running while the Connect stream stays open; the SDK drains it on a daemon
     * thread until the process exits.
     */
    public CommandHandle runBackground(String cmd, Map<String, String> envs, String user, String cwd) {
        byte[] body = encodeEnvelope(serialize(buildStartRequest(cmd, envs, cwd)));

        Request.Builder builder = new Request.Builder()
                .url(envdUrl + "/process.Process/Start")
                .post(RequestBody.create(body, CONNECT_STREAM_JSON))
                .header("Connect-Protocol-Version", "1")
                .header("connect-content-encoding", "identity");
        applyAuth(builder, user);

        // Background processes can outlive any fixed read timeout, so disable it for this call.
        OkHttpClient client = api.httpClient().newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        Call call = client.newCall(builder.build());

        Response resp;
        try {
            resp = call.execute();
        } catch (IOException e) {
            throw new SandboxException("Background command start failed", e);
        }
        if (!resp.isSuccessful()) {
            String err = "";
            try {
                err = resp.body() != null ? resp.body().string() : "";
            } catch (IOException ignored) {
            }
            resp.close();
            throw new SandboxException("Background command start failed (" + resp.code() + "): " + err);
        }

        BufferedSource source = resp.body().source();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int[] exit = {0};
        String[] error = {null};

        int pid;
        try {
            pid = readUntilStart(source, stdout, stderr, exit, error);
        } catch (IOException e) {
            resp.close();
            throw new SandboxException("Failed to read background process start", e);
        }

        if (pid < 0) {
            // Process produced an end (or EOF) before any start event — already finished.
            resp.close();
            CommandResult finished = CommandResult.builder()
                    .stdout(stdout.toString()).stderr(stderr.toString())
                    .exitCode(exit[0]).error(error[0]).build();
            return new CommandHandle(-1, CompletableFuture.completedFuture(finished), call);
        }

        CompletableFuture<CommandResult> future = new CompletableFuture<CommandResult>();
        Thread drain = new Thread(() -> {
            try {
                drainRemaining(source, stdout, stderr, exit, error);
                future.complete(CommandResult.builder()
                        .stdout(stdout.toString()).stderr(stderr.toString())
                        .exitCode(exit[0]).error(error[0]).build());
            } catch (Exception e) {
                future.completeExceptionally(new SandboxException("Background command stream error", e));
            } finally {
                resp.close();
            }
        }, "e2b-cmd-" + pid);
        drain.setDaemon(true);
        drain.start();

        return new CommandHandle(pid, future, call);
    }

    /** Start a command in the background with defaults. */
    public CommandHandle runBackground(String cmd) {
        return runBackground(cmd, null, null, null);
    }

    private Map<String, Object> buildStartRequest(String cmd, Map<String, String> envs, String cwd) {
        Map<String, Object> process = new LinkedHashMap<String, Object>();
        process.put("cmd", "/bin/bash");
        process.put("args", Arrays.asList("-l", "-c", cmd));
        if (envs != null && !envs.isEmpty()) {
            process.put("envs", envs);
        }
        if (cwd != null) {
            process.put("cwd", cwd);
        }
        Map<String, Object> startRequest = new LinkedHashMap<String, Object>();
        startRequest.put("process", process);
        return startRequest;
    }

    /**
     * List all running processes inside the sandbox (unary RPC).
     */
    public List<ProcessInfo> list() {
        Request req = unaryRequest("/process.Process/List", "{}", null);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                String err = resp.body() != null ? resp.body().string() : "";
                throw new SandboxException("List processes failed (" + resp.code() + "): " + err);
            }
            String bodyStr = resp.body() != null ? resp.body().string() : "{}";
            JsonNode root = api.mapper.readTree(bodyStr);
            JsonNode processes = root.get("processes");
            List<ProcessInfo> result = new ArrayList<ProcessInfo>();
            if (processes != null && processes.isArray()) {
                for (JsonNode node : processes) {
                    ProcessInfo info = new ProcessInfo();
                    info.setPid(node.path("pid").asInt());
                    if (node.hasNonNull("tag")) {
                        info.setTag(node.get("tag").asText());
                    }
                    JsonNode config = node.get("config");
                    if (config != null) {
                        if (config.hasNonNull("cmd")) {
                            info.setCmd(config.get("cmd").asText());
                        }
                        if (config.hasNonNull("cwd")) {
                            info.setCwd(config.get("cwd").asText());
                        }
                        JsonNode args = config.get("args");
                        if (args != null && args.isArray()) {
                            List<String> argList = new ArrayList<String>();
                            for (JsonNode a : args) {
                                argList.add(a.asText());
                            }
                            info.setArgs(argList);
                        }
                        JsonNode envs = config.get("envs");
                        if (envs != null && envs.isObject()) {
                            Map<String, String> envMap = new LinkedHashMap<String, String>();
                            envs.fields().forEachRemaining(en -> envMap.put(en.getKey(), en.getValue().asText()));
                            info.setEnvs(envMap);
                        }
                    }
                    result.add(info);
                }
            }
            return result;
        } catch (IOException e) {
            throw new SandboxException("Failed to list processes", e);
        }
    }

    /**
     * Send data to the stdin of a running process (unary RPC).
     *
     * @param pid  Process ID
     * @param data Data to send
     */
    public void sendStdin(int pid, String data) {
        Map<String, Object> input = new LinkedHashMap<String, Object>();
        input.put("stdin", Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("process", singletonPid(pid));
        body.put("input", input);

        Request req = unaryRequest("/process.Process/SendInput", serializeString(body), null);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new SandboxException("sendStdin failed: " + resp.code());
            }
        } catch (IOException e) {
            throw new SandboxException("sendStdin failed", e);
        }
    }

    /**
     * Kill a running process by PID using SIGKILL (unary RPC).
     */
    public boolean kill(int pid) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("process", singletonPid(pid));
        body.put("signal", "SIGNAL_SIGKILL");

        Request req = unaryRequest("/process.Process/SendSignal", serializeString(body), null);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            return resp.isSuccessful();
        } catch (IOException e) {
            throw new SandboxException("kill failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Connect protocol helpers
    // -------------------------------------------------------------------------

    private CommandResult parseProcessStream(byte[] buf) throws IOException {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        int exitCode = 0;
        String error = null;

        int pos = 0;
        while (pos + ENVELOPE_HEADER_LEN <= buf.length) {
            int flags = buf[pos] & 0xFF;
            long len = ((buf[pos + 1] & 0xFFL) << 24)
                    | ((buf[pos + 2] & 0xFFL) << 16)
                    | ((buf[pos + 3] & 0xFFL) << 8)
                    | (buf[pos + 4] & 0xFFL);
            pos += ENVELOPE_HEADER_LEN;
            if (pos + len > buf.length) {
                break;
            }
            byte[] payload = Arrays.copyOfRange(buf, pos, (int) (pos + len));
            pos += (int) len;

            JsonNode message = api.mapper.readTree(payload);

            if ((flags & FLAG_END_STREAM) != 0) {
                if (message != null && message.has("error")) {
                    throw new SandboxException("Command stream error: " + message.get("error").toString());
                }
                break;
            }

            JsonNode event = message.get("event");
            if (event == null) {
                continue;
            }
            JsonNode data = event.get("data");
            if (data != null) {
                if (data.hasNonNull("stdout")) {
                    stdout.append(decodeBytes(data.get("stdout").asText()));
                }
                if (data.hasNonNull("stderr")) {
                    stderr.append(decodeBytes(data.get("stderr").asText()));
                }
            }
            JsonNode end = event.get("end");
            if (end != null) {
                exitCode = end.path("exitCode").asInt(0);
                if (end.hasNonNull("error")) {
                    error = end.get("error").asText();
                }
            }
        }

        return CommandResult.builder()
                .stdout(stdout.toString())
                .stderr(stderr.toString())
                .exitCode(exitCode)
                .error(error)
                .build();
    }

    /** Read frames until the process {@code start} event; returns its pid, or -1 if the stream ended first. */
    private int readUntilStart(BufferedSource source, StringBuilder out, StringBuilder err,
                               int[] exit, String[] error) throws IOException {
        while (true) {
            byte[] frame = readFrame(source);
            if (frame == null) {
                return -1;
            }
            int flags = lastFrameFlags;
            int pid = processFrame(flags, frame, out, err, exit, error);
            if (pid == FRAME_END) {
                return -1;
            }
            if (pid >= 0) {
                return pid;
            }
        }
    }

    /** Drain remaining frames until end-of-stream / EOF, accumulating output and exit code. */
    private void drainRemaining(BufferedSource source, StringBuilder out, StringBuilder err,
                                int[] exit, String[] error) throws IOException {
        while (true) {
            byte[] frame = readFrame(source);
            if (frame == null) {
                return;
            }
            int result = processFrame(lastFrameFlags, frame, out, err, exit, error);
            if (result == FRAME_END) {
                return;
            }
        }
    }

    private static final int FRAME_NO_PID = -1;
    private static final int FRAME_END = -2;

    /** Flags byte of the most recently read frame (paired with {@link #readFrame}). */
    private int lastFrameFlags;

    /** Read one Connect envelope frame, returning its payload bytes (and setting {@link #lastFrameFlags}). */
    private byte[] readFrame(BufferedSource source) throws IOException {
        if (source.exhausted()) {
            return null;
        }
        source.require(ENVELOPE_HEADER_LEN);
        lastFrameFlags = source.readByte() & 0xFF;
        long len = source.readInt() & 0xFFFFFFFFL;
        return source.readByteArray(len);
    }

    /**
     * Process a single decoded frame into the accumulators.
     *
     * @return the pid if this frame carried a {@code start} event, {@link #FRAME_END} for an
     *         end-of-stream frame, or {@link #FRAME_NO_PID} otherwise.
     */
    private int processFrame(int flags, byte[] payload, StringBuilder out, StringBuilder err,
                             int[] exit, String[] error) throws IOException {
        JsonNode message = api.mapper.readTree(payload);
        if ((flags & FLAG_END_STREAM) != 0) {
            if (message != null && message.has("error")) {
                throw new SandboxException("Command stream error: " + message.get("error").toString());
            }
            return FRAME_END;
        }
        JsonNode event = message != null ? message.get("event") : null;
        if (event == null) {
            return FRAME_NO_PID;
        }
        JsonNode data = event.get("data");
        if (data != null) {
            if (data.hasNonNull("stdout")) {
                out.append(decodeBytes(data.get("stdout").asText()));
            }
            if (data.hasNonNull("stderr")) {
                err.append(decodeBytes(data.get("stderr").asText()));
            }
        }
        JsonNode end = event.get("end");
        if (end != null) {
            exit[0] = end.path("exitCode").asInt(0);
            if (end.hasNonNull("error")) {
                error[0] = end.get("error").asText();
            }
        }
        JsonNode start = event.get("start");
        if (start != null) {
            return start.path("pid").asInt(FRAME_NO_PID);
        }
        return FRAME_NO_PID;
    }

    private static String decodeBytes(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    private static byte[] encodeEnvelope(byte[] payload) {
        byte[] framed = new byte[ENVELOPE_HEADER_LEN + payload.length];
        framed[0] = 0; // flags: not compressed, not end-of-stream
        int len = payload.length;
        framed[1] = (byte) ((len >>> 24) & 0xFF);
        framed[2] = (byte) ((len >>> 16) & 0xFF);
        framed[3] = (byte) ((len >>> 8) & 0xFF);
        framed[4] = (byte) (len & 0xFF);
        System.arraycopy(payload, 0, framed, ENVELOPE_HEADER_LEN, payload.length);
        return framed;
    }

    private Request unaryRequest(String rpcPath, String jsonBody, String user) {
        Request.Builder builder = new Request.Builder()
                .url(envdUrl + rpcPath)
                .post(RequestBody.create(jsonBody, CONNECT_JSON))
                .header("Connect-Protocol-Version", "1");
        applyAuth(builder, user);
        return builder.build();
    }

    private void applyAuth(Request.Builder builder, String user) {
        if (accessToken != null && !accessToken.isEmpty()) {
            builder.header("X-Access-Token", accessToken);
        }
        // envd selects the OS user via HTTP Basic auth ("<user>:"). Only sent when an
        // explicit user is requested; modern envd defaults to the standard user otherwise.
        if (user != null && !user.isEmpty()) {
            String basic = Base64.getEncoder().encodeToString((user + ":").getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        }
    }

    private static Map<String, Object> singletonPid(int pid) {
        Map<String, Object> selector = new LinkedHashMap<String, Object>();
        selector.put("pid", pid);
        return selector;
    }

    private byte[] serialize(Object obj) {
        try {
            return api.mapper.writeValueAsBytes(obj);
        } catch (IOException e) {
            throw new SandboxException("Failed to serialize process request", e);
        }
    }

    private String serializeString(Object obj) {
        try {
            return api.mapper.writeValueAsString(obj);
        } catch (IOException e) {
            throw new SandboxException("Failed to serialize process request", e);
        }
    }
}
