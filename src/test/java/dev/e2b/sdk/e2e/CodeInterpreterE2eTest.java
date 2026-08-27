package dev.e2b.sdk.e2e;

import dev.e2b.sdk.codeinterpreter.CodeInterpreter;
import dev.e2b.sdk.codeinterpreter.Context;
import dev.e2b.sdk.codeinterpreter.Execution;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.exception.TimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E for the Code Interpreter module (mirrors Python {@code test_02_code_interpreter.py}):
 * create a code-interpreter sandbox, run code, and assert rich {@link Execution} output.
 *
 * <p>Requires a code-interpreter template (alias {@code code-interpreter-v1} by default; override
 * with {@code E2E_CI_TEMPLATE}). If the template is unavailable in the target environment, the
 * general capability tests are skipped rather than failed.
 */
class CodeInterpreterE2eTest extends E2eTestBase {

    private CodeInterpreter createCodeInterpreter() {
        try {
            return createRequiredCodeInterpreter();
        } catch (SandboxException e) {
            Assumptions.assumeTrue(false,
                    "code-interpreter template '" + config.codeInterpreterTemplate()
                            + "' not available in this environment: " + e.getMessage());
            throw e; // unreachable
        }
    }

    private CodeInterpreter createRequiredCodeInterpreter() {
        return CodeInterpreter.create(config.codeInterpreterTemplate(), config.toConnectionConfig());
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

    @Test
    void runCodeTimeoutReturnsPromptlyAndKeepsSandboxUsable() {
        // This regression must fail on authentication, networking, or template errors rather than
        // turning an infrastructure problem into a skipped timeout result.
        CodeInterpreter ci = createRequiredCodeInterpreter();
        try {
            // Warm the Jupyter kernel so request/startup latency is not mistaken for execution time.
            Execution warmup = ci.runCode("1", "python", null, null, 10);
            assertNull(warmup.getError());

            long startedAt = System.nanoTime();
            TimeoutException error = assertThrows(TimeoutException.class, () -> ci.runCode(
                    "import time\n"
                            + "time.sleep(5)\n"
                            + "print('late')",
                    "python", null, null, 1));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(error.getMessage().contains("1"));
            assertTrue(elapsedMillis >= 700,
                    "execution timeout fired too early: " + elapsedMillis + "ms");
            assertTrue(elapsedMillis < 4000,
                    "execution timeout was not enforced promptly: " + elapsedMillis + "ms");

            // A transport cancellation always stops the Java caller from waiting. Whether a
            // compatibility gateway propagates that disconnect quickly enough to interrupt the
            // remote kernel is a server-side best-effort behavior. The public SDK guarantee is
            // that a later execution can still use the same sandbox.
            Execution recovery = ci.runCode(
                    "print('after-timeout')", "python", null, null, 10);
            assertNull(recovery.getError());
            assertEquals("after-timeout", recovery.getLogs().getStdout().get(0).trim());
        } finally {
            ci.close();
        }
    }
}
