package dev.e2b.sdk.exception;

public class TemplateException extends SandboxException {
    public TemplateException(String message) {
        super(message);
    }

    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
