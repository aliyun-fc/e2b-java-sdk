package dev.e2b.sdk.codeinterpreter;

import java.util.ArrayList;
import java.util.List;

/** stdout / stderr captured during a code execution. */
public class Logs {

    private final List<String> stdout = new ArrayList<String>();
    private final List<String> stderr = new ArrayList<String>();

    public List<String> getStdout() {
        return stdout;
    }

    public List<String> getStderr() {
        return stderr;
    }

    @Override
    public String toString() {
        return "Logs(stdout=" + stdout + ", stderr=" + stderr + ")";
    }
}
