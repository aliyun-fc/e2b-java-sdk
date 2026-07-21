package dev.e2b.sdk.codeinterpreter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A persistent code-execution context (kernel) with its own state, language and working dir. */
public class Context {

    private final String id;
    private final String language;
    private final String cwd;
    private String requestId;
    private Map<String, String> headers = Collections.emptyMap();

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    @Override
    public String toString() {
        return "Context(id=" + id + ", language=" + language + ", cwd=" + cwd + ")";
    }
}
