package dev.e2b.sdk.exception;

public class SandboxException extends RuntimeException {
    public SandboxException(String message) { super(message); }
    public SandboxException(String message, Throwable cause) { super(message, cause); }
}
