package dev.e2b.sdk.client;

import lombok.Builder;
import lombok.Getter;
import okhttp3.OkHttpClient;

import java.util.Map;

/**
 * Configuration for connecting to the e2b API.
 */
@Getter
@Builder
public class ConnectionConfig {

    private static final String DEFAULT_DOMAIN = "e2b.app";
    private static final double DEFAULT_REQUEST_TIMEOUT = 60.0;
    private static final int ENVD_PORT = 49983;

    /** e2b API key. Defaults to E2B_API_KEY environment variable. */
    private final String apiKey;

    /** Explicit API/sandbox domain override, ahead of E2B_DOMAIN and API response domains. */
    private final String domain;

    /** Override the API base URL. Defaults to https://api.{domain} */
    private final String apiUrl;

    /** Override the sandbox base URL. */
    private final String sandboxUrl;

    /** Request timeout in seconds. Defaults to 60.0. */
    @Builder.Default
    private final double requestTimeout = DEFAULT_REQUEST_TIMEOUT;

    /** Additional headers to send with every API request. */
    private final Map<String, String> headers;

    /** Additional headers sent only to the API (not sandbox). */
    private final Map<String, String> apiHeaders;

    /** Extra headers forwarded to sandbox requests. */
    private final Map<String, String> extraSandboxHeaders;

    /** Integration identifier (e.g. "langchain", "crewai"). */
    private final String integration;

    /** Enable debug logging. Defaults to E2B_DEBUG env var. */
    @Builder.Default
    private final boolean debug = false;

    /** Whether to validate the API key on first use. */
    @Builder.Default
    private final boolean validateApiKey = true;

    /**
     * Optional pre-built {@link OkHttpClient} to reuse across all sandboxes and API calls.
     *
     * <p>When set, every {@code E2bApiClient} derives its client from this instance (via
     * {@code newBuilder()}), so the whole application shares one dispatcher thread pool and one
     * connection pool — the pattern recommended by OkHttp and used by the OpenAI / Kubernetes /
     * AgentScope Java SDKs. When left null, the SDK falls back to a process-wide shared client.
     */
    private final OkHttpClient httpClient;

    /**
     * Resolves the effective API key: constructor arg, then E2B_API_KEY env var.
     */
    public String resolvedApiKey() {
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        return System.getenv("E2B_API_KEY");
    }

    public String resolvedApiUrl() {
        if (apiUrl != null && !apiUrl.isEmpty()) {
            return apiUrl;
        }
        String envUrl = System.getenv("E2B_API_URL");
        if (envUrl != null && !envUrl.isEmpty()) {
            return envUrl;
        }
        return "https://api." + resolvedDomain();
    }

    /** Configured domain, or the default when no explicit override was supplied. */
    public String getDomain() {
        return domain != null && !domain.isEmpty() ? domain : DEFAULT_DOMAIN;
    }

    public String resolvedDomain() {
        return resolvedSandboxDomain(null);
    }

    /**
     * Explicit configuration wins over a gateway-provided domain. Only use the response
     * domain when neither the builder nor E2B_DOMAIN specifies an override.
     */
    public String resolvedSandboxDomain(String responseDomain) {
        if (domain != null && !domain.isEmpty()) {
            return domain;
        }
        String envDomain = System.getenv("E2B_DOMAIN");
        if (envDomain != null && !envDomain.isEmpty()) {
            return envDomain;
        }
        return responseDomain != null && !responseDomain.isEmpty() ? responseDomain : DEFAULT_DOMAIN;
    }

    public String getSandboxUrl(String sandboxId, String sandboxDomain) {
        if (sandboxUrl != null && !sandboxUrl.isEmpty()) {
            return sandboxUrl;
        }
        return "https://" + ENVD_PORT + "-" + sandboxId + "." + sandboxDomain;
    }

    public String getSandboxDirectUrl(String sandboxId, String sandboxDomain) {
        if (sandboxUrl != null && !sandboxUrl.isEmpty()) {
            return sandboxUrl;
        }
        return "https://" + sandboxId + "." + sandboxDomain + ":" + ENVD_PORT;
    }

    public String getHost(String sandboxId, String sandboxDomain, int port) {
        return port + "-" + sandboxId + "." + sandboxDomain;
    }
}
