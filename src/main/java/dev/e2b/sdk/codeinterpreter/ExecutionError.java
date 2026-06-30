package dev.e2b.sdk.codeinterpreter;

/** An error raised while executing code (exception name, value and traceback). */
public class ExecutionError {

    private final String name;
    private final String value;
    private final String traceback;

    public ExecutionError(String name, String value, String traceback) {
        this.name = name;
        this.value = value;
        this.traceback = traceback;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getTraceback() {
        return traceback;
    }

    @Override
    public String toString() {
        return "ExecutionError(name=" + name + ", value=" + value + ")";
    }
}
