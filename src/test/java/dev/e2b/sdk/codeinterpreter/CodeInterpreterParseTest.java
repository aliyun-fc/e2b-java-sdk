package dev.e2b.sdk.codeinterpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Jupyter NDJSON output parser. Mirrors the message types produced by the
 * in-sandbox code-interpreter server (stdout/stderr/result/error/number_of_executions).
 */
class CodeInterpreterParseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStdoutResultAndExecutionCount() {
        Execution exec = CodeInterpreter.parseExecution(mapper, Arrays.asList(
                "{\"type\":\"number_of_executions\",\"execution_count\":1}",
                "{\"type\":\"stdout\",\"text\":\"3\\n\",\"timestamp\":1700000000000000000}",
                "{\"type\":\"result\",\"text\":\"3\",\"is_main_result\":true}"
        ));

        assertEquals(Integer.valueOf(1), exec.getExecutionCount());
        assertEquals(1, exec.getLogs().getStdout().size());
        assertEquals("3\n", exec.getLogs().getStdout().get(0));
        assertTrue(exec.getLogs().getStderr().isEmpty());
        assertNull(exec.getError());

        assertEquals(1, exec.getResults().size());
        Result main = exec.getResults().get(0);
        assertTrue(main.isMainResult());
        assertEquals("3", main.getText());
        assertEquals("3", exec.text());
    }

    @Test
    void parsesStderrAndError() {
        Execution exec = CodeInterpreter.parseExecution(mapper, Arrays.asList(
                "{\"type\":\"stderr\",\"text\":\"oops\",\"timestamp\":1}",
                "{\"type\":\"error\",\"name\":\"ValueError\",\"value\":\"bad\",\"traceback\":\"Traceback...\"}"
        ));

        assertEquals(Arrays.asList("oops"), exec.getLogs().getStderr());
        assertNotNull(exec.getError());
        assertEquals("ValueError", exec.getError().getName());
        assertEquals("bad", exec.getError().getValue());
        assertEquals("Traceback...", exec.getError().getTraceback());
        assertNull(exec.text());
    }

    @Test
    void parsesRichResultWithMultipleFormats() {
        Execution exec = CodeInterpreter.parseExecution(mapper, Arrays.asList(
                "{\"type\":\"result\",\"text\":\"<Figure>\",\"png\":\"iVBORw0KGgo=\","
                        + "\"html\":\"<div/>\",\"json\":{\"a\":1},\"is_main_result\":false,"
                        + "\"extra\":{\"application/vnd.custom\":\"x\"}}"
        ));

        assertEquals(1, exec.getResults().size());
        Result r = exec.getResults().get(0);
        assertFalse(r.isMainResult());
        assertEquals("<Figure>", r.getText());
        assertEquals("iVBORw0KGgo=", r.getPng());
        assertEquals("<div/>", r.getHtml());
        Map<String, Object> json = r.getJson();
        assertNotNull(json);
        assertEquals(1, ((Number) json.get("a")).intValue());
        assertEquals("x", r.get("application/vnd.custom"));
        assertTrue(r.formats().contains("png"));
    }

    @Test
    void ignoresBlankAndUnknownLines() {
        Execution exec = CodeInterpreter.parseExecution(mapper, Arrays.asList(
                "",
                "{\"type\":\"something_new\",\"foo\":\"bar\"}",
                "{\"type\":\"stdout\",\"text\":\"hi\",\"timestamp\":1}"
        ));
        assertEquals(Arrays.asList("hi"), exec.getLogs().getStdout());
        assertTrue(exec.getResults().isEmpty());
    }
}
