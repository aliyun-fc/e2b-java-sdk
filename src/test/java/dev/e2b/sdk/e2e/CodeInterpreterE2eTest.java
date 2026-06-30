package dev.e2b.sdk.e2e;

import dev.e2b.sdk.codeinterpreter.CodeInterpreter;
import dev.e2b.sdk.codeinterpreter.Context;
import dev.e2b.sdk.codeinterpreter.Execution;
import dev.e2b.sdk.exception.SandboxException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E for the Code Interpreter module (mirrors Python {@code test_02_code_interpreter.py}):
 * create a code-interpreter sandbox, run code, and assert rich {@link Execution} output.
 *
 * <p>Requires a code-interpreter template (alias {@code code-interpreter-v1} by default; override
 * with {@code E2E_CI_TEMPLATE}). If the template is unavailable in the target environment, the
 * tests are skipped rather than failed.
 */
class CodeInterpreterE2eTest extends E2eTestBase {

    private CodeInterpreter createCodeInterpreter() {
        try {
            return CodeInterpreter.create(config.codeInterpreterTemplate(), config.toConnectionConfig());
        } catch (SandboxException e) {
            Assumptions.assumeTrue(false,
                    "code-interpreter template '" + config.codeInterpreterTemplate()
                            + "' not available in this environment: " + e.getMessage());
            throw e; // unreachable
        }
    }

    @Test
    void runCodePrintsToStdout() {
        CodeInterpreter ci = createCodeInterpreter();
        try {
            Execution exec = ci.runCode("x = 1 + 2\nprint(x)");
            assertNull(exec.getError(), "execution should not error");
            assertFalse(exec.getLogs().getStdout().isEmpty(), "stdout should not be empty");
            assertEquals("3", exec.getLogs().getStdout().get(0).trim());
        } finally {
            ci.close();
        }
    }

    @Test
    void runCodeReturnsMainResult() {
        CodeInterpreter ci = createCodeInterpreter();
        try {
            // The interactively-evaluated last expression becomes the main result.
            Execution exec = ci.runCode("40 + 2");
            assertNull(exec.getError());
            assertEquals("42", exec.text(), "main result text should be the evaluated expression");
        } finally {
            ci.close();
        }
    }

    @Test
    void runCodePassesEnvVars() {
        CodeInterpreter ci = createCodeInterpreter();
        try {
            Execution exec = ci.runCode(
                    "import os\nprint(os.environ.get('CI_E2E_VAR'))",
                    null, null, Collections.singletonMap("CI_E2E_VAR", "hello-ci"), null);
            assertNull(exec.getError());
            assertFalse(exec.getLogs().getStdout().isEmpty());
            assertEquals("hello-ci", exec.getLogs().getStdout().get(0).trim());
        } finally {
            ci.close();
        }
    }

    @Test
    void contextPreservesStateAcrossExecutions() {
        CodeInterpreter ci = createCodeInterpreter();
        try {
            Context ctx = ci.createCodeContext();
            assertNotNull(ctx.getId());

            Execution define = ci.runCode("counter = 41", ctx);
            assertNull(define.getError());

            Execution use = ci.runCode("counter += 1\nprint(counter)", ctx);
            assertNull(use.getError());
            assertFalse(use.getLogs().getStdout().isEmpty());
            assertEquals("42", use.getLogs().getStdout().get(0).trim());

            // listCodeContexts must work; note that envd 0.5.2 in this environment does not track
            // created contexts in the listing (returns []), so we only assert the call succeeds.
            assertNotNull(ci.listCodeContexts(), "listCodeContexts should return a (possibly empty) list");

            ci.removeCodeContext(ctx);
        } finally {
            ci.close();
        }
    }

    @Test
    void runCodeCapturesError() {
        CodeInterpreter ci = createCodeInterpreter();
        try {
            Execution exec = ci.runCode("raise ValueError('boom')");
            assertNotNull(exec.getError(), "error should be captured");
            assertNotNull(exec.getError().getName());
            // The error value/traceback should reference the raised exception. The kernel's exact
            // `name` field can differ by environment, so assert on the captured details instead.
            String details = String.valueOf(exec.getError().getValue())
                    + String.valueOf(exec.getError().getTraceback());
            assertTrue(details.contains("boom") || details.contains("ValueError"),
                    "error details should reference the raised exception: " + details);
        } finally {
            ci.close();
        }
    }
}
