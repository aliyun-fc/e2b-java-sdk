package dev.e2b.sdk.codeinterpreter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a code execution: the rich {@link Result} objects, the stdout/stderr
 * {@link Logs}, an optional {@link ExecutionError}, and the Jupyter execution count.
 */
public class Execution {

    private final List<Result> results = new ArrayList<Result>();
    private final Logs logs = new Logs();
    private ExecutionError error;
    private Integer executionCount;

    public List<Result> getResults() {
        return results;
    }

    public Logs getLogs() {
        return logs;
    }

    public ExecutionError getError() {
        return error;
    }

    void setError(ExecutionError error) {
        this.error = error;
    }

    public Integer getExecutionCount() {
        return executionCount;
    }

    void setExecutionCount(Integer executionCount) {
        this.executionCount = executionCount;
    }

    /** Convenience: the text of the main result, or {@code null} if there is none. */
    public String text() {
        for (Result r : results) {
            if (r.isMainResult() && r.getText() != null) {
                return r.getText();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Execution(results=" + results.size() + ", error=" + error
                + ", executionCount=" + executionCount + ")";
    }
}
