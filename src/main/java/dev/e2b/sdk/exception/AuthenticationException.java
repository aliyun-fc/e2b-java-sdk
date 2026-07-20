package dev.e2b.sdk.exception;

import java.util.Map;

/**
 * Thrown when API authentication fails (e.g. HTTP 401) or the API key is missing.
 */
public class AuthenticationException extends SandboxException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Integer statusCode, String requestId, Map<String, String> headers) {
        super(message, statusCode, requestId, headers);
    }
}
