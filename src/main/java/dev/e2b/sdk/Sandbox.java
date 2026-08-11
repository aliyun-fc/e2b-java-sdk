package dev.e2b.sdk;

import dev.e2b.sdk.client.ApiResponse;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.exception.SandboxNotFoundException;
import dev.e2b.sdk.model.*;
import dev.e2b.sdk.sandbox.Commands;
import dev.e2b.sdk.sandbox.Filesystem;
import dev.e2b.sdk.sandbox.Git;
import lombok.Getter;

import java.time.Instant;
import java.util.*;

/**
 * E2B Sandbox — the main entry point for the Java SDK.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * try (Sandbox sandbox = Sandbox.create(
 *         ConnectionConfig.builder().apiKey("e2b_xxx").build())) {
 *
 *     CommandResult result = sandbox.commands().run("echo hello");
 *     System.out.println(result.getStdout()); // "hello\n"
 *
 *     sandbox.files().write("/home/user/hello.txt", "Hello, world!");
 *     String content = sandbox.files().read("/home/user/hello.txt");
 * }
 * }</pre>
 */
@Getter
public class Sandbox implements AutoCloseable {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    public static final int    MCP_PORT                = 50005;
    public static final int    DEFAULT_SANDBOX_TIMEOUT = 300;
    public static final String DEFAULT_TEMPLATE        = "base";

    /** HTTP header name required when {@link #trafficAccessToken} is non-null. */
    public static final String TRAFFIC_ACCESS_TOKEN_HEADER = "e2b-traffic-access-token";

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private final String           sandboxId;
    private final String           sandboxDomain;
    private final ConnectionConfig connectionConfig;
    private final E2bApiClient     apiClient;

    private final Commands   commands;
    private final Filesystem files;
    private final Git        git;

    private final String envdAccessToken;

    /**
     * Token for accessing the sandbox via the proxy when public traffic is restricted
     * ({@code network.allowPublicTraffic=false}). Null when public access is allowed.
     * External callers must send {@code e2b-traffic-access-token: <token>}.
     */
    private final String trafficAccessToken;

    /**
     * Request identifier ({@code X-Request-ID} or {@code x-fc-request-id}) of the create/connect
     * API call that produced this instance.
     */
    private final String requestId;

    /** Full HTTP response headers from the create/connect API call that produced this instance. */
    private final Map<String, String> headers;

    // -------------------------------------------------------------------------
    // Constructors (private — use static factory methods)
    // -------------------------------------------------------------------------

    private Sandbox(SandboxInfo info, ConnectionConfig config, E2bApiClient apiClient,
                    String accessToken, String trafficAccessToken,
                    String requestId, Map<String, String> headers) {
        this.sandboxId          = info.getSandboxId();
        // The create/connect responses carry `domain` only when the gateway overrides it
        // (it is nullable/omitempty); otherwise fall back to the configured E2B_DOMAIN,
        // matching the Python SDK (`sandbox_domain or connection_config.domain`).
        this.sandboxDomain      = (info.getSandboxDomain() != null && !info.getSandboxDomain().isEmpty())
                ? info.getSandboxDomain()
                : config.resolvedDomain();
        this.connectionConfig   = config;
        this.apiClient          = apiClient;
        this.envdAccessToken    = accessToken;
        this.trafficAccessToken = trafficAccessToken;
        this.requestId          = requestId;
        this.headers            = headers == null || headers.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));

        String envdUrl = config.getSandboxUrl(sandboxId, sandboxDomain);
        this.commands = new Commands(apiClient, envdUrl, accessToken, trafficAccessToken);
        this.files    = new Filesystem(apiClient, envdUrl, accessToken, trafficAccessToken);
        this.git      = new Git(commands);
    }

    // -------------------------------------------------------------------------
    // Static factory: create
    // -------------------------------------------------------------------------

    /**
     * Create a new sandbox from a template and return it.
     *
     * @param template Template ID or name (e.g. "base", "python")
     * @param config   Connection configuration
     * @param opts     Additional creation options (timeout, metadata, envs, …)
     * @return Running Sandbox instance; the create call's request id and headers are available via
     *         {@link #getRequestId()} and {@link #getHeaders()}
     */
    public static Sandbox create(String template, ConnectionConfig config, NewSandbox opts) {
        E2bApiClient api = new E2bApiClient(config);

        NewSandbox body = opts != null ? opts : NewSandbox.builder().build();
        if (template != null) body = copyWithTemplate(body, template);

        ApiResponse<SandboxInfo> response = api.postWithResponse("/sandboxes", body, SandboxInfo.class);
        SandboxInfo info = response.getBody();
        return new Sandbox(info, config, api, info.getEnvdAccessToken(), info.getTrafficAccessToken(),
                response.requestId(), response.headersAsMap());
    }

    public static Sandbox create(ConnectionConfig config) {
        return create(DEFAULT_TEMPLATE, config, null);
    }

    public static Sandbox create(String template, ConnectionConfig config) {
        return create(template, config, null);
    }

    public static Sandbox create(NewSandbox opts, ConnectionConfig config) {
        return create(null, config, opts);
    }

    // -------------------------------------------------------------------------
    // Static factory: connect to an existing sandbox
    // -------------------------------------------------------------------------

    /**
     * Connect to an already-running sandbox (or resume a paused sandbox).
     *
     * <p>Uses {@code POST /sandboxes/{sandboxID}/connect} per sandbox-gateway E2B API.
     */
    public static Sandbox connect(String sandboxId, ConnectionConfig config) {
        return connect(sandboxId, config, null);
    }

    /**
     * Connect to a sandbox, optionally setting a new timeout while resuming.
     *
     * @param sandboxId       Sandbox ID
     * @param config          Connection configuration
     * @param timeoutSeconds  Optional new timeout in seconds
     */
    public static Sandbox connect(String sandboxId, ConnectionConfig config, Integer timeoutSeconds) {
        E2bApiClient api = new E2bApiClient(config);
        Object body = timeoutSeconds != null
                ? ConnectSandbox.builder().timeout(timeoutSeconds).build()
                : Collections.emptyMap();
        ApiResponse<SandboxConnectResponse> response = api.postWithResponse(
                "/sandboxes/" + sandboxId + "/connect", body, SandboxConnectResponse.class);
        return fromConnectResponse(response.getBody(), config, api,
                response.requestId(), response.headersAsMap());
    }

    /**
     * Get sandbox info by ID without connecting to the data plane.
     *
     * @return sandbox details plus {@code requestId} from response headers
     */
    public static GetSandboxOutput getInfo(String sandboxId, ConnectionConfig config) {
        ApiResponse<SandboxInfo> response = new E2bApiClient(config)
                .getWithResponse("/sandboxes/" + sandboxId, null, SandboxInfo.class);
        return GetSandboxOutput.builder()
                .sandbox(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    // -------------------------------------------------------------------------
    // Instance lifecycle methods
    // -------------------------------------------------------------------------

    /**
     * Kill (terminate) this sandbox immediately.
     *
     * @return kill result plus {@code requestId} from response headers
     */
    public KillSandboxOutput kill() {
        // Cancel any open background-command streams first so their drain threads exit and the
        // underlying connections are released rather than lingering after the sandbox is gone.
        try {
            commands.closeActive();
        } catch (Exception ignored) {
        }
        ApiResponse<Boolean> response = apiClient.deleteWithResponse("/sandboxes/" + sandboxId);
        return KillSandboxOutput.builder()
                .killed(Boolean.TRUE.equals(response.getBody()))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public boolean release() {
        try {
            commands.closeActive();
        } catch (Exception ignored) {
            return false;
        }
        return true;
    }

    /**
     * Pause this sandbox (preserves state; can be resumed later).
     *
     * @return pause result plus {@code requestId} from response headers
     */
    public PauseSandboxOutput pause() {
        ApiResponse<Void> response = apiClient.postWithResponse(
                "/sandboxes/" + sandboxId + "/pause", Void.class);
        return PauseSandboxOutput.builder()
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Set the sandbox timeout.
     *
     * @param timeoutSeconds New timeout in seconds
     * @return result plus {@code requestId} from response headers
     */
    public SetSandboxTimeoutOutput setTimeout(int timeoutSeconds) {
        ApiResponse<Void> response = apiClient.postWithResponse(
                "/sandboxes/" + sandboxId + "/timeout",
                Collections.singletonMap("timeout", timeoutSeconds), Void.class);
        return SetSandboxTimeoutOutput.builder()
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Get the latest info for this sandbox.
     *
     * @return sandbox details plus {@code requestId} from response headers
     */
    public GetSandboxOutput getInfo() {
        ApiResponse<SandboxInfo> response = apiClient
                .getWithResponse("/sandboxes/" + sandboxId, null, SandboxInfo.class);
        return GetSandboxOutput.builder()
                .sandbox(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Get CPU / memory / disk metrics.
     *
     * @param start Optional start time
     * @param end   Optional end time
     * @return metrics plus {@code requestId} from response headers
     */
    public GetSandboxMetricsOutput getMetrics(Instant start, Instant end) {
        Map<String, String> params = new HashMap<>();
        if (start != null) params.put("start", String.valueOf(start.getEpochSecond()));
        if (end   != null) params.put("end",   String.valueOf(end.getEpochSecond()));
        ApiResponse<SandboxMetrics[]> response = apiClient.getWithResponse(
                "/sandboxes/" + sandboxId + "/metrics", params, SandboxMetrics[].class);
        SandboxMetrics[] arr = response.getBody();
        return GetSandboxMetricsOutput.builder()
                .metrics(arr != null ? Arrays.asList(arr) : Collections.emptyList())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public GetSandboxMetricsOutput getMetrics() {
        return getMetrics(null, null);
    }

    /**
     * Update network rules for this sandbox.
     *
     * @return result plus {@code requestId} from response headers
     */
    public UpdateSandboxNetworkOutput updateNetwork(SandboxNetworkUpdate network) {
        ApiResponse<Void> response = apiClient.putWithResponse(
                "/sandboxes/" + sandboxId + "/network", network, Void.class);
        return UpdateSandboxNetworkOutput.builder()
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Create a snapshot of this sandbox.
     *
     * @param name Optional snapshot name
     * @return snapshot details plus {@code requestId} from response headers
     */
    public CreateSnapshotOutput createSnapshot(String name) {
        Map<String, Object> body = new HashMap<>();
        if (name != null) body.put("name", name);
        ApiResponse<SnapshotInfo> response = apiClient.postWithResponse(
                "/sandboxes/" + sandboxId + "/snapshots", body, SnapshotInfo.class);
        return CreateSnapshotOutput.builder()
                .snapshot(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * List snapshots for this sandbox.
     */
    public ListSnapshotsOutput listSnapshots() {
        return listSnapshots(connectionConfig, sandboxId, null, null);
    }

    /**
     * Check whether the sandbox is currently running (live ping via {@link #getInfo()}).
     *
     * <p>{@link SandboxNotFoundException} (HTTP 404) is treated as not running. Other API
     * failures — including authentication, rate limiting, and 5xx — are rethrown so callers
     * do not mistake transient or auth errors for a stopped sandbox.
     */
    public boolean isRunning() {
        try {
            SandboxInfo info = getInfo().getSandbox();
            return info != null && SandboxState.RUNNING.equals(info.getState());
        } catch (SandboxNotFoundException e) {
            return false;
        }
    }

    /**
     * Get the public hostname for a port exposed by this sandbox.
     *
     * @param port Port number
     * @return Hostname string (e.g. "3000-abc123.sandbox.e2b.app")
     */
    public String getHost(int port) {
        return connectionConfig.getHost(sandboxId, sandboxDomain, port);
    }

    /**
     * Get the MCP server URL for this sandbox.
     */
    public String getMcpUrl() {
        return "https://" + getHost(MCP_PORT);
    }

    /**
     * Get a pre-signed download URL for a file inside the sandbox.
     *
     * @param path Path inside the sandbox
     * @param user Optional username
     * @return Download URL
     */
    public String downloadUrl(String path, String user) {
        return files.downloadUrl(path, user);
    }

    public String downloadUrl(String path) {
        return downloadUrl(path, null);
    }

    // -------------------------------------------------------------------------
    // Static methods: list / snapshot management
    // -------------------------------------------------------------------------

    private static final String HEADER_NEXT_TOKEN = "x-next-token";

    /**
     * List all sandboxes visible to the API key.
     *
     * @param config    Connection configuration
     * @param query     Optional filter (metadata / state)
     * @param limit     Page size
     * @param nextToken Pagination cursor from previous page
     * @return page of sandboxes plus {@code nextToken} / {@code requestId} from response headers
     */
    public static ListSandboxesOutput list(ConnectionConfig config,
                                           SandboxQuery query,
                                           Integer limit,
                                           String nextToken) {
        E2bApiClient api = new E2bApiClient(config);
        Map<String, String> params = new HashMap<>();
        if (limit     != null) params.put("limit", String.valueOf(limit));
        if (nextToken != null) params.put("nextToken", nextToken);
        if (query     != null) {
            // The gateway expects a single `metadata` query param whose value is itself a
            // url-encoded `k=v&k2=v2` string (parsed server-side via url.ParseQuery), matching
            // the Python SDK (quote each key/value, then urlencode). NOT `metadata[k]=v`.
            if (query.getMetadata() != null && !query.getMetadata().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> e : query.getMetadata().entrySet()) {
                    if (sb.length() > 0) sb.append('&');
                    sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
                }
                params.put("metadata", sb.toString());
            }
            if (query.getState() != null && !query.getState().isEmpty()) {
                // Map can only carry one state; for multi-state filters callers should page.
                // Single-state (the common case) is sent as-is.
                params.put("state", query.getState().get(0).getValue());
            }
        }
        ApiResponse<SandboxInfo[]> response = api.getWithResponse("/v2/sandboxes", params, SandboxInfo[].class);
        SandboxInfo[] arr = response.getBody();
        return ListSandboxesOutput.builder()
                .sandboxes(arr != null ? Arrays.asList(arr) : Collections.emptyList())
                .nextToken(response.header(HEADER_NEXT_TOKEN))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static ListSandboxesOutput list(ConnectionConfig config) {
        return list(config, null, null, null);
    }

    /**
     * Kill a sandbox by ID without instantiating a Sandbox object.
     *
     * @return kill result plus {@code requestId} from response headers
     */
    public static KillSandboxOutput kill(String sandboxId, ConnectionConfig config) {
        ApiResponse<Boolean> response = new E2bApiClient(config)
                .deleteWithResponse("/sandboxes/" + sandboxId);
        return KillSandboxOutput.builder()
                .killed(Boolean.TRUE.equals(response.getBody()))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Pause a sandbox by ID.
     *
     * @return pause result plus {@code requestId} from response headers
     */
    public static PauseSandboxOutput pause(String sandboxId, ConnectionConfig config) {
        ApiResponse<Void> response = new E2bApiClient(config)
                .postWithResponse("/sandboxes/" + sandboxId + "/pause", Void.class);
        return PauseSandboxOutput.builder()
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Set sandbox timeout by ID.
     *
     * @return result plus {@code requestId} from response headers
     */
    public static SetSandboxTimeoutOutput setTimeout(String sandboxId, int timeoutSeconds, ConnectionConfig config) {
        ApiResponse<Void> response = new E2bApiClient(config).postWithResponse(
                "/sandboxes/" + sandboxId + "/timeout",
                Collections.singletonMap("timeout", timeoutSeconds),
                Void.class);
        return SetSandboxTimeoutOutput.builder()
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * List snapshots visible to the API key.
     *
     * @param config    Connection configuration
     * @param sandboxId Optional source sandbox ID filter (null lists all)
     * @param limit     Optional page size
     * @param nextToken Optional pagination cursor
     * @return snapshots plus {@code nextToken} / {@code requestId} from response headers
     */
    public static ListSnapshotsOutput listSnapshots(ConnectionConfig config,
                                                    String sandboxId,
                                                    Integer limit,
                                                    String nextToken) {
        Map<String, String> params = new HashMap<>();
        if (sandboxId != null) params.put("sandboxID", sandboxId);
        if (limit     != null) params.put("limit", String.valueOf(limit));
        if (nextToken != null) params.put("nextToken", nextToken);
        ApiResponse<SnapshotInfo[]> response = new E2bApiClient(config)
                .getWithResponse("/snapshots", params, SnapshotInfo[].class);
        SnapshotInfo[] arr = response.getBody();
        return ListSnapshotsOutput.builder()
                .snapshots(arr != null ? Arrays.asList(arr) : Collections.emptyList())
                .nextToken(response.header(HEADER_NEXT_TOKEN))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static ListSnapshotsOutput listSnapshots(ConnectionConfig config) {
        return listSnapshots(config, null, null, null);
    }

    /**
     * Delete a snapshot by ID.
     *
     * @return delete result plus {@code requestId} from response headers
     */
    public static DeleteSnapshotOutput deleteSnapshot(String snapshotId, ConnectionConfig config) {
        ApiResponse<Boolean> response = new E2bApiClient(config)
                .deleteWithResponse("/templates/" + snapshotId);
        return DeleteSnapshotOutput.builder()
                .deleted(Boolean.TRUE.equals(response.getBody()))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    // -------------------------------------------------------------------------
    // AutoCloseable
    // -------------------------------------------------------------------------

    /**
     * Kills the sandbox when used in a try-with-resources block.
     */
    @Override
    public void close() {
        try {
            kill();
        } catch (Exception ignored) {
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new SandboxException("Failed to encode metadata filter", e);
        }
    }

    private static NewSandbox copyWithTemplate(NewSandbox src, String template) {
        NewSandbox.NewSandboxBuilder builder = NewSandbox.builder()
                .templateId(template)
                .templateName(src.getTemplateName())
                .timeout(src.getTimeout())
                .metadata(src.getMetadata())
                .envVars(src.getEnvVars())
                .secure(src.getSecure())
                .allowInternetAccess(src.getAllowInternetAccess())
                .autoPause(src.getAutoPause())
                .autoResume(src.getAutoResume())
                .network(src.getNetwork())
                .mcp(src.getMcp())
                .volumeMounts(src.getVolumeMounts());
        if (src.getTemplateId() == null && src.getTemplateName() == null) {
            builder.templateName(template);
        }
        return builder.build();
    }

    private static Sandbox fromConnectResponse(SandboxConnectResponse response,
                                               ConnectionConfig config,
                                               E2bApiClient api,
                                               String requestId,
                                               Map<String, String> headers) {
        SandboxInfo info = new SandboxInfo();
        info.setSandboxId(response.getSandboxId());
        info.setSandboxDomain(response.getSandboxDomain());
        info.setTemplateId(response.getTemplateId());
        info.setEnvdAccessToken(response.getEnvdAccessToken());
        info.setTrafficAccessToken(response.getTrafficAccessToken());
        info.setEnvdVersion(response.getEnvdVersion());
        return new Sandbox(info, config, api, response.getEnvdAccessToken(),
                response.getTrafficAccessToken(), requestId, headers);
    }
}
