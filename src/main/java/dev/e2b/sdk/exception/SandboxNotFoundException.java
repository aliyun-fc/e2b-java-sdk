package dev.e2b.sdk.exception;

import java.util.Map;

public class SandboxNotFoundException extends SandboxException {

    public SandboxNotFoundException(String message) {
        super(message);
    }

    public SandboxNotFoundException(String message, Integer statusCode, String requestId, Map<String, String> headers) {
        super(message, statusCode, requestId, headers);
    }
}
