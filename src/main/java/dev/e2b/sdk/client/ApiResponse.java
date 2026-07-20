package dev.e2b.sdk.client;

import okhttp3.Headers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP response body plus response headers.
 *
 * @param <T> deserialized response body type
 */
public final class ApiResponse<T> {

    private final T body;
    private final Headers headers;

    public ApiResponse(T body, Headers headers) {
        this.body = body;
        this.headers = headers == null ? Headers.of() : headers;
    }

    public T getBody() {
        return body;
    }

    public Headers getHeaders() {
        return headers;
    }

    /**
     * Response headers as a map (first value per name). Names keep their original casing
     * from the first occurrence of each header.
     */
    public Map<String, String> headersAsMap() {
        return headersAsMap(headers);
    }

    /** Returns the first header value for {@code name} (case-insensitive), or null. */
    public String header(String name) {
        return headers.get(name);
    }

    /**
     * Request identifier from response headers.
     * Prefers {@code X-Request-ID}; if absent, falls back to {@code x-fc-request-id}.
     * Returns null when neither is present.
     */
    public String requestId() {
        return requestIdFrom(headers);
    }

    /**
     * Convert OkHttp {@link Headers} to an unmodifiable map (first value per name).
     */
    public static Map<String, String> headersAsMap(Headers headers) {
        if (headers == null || headers.size() == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : headers.names()) {
            map.put(name, headers.get(name));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Prefers {@code X-Request-ID}; if absent, falls back to {@code x-fc-request-id}.
     */
    public static String requestIdFrom(Headers headers) {
        if (headers == null) {
            return null;
        }
        String requestId = headers.get("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = headers.get("x-fc-request-id");
        }
        return (requestId == null || requestId.isEmpty()) ? null : requestId;
    }
}
