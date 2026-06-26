package dev.e2b.sdk.sandbox;

import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.CommandExitException;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.ProcessInfo;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Commands module: run shell commands inside the sandbox.
 * Communicates with the sandbox envd via Connect-RPC protocol.
 */
@RequiredArgsConstructor
public class Commands {

    private static final Logger log = LoggerFactory.getLogger(Commands.class);
    private static final MediaType CONNECT_JSON = MediaType.get("application/json");

    private final E2bApiClient api;
    private final String sandboxId;
    private final String envdUrl;
    private final String accessToken;

    /**
     * Run a shell command in the foreground (waits for completion).
     *
     * @param cmd            Shell command string
     * @param envs           Additional environment variables
     * @param user           Username to run as (default: "user")
     * @param cwd            Working directory
     * @param timeoutSeconds Command timeout in seconds (default: 60)
     * @param throwOnError   If true, throws CommandExitException on non-zero exit
     * @return CommandResult with stdout, stderr, exitCode
     */
    public CommandResult run(String cmd, Map<String, String> envs, String user,
                             String cwd, Integer timeoutSeconds, boolean throwOnError) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"cmd\":").append(quote(cmd));
        if (user != null)  json.append(",\"user\":").append(quote(user));
        if (cwd  != null)  json.append(",\"cwd\":").append(quote(cwd));
        if (envs != null && !envs.isEmpty()) {
            json.append(",\"envs\":{");
            envs.forEach((k, v) -> json.append(quote(k)).append(":").append(quote(v)).append(","));
            json.setLength(json.length() - 1);
            json.append("}");
        }
        json.append("}");

        Request req = buildConnectRequest("/process.Process/Start", json.toString());

        OkHttpClient client = api.httpClient().newBuilder()
                .readTimeout(timeoutSeconds != null ? timeoutSeconds : 60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        try (Response resp = client.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            CommandResult result = api.mapper.readValue(body, CommandResult.class);
            if (throwOnError && result.getExitCode() != 0) {
                throw new CommandExitException(result);
            }
            return result;
        } catch (IOException e) {
            throw new dev.e2b.sdk.exception.SandboxException("Command execution failed", e);
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
     * List all running processes inside the sandbox.
     */
    public List<ProcessInfo> list() {
        Request req = buildConnectRequest("/process.Process/List", "{}");
        try (Response resp = api.httpClient().newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            ProcessInfo[] arr = api.mapper.readValue(body, ProcessInfo[].class);
            return Arrays.asList(arr);
        } catch (IOException e) {
            throw new dev.e2b.sdk.exception.SandboxException("Failed to list processes", e);
        }
    }

    /**
     * Send data to the stdin of a running process.
     *
     * @param pid  Process ID
     * @param data Data to send
     */
    public void sendStdin(int pid, String data) {
        String json = "{\"pid\":" + pid + ",\"data\":" + quote(data) + "}";
        Request req = buildConnectRequest("/process.Process/SendInput", json);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new dev.e2b.sdk.exception.SandboxException("sendStdin failed: " + resp.code());
            }
        } catch (IOException e) {
            throw new dev.e2b.sdk.exception.SandboxException("sendStdin failed", e);
        }
    }

    /**
     * Kill a running process by PID.
     */
    public boolean kill(int pid) {
        String json = "{\"pid\":" + pid + ",\"signal\":9}";
        Request req = buildConnectRequest("/process.Process/SendSignal", json);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            return resp.isSuccessful();
        } catch (IOException e) {
            throw new dev.e2b.sdk.exception.SandboxException("kill failed", e);
        }
    }

    private Request buildConnectRequest(String rpcPath, String jsonBody) {
        return new Request.Builder()
                .url(envdUrl + rpcPath)
                .post(RequestBody.create(jsonBody, CONNECT_JSON))
                .header("Content-Type", "application/json")
                .header("Connect-Protocol-Version", "1")
                .header("X-Access-Token", accessToken != null ? accessToken : "")
                .build();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
