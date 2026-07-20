package dev.e2b.sdk.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.e2b.sdk.exception.AuthenticationException;
import dev.e2b.sdk.exception.RateLimitException;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.exception.SandboxNotFoundException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Low-level HTTP client for the e2b REST API.
 * Handles authentication, serialization, and error mapping.
 */
public class E2bApiClient {

    private static final Logger log = LoggerFactory.getLogger(E2bApiClient.class);
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final ConnectionConfig config;
    private final OkHttpClient http;
    public final ObjectMapper mapper;

    public E2bApiClient(ConnectionConfig config) {
        this.config = config;
        this.http = buildHttpClient(config);
        this.mapper = buildObjectMapper();
    }

    /**
     * Process-wide shared base client. Per OkHttp's guidance an {@code OkHttpClient} (and thus its
     * dispatcher thread pool + connection pool) should be shared across the whole application; the
     * per-config client returned by {@link #buildHttpClient} is derived from this base via
     * {@code newBuilder()}, so every sandbox and every static API call reuse the same pools instead
     * of allocating a fresh connection pool per {@code create}. Mirrors the shared/injectable client
     * pattern used by the OpenAI, Kubernetes and AgentScope Java SDKs.
     */
    private static volatile OkHttpClient sharedBase;

    private static OkHttpClient sharedBase() {
        OkHttpClient c = sharedBase;
        if (c == null) {
            synchronized (E2bApiClient.class) {
                c = sharedBase;
                if (c == null) {
                    c = new OkHttpClient();
                    sharedBase = c;
                }
            }
        }
        return c;
    }

    private OkHttpClient buildHttpClient(ConnectionConfig cfg) {
        long timeoutMs = (long) (cfg.getRequestTimeout() * 1000);
        OkHttpClient base = cfg.getHttpClient() != null ? cfg.getHttpClient() : sharedBase();
        // Derive a per-config client that keeps this config's timeouts while sharing the base's
        // dispatcher thread pool and connection pool.
        return base.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    private ObjectMapper buildObjectMapper() {
        // The sandbox-gateway control plane and envd protojson both use camelCase
        // (e.g. templateID, sandboxID, memoryMB, envVars, exitCode). We therefore keep
        // Jackson's default (lower camelCase) naming and rely on explicit @JsonProperty
        // on model fields whose wire name differs (Go-style acronyms like templateID).
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new JavaTimeModule());
    }

    // -------------------------------------------------------------------------
    // Core HTTP helpers
    // -------------------------------------------------------------------------

    public <T> T get(String path, Class<T> responseType) {
        return getWithResponse(path, null, responseType).getBody();
    }

    public <T> T get(String path, Map<String, String> queryParams, Class<T> responseType) {
        return getWithResponse(path, queryParams, responseType).getBody();
    }

    public <T> ApiResponse<T> getWithResponse(String path, Map<String, String> queryParams, Class<T> responseType) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(config.resolvedApiUrl() + path).newBuilder();
        if (queryParams != null) {
            queryParams.forEach(urlBuilder::addQueryParameter);
        }
        Request req = baseRequestForUrl(urlBuilder.build()).get().build();
        return executeWithResponse(req, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return postWithResponse(path, body, responseType).getBody();
    }

    public <T> ApiResponse<T> postWithResponse(String path, Object body, Class<T> responseType) {
        String json = serialize(body);
        RequestBody reqBody = RequestBody.create(json, JSON_MEDIA);
        Request req = baseRequest(path).post(reqBody).build();
        return executeWithResponse(req, responseType);
    }

    public <T> T post(String path, Class<T> responseType) {
        return postWithResponse(path, responseType).getBody();
    }

    public <T> ApiResponse<T> postWithResponse(String path, Class<T> responseType) {
        RequestBody reqBody = RequestBody.create(new byte[0]);
        Request req = baseRequest(path).post(reqBody).build();
        return executeWithResponse(req, responseType);
    }

    public boolean delete(String path) {
        return deleteWithResponse(path).getBody();
    }

    public ApiResponse<Boolean> deleteWithResponse(String path) {
        Request req = baseRequest(path).delete().build();
        try (Response resp = http.newCall(req).execute()) {
            handleErrors(resp);
            return new ApiResponse<>(resp.isSuccessful(), resp.headers());
        } catch (IOException e) {
            throw new SandboxException("HTTP delete failed: " + path, e);
        }
    }

    public <T> T put(String path, Object body, Class<T> responseType) {
        return putWithResponse(path, body, responseType).getBody();
    }

    public <T> ApiResponse<T> putWithResponse(String path, Object body, Class<T> responseType) {
        String json = serialize(body);
        RequestBody reqBody = RequestBody.create(json, JSON_MEDIA);
        Request req = baseRequest(path).put(reqBody).build();
        return executeWithResponse(req, responseType);
    }

    public <T> T patch(String path, Object body, Class<T> responseType) {
        return patchWithResponse(path, body, responseType).getBody();
    }

    public <T> ApiResponse<T> patchWithResponse(String path, Object body, Class<T> responseType) {
        String json = serialize(body);
        RequestBody reqBody = RequestBody.create(json, JSON_MEDIA);
        Request req = baseRequest(path).patch(reqBody).build();
        return executeWithResponse(req, responseType);
    }

    // -------------------------------------------------------------------------
    // Request builders
    // -------------------------------------------------------------------------

    private Request.Builder baseRequest(String path) {
        HttpUrl url = HttpUrl.parse(config.resolvedApiUrl() + path);
        return baseRequestForUrl(url);
    }

    private Request.Builder baseRequestForUrl(HttpUrl url) {
        String key = config.resolvedApiKey();
        if (key == null || key.isEmpty()) {
            throw new AuthenticationException("E2B API key is required. Set E2B_API_KEY env or pass apiKey to ConnectionConfig.");
        }
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("X-API-KEY", key)
                .header("Accept", "application/json");

        if (config.getIntegration() != null) {
            builder.header("X-E2B-Integration", config.getIntegration());
        }
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(builder::header);
        }
        if (config.getApiHeaders() != null) {
            config.getApiHeaders().forEach(builder::header);
        }
        return builder;
    }

    // -------------------------------------------------------------------------
    // Response handling
    // -------------------------------------------------------------------------

    private <T> T execute(Request req, Class<T> type) {
        return executeWithResponse(req, type).getBody();
    }

    private <T> ApiResponse<T> executeWithResponse(Request req, Class<T> type) {
        if (config.isDebug()) {
            log.debug("→ {} {}", req.method(), req.url());
        }
        try (Response resp = http.newCall(req).execute()) {
            handleErrors(resp);
            Headers headers = resp.headers();
            if (type == Void.class || type == null) {
                return new ApiResponse<>(null, headers);
            }
            ResponseBody body = resp.body();
            if (body == null) {
                return new ApiResponse<>(null, headers);
            }
            String json = body.string();
            if (config.isDebug()) {
                log.debug("← {} {}", resp.code(), json);
            }
            if (json.isEmpty()) {
                return new ApiResponse<>(null, headers);
            }
            return new ApiResponse<>(mapper.readValue(json, type), headers);
        } catch (IOException e) {
            throw new SandboxException("HTTP request failed", e);
        }
    }

    private void handleErrors(Response resp) throws IOException {
        if (resp.isSuccessful()) return;
        String body = resp.body() != null ? resp.body().string() : "";
        int code = resp.code();
        String requestId = ApiResponse.requestIdFrom(resp.headers());
        Map<String, String> headers = ApiResponse.headersAsMap(resp.headers());
        switch (code) {
            case 401:
                throw new AuthenticationException("Authentication failed: " + body, code, requestId, headers);
            case 404:
                throw new SandboxNotFoundException("Resource not found: " + body, code, requestId, headers);
            case 429:
                throw new RateLimitException("Rate limit exceeded: " + body, code, requestId, headers);
            default:
                throw new SandboxException("API error " + code + ": " + body, code, requestId, headers);
        }
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (IOException e) {
            throw new SandboxException("Failed to serialize request body", e);
        }
    }

    public OkHttpClient httpClient() {
        return http;
    }

    public ConnectionConfig getConfig() {
        return config;
    }
}
