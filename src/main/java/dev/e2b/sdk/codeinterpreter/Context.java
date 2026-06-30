package dev.e2b.sdk.codeinterpreter;

/** A persistent code-execution context (kernel) with its own state, language and working dir. */
public class Context {

    private final String id;
    private final String language;
    private final String cwd;

    public Context(String id, String language, String cwd) {
        this.id = id;
        this.language = language;
        this.cwd = cwd;
    }

    public String getId() {
        return id;
    }

    public String getLanguage() {
        return language;
    }

    public String getCwd() {
        return cwd;
    }

    @Override
    public String toString() {
        return "Context(id=" + id + ", language=" + language + ", cwd=" + cwd + ")";
    }
}
