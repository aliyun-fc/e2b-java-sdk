package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J01: default create + synchronous command execution.
 */
class BasicSandboxE2eTest extends E2eTestBase {

    @Test
    void createAndRunCommands() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            assertNotNull(sandbox.getSandboxId());
            assertNotNull(sandbox.getSandboxDomain());
            assertNotNull(sandbox.getCommands());
            assertNotNull(sandbox.getFiles());
            assertNotNull(sandbox.getGit());
            assertTrue(sandbox.isRunning());

            CommandResult echo = sandbox.getCommands().run("echo 'Hello, World!'");
            assertEquals(0, echo.getExitCode());
            assertTrue(echo.isSuccess());
            assertEquals("Hello, World!", echo.getStdout().trim());

            CommandResult date = sandbox.getCommands().run("date");
            assertEquals(0, date.getExitCode());
            assertFalse(date.getStdout().trim().isEmpty());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void createWithExplicitTemplateAndTimeout() {
        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                E2eSupport.defaultOpts(300));
        try {
            assertNotNull(sandbox.getSandboxId());
            CommandResult result = sandbox.getCommands().run("echo ok");
            assertEquals(0, result.getExitCode());
            assertEquals("ok", result.getStdout().trim());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
