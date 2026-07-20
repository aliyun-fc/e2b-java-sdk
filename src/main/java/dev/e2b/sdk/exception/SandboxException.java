package dev.e2b.sdk.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base exception for sandbox / control-plane API failures.
 * HTTP API errors may carry {@code statusCode}, {@code requestId}, and response {@code headers}.
 */
public class SandboxException extends RuntimeException {

    private final Integer statusCode;
    private final String requestId;
    private final Map<String, String> headers;

    public SandboxException(String message) {
        this(message, null, null, null, null);
    }

    public SandboxException(String message, Throwable cause) {
        this(message, cause, null, null, null);
    }

    public SandboxException(String message, Integer statusCode, String requestId, Map<String, String> headers) {
        this(message, null, statusCode, requestId, headers);
    }

    public SandboxException(String message, Throwable cause,
                            Integer statusCode, String requestId, Map<String, String> headers) {
        super(message, cause);
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.headers = copyHeaders(headers);
    }

    /** HTTP status code when this exception came from an API response; otherwise null. */
    public Integer getStatusCode() {
        return statusCode;
    }

    /**
     * Request identifier from the failed response ({@code X-Request-ID}, or
     * {@code x-fc-request-id} if absent); otherwise null.
     */
    public String getRequestId() {
        return requestId;
    }

    /** Response headers from the failed API call (empty when not from HTTP). */
    public Map<String, String> getHeaders() {
        return headers;
    }

    private static Map<String, String> copyHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }
}
