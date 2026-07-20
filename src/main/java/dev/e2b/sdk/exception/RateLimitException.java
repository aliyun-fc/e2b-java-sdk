package dev.e2b.sdk.exception;

import java.util.Map;

public class RateLimitException extends SandboxException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Integer statusCode, String requestId, Map<String, String> headers) {
        super(message, statusCode, requestId, headers);
    }
}
