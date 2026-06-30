package dev.e2b.sdk.codeinterpreter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single result of a Jupyter cell execution (the interactively-evaluated last line, or a
 * {@code display()} call). Mirrors the Python {@code e2b_code_interpreter.Result}: a result can carry
 * multiple representations (text, html, png, svg, json, chart, …).
 */
public class Result {

    private final Map<String, Object> formats;
    private final boolean mainResult;

    Result(Map<String, Object> formats, boolean mainResult) {
        this.formats = formats != null ? formats : new LinkedHashMap<String, Object>();
        this.mainResult = mainResult;
    }

    /** Whether this is the cell's main result (vs. an intermediate display call). */
    public boolean isMainResult() {
        return mainResult;
    }

    public String getText()       { return asString("text"); }
    public String getHtml()       { return asString("html"); }
    public String getMarkdown()   { return asString("markdown"); }
    public String getSvg()        { return asString("svg"); }
    public String getPng()        { return asString("png"); }
    public String getJpeg()       { return asString("jpeg"); }
    public String getPdf()        { return asString("pdf"); }
    public String getLatex()      { return asString("latex"); }
    public String getJavascript() { return asString("javascript"); }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getJson() {
        Object v = formats.get("json");
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getChart() {
        Object v = formats.get("chart");
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    /** Raw value for an arbitrary MIME/format key. */
    public Object get(String format) {
        return formats.get(format);
    }

    /** All representation formats present on this result. */
    public List<String> formats() {
        return new ArrayList<String>(formats.keySet());
    }

    private String asString(String key) {
        Object v = formats.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    @Override
    public String toString() {
        String text = getText();
        return text != null ? "Result(" + text + ")" : "Result(formats=" + formats.keySet() + ")";
    }
}
