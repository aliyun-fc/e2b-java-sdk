package dev.e2b.sdk.codeinterpreter;

import com.fasterxml.jackson.databind.JsonNode;
import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.exception.SandboxNotFoundException;
import dev.e2b.sdk.model.NewSandbox;
import okhttp3.*;
import okio.BufferedSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Code Interpreter — runs Python/JavaScript/etc. inside a sandbox via the in-sandbox Jupyter server
 * and returns rich {@link Execution} results (stdout/stderr, results with multiple representations,
 * errors).
 *
 * <p>Java equivalent of Python's {@code e2b_code_interpreter.Sandbox}. It wraps a regular
 * {@link Sandbox} (created from the {@code code-interpreter-v1} template by default) and talks to the
 * Jupyter HTTP server listening on port {@value #JUPYTER_PORT} inside the sandbox.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * try (CodeInterpreter ci = CodeInterpreter.create(config)) {
 *     Execution exec = ci.runCode("x = 1 + 2\nprint(x)");
 *     System.out.println(exec.getLogs().getStdout()); // [3\n]
 * }
 * }</pre>
 */
public class CodeInterpreter implements AutoCloseable {

    public static final String DEFAULT_TEMPLATE = "code-interpreter-v1";
    public static final int JUPYTER_PORT = 49999;
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final Sandbox sandbox;
    private final OkHttpClient http;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final String jupyterUrl;
    private final String accessToken;

    private CodeInterpreter(Sandbox sandbox) {
        this.sandbox = sandbox;
        this.mapper = sandbox.getApiClient().mapper;
        this.accessToken = sandbox.getEnvdAccessToken();
        ConnectionConfig cfg = sandbox.getConnectionConfig();
        String scheme = cfg.isDebug() ? "http" : "https";
        this.jupyterUrl = scheme + "://" + sandbox.getHost(JUPYTER_PORT);
        // A dedicated HTTP/1.1 client with no read timeout: code execution streams NDJSON and can
        // legitimately take a long time. Forcing HTTP/1.1 (matching the Python SDK) keeps a 1:1
        // TCP-to-request mapping so client disconnects reliably cancel server-side execution.
        this.http = sandbox.getApiClient().httpClient().newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();
    }

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /** Create a code-interpreter sandbox from the default {@code code-interpreter-v1} template. */
    public static CodeInterpreter create(ConnectionConfig config) {
        return new CodeInterpreter(Sandbox.create(DEFAULT_TEMPLATE, config));
    }

    /** Create a code-interpreter sandbox from a specific template (falls back to default if null). */
    public static CodeInterpreter create(String template, ConnectionConfig config) {
        return new CodeInterpreter(Sandbox.create(template != null ? template : DEFAULT_TEMPLATE, config));
    }

    /** Create a code-interpreter sandbox with full creation options. */
    public static CodeInterpreter create(String template, ConnectionConfig config, NewSandbox opts) {
        return new CodeInterpreter(Sandbox.create(template != null ? template : DEFAULT_TEMPLATE, config, opts));
    }

    /** Wrap an already-created/connected {@link Sandbox} (must be built from a code-interpreter image). */
    public static CodeInterpreter from(Sandbox sandbox) {
        return new CodeInterpreter(sandbox);
    }

    /** The underlying sandbox (for filesystem/commands access, host URLs, kill, etc.). */
    public Sandbox getSandbox() {
        return sandbox;
    }

    public String getSandboxId() {
        return sandbox.getSandboxId();
    }

    // -------------------------------------------------------------------------
    // run_code
    // -------------------------------------------------------------------------

    public Execution runCode(String code) {
        return runCode(code, null, null, null, null);
    }

    public Execution runCode(String code, String language) {
        return runCode(code, language, null, null, null);
    }

    /** Run code in an existing {@link Context} (language is taken from the context). */
    public Execution runCode(String code, Context context) {
        return runCode(code, null, context != null ? context.getId() : null, null, null);
    }

    /**
     * Run code.
     *
     * @param code           source to execute
     * @param language       language (python/javascript/…); mutually exclusive with contextId
     * @param contextId      id of a previously created {@link Context}; mutually exclusive with language
     * @param envs           extra environment variables for this execution
     * @param timeoutSeconds execution timeout in seconds (null → {@value #DEFAULT_TIMEOUT_SECONDS}, 0 → no timeout)
     */
    public Execution runCode(String code, String language, String contextId,
                             Map<String, String> envs, Integer timeoutSeconds) {
        if (language != null && contextId != null) {
            throw new IllegalArgumentException(
                    "Provide either language or context, but not both at the same time.");
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("code", code);
        payload.put("context_id", contextId);
        payload.put("language", language);
        payload.put("env_vars", envs);

        Request req = jupyterRequest("/execute")
                .post(RequestBody.create(serialize(payload),
                        MediaType.get("application/json; charset=utf-8")))
                .build();

        Execution execution = new Execution();
        try (Response resp = http.newCall(req).execute()) {
            throwIfError(resp);
            ResponseBody body = resp.body();
            if (body == null) {
                return execution;
            }
            BufferedSource source = body.source();
            String line;
            while ((line = source.readUtf8Line()) != null) {
                if (!line.isEmpty()) {
                    parseLine(mapper, execution, line);
                }
            }
            return execution;
        } catch (IOException e) {
            throw new SandboxException("Code execution request failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Context management
    // -------------------------------------------------------------------------

    public Context createCodeContext() {
        return createCodeContext(null, null);
    }

    /**
     * Create a new execution context (kernel).
     *
     * @param cwd      working directory (defaults to {@code /home/user} server-side)
     * @param language language of the context (defaults to Python server-side)
     */
    public Context createCodeContext(String cwd, String language) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        // The Jupyter server requires a language; default to Python (matching the documented
        // behavior "if not specified, defaults to Python").
        data.put("language", language != null ? language : "python");
        if (cwd != null) data.put("cwd", cwd);

        Request req = jupyterRequest("/contexts")
                .post(RequestBody.create(serialize(data),
                        MediaType.get("application/json; charset=utf-8")))
                .build();
        JsonNode node = sendForJson(req);
        return toContext(node);
    }

    public List<Context> listCodeContexts() {
        Request req = jupyterRequest("/contexts").get().build();
        JsonNode node = sendForJson(req);
        List<Context> contexts = new ArrayList<Context>();
        if (node != null && node.isArray()) {
            for (JsonNode c : node) {
                contexts.add(toContext(c));
            }
        }
        return contexts;
    }

    public void removeCodeContext(String contextId) {
        Request req = jupyterRequest("/contexts/" + contextId).delete().build();
        sendForJson(req);
    }

    public void removeCodeContext(Context context) {
        removeCodeContext(context.getId());
    }

    public void restartCodeContext(String contextId) {
        Request req = jupyterRequest("/contexts/" + contextId + "/restart")
                .post(RequestBody.create(new byte[0]))
                .build();
        sendForJson(req);
    }

    public void restartCodeContext(Context context) {
        restartCodeContext(context.getId());
    }

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------

    @Override
    public void close() {
        sandbox.close();
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private Request.Builder jupyterRequest(String path) {
        Request.Builder builder = new Request.Builder()
                .url(jupyterUrl + path)
                .header("Accept", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) {
            builder.header("X-Access-Token", accessToken);
        }
        String trafficToken = sandbox.getTrafficAccessToken();
        if (trafficToken != null && !trafficToken.isEmpty()) {
            builder.header(Sandbox.TRAFFIC_ACCESS_TOKEN_HEADER, trafficToken);
        }
        Map<String, String> extra = sandbox.getConnectionConfig().getExtraSandboxHeaders();
        if (extra != null) {
            for (Map.Entry<String, String> e : extra.entrySet()) {
                builder.header(e.getKey(), e.getValue());
            }
        }
        return builder;
    }

    private JsonNode sendForJson(Request req) {
        try (Response resp = http.newCall(req).execute()) {
            throwIfError(resp);
            ResponseBody body = resp.body();
            if (body == null) {
                return null;
            }
            String json = body.string();
            if (json.isEmpty()) {
                return null;
            }
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new SandboxException("Jupyter request failed: " + req.url(), e);
        }
    }

    /** Parse a sequence of NDJSON output lines (package-private for testing). */
    static Execution parseExecution(com.fasterxml.jackson.databind.ObjectMapper mapper,
                                    Iterable<String> lines) {
        Execution execution = new Execution();
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                parseLine(mapper, execution, line);
            }
        }
        return execution;
    }

    private static void parseLine(com.fasterxml.jackson.databind.ObjectMapper mapper,
                                  Execution execution, String line) {
        try {
            JsonNode node = mapper.readTree(line);
            JsonNode typeNode = node.get("type");
            if (typeNode == null) {
                return;
            }
            String type = typeNode.asText();
            if ("result".equals(type)) {
                execution.getResults().add(toResult(mapper, node));
            } else if ("stdout".equals(type)) {
                execution.getLogs().getStdout().add(textValue(node, "text"));
            } else if ("stderr".equals(type)) {
                execution.getLogs().getStderr().add(textValue(node, "text"));
            } else if ("error".equals(type)) {
                execution.setError(new ExecutionError(
                        textValue(node, "name"),
                        textValue(node, "value"),
                        textValue(node, "traceback")));
            } else if ("number_of_executions".equals(type)) {
                JsonNode count = node.get("execution_count");
                if (count != null && count.isNumber()) {
                    execution.setExecutionCount(count.intValue());
                }
            }
        } catch (IOException e) {
            throw new SandboxException("Failed to parse execution output line", e);
        }
    }

    private static Result toResult(com.fasterxml.jackson.databind.ObjectMapper mapper, JsonNode node) {
        Map<String, Object> formats = new LinkedHashMap<String, Object>();
        boolean mainResult = false;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            if ("type".equals(key)) {
                continue;
            }
            if ("is_main_result".equals(key)) {
                mainResult = entry.getValue().asBoolean(false);
                continue;
            }
            if ("extra".equals(key) && entry.getValue().isObject()) {
                Iterator<Map.Entry<String, JsonNode>> extra = entry.getValue().fields();
                while (extra.hasNext()) {
                    Map.Entry<String, JsonNode> ex = extra.next();
                    formats.put(ex.getKey(), mapper.convertValue(ex.getValue(), Object.class));
                }
                continue;
            }
            formats.put(key, mapper.convertValue(entry.getValue(), Object.class));
        }
        return new Result(formats, mainResult);
    }

    private Context toContext(JsonNode node) {
        if (node == null) {
            return null;
        }
        return new Context(
                textValue(node, "id"),
                textValue(node, "language"),
                textValue(node, "cwd"));
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (IOException e) {
            throw new SandboxException("Failed to serialize Jupyter request body", e);
        }
    }

    private void throwIfError(Response resp) throws IOException {
        if (resp.isSuccessful()) {
            return;
        }
        String body = resp.body() != null ? resp.body().string() : "";
        int code = resp.code();
        if (code == 404) {
            throw new SandboxNotFoundException("Code interpreter resource not found: " + body);
        }
        if (code == 502) {
            throw new SandboxException("502: " + body
                    + " (likely sandbox timeout; increase the sandbox timeout or call setTimeout)");
        }
        throw new SandboxException("Code interpreter error " + code + ": " + body);
    }
}
