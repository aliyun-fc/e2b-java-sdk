package dev.e2b.sdk.exception;
public class TimeoutException extends SandboxException {
    public TimeoutException(String message) { super(message); }
    public TimeoutException(String message, Throwable cause) { super(message, cause); }
}
