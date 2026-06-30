package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.exception.CommandExitException;
import dev.e2b.sdk.model.CommandResult;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J04/J05: command execution parameters and error handling.
 */
class CommandsE2eTest extends E2eTestBase {

    @Test
    void runWithCwdUserEnvsAndTimeout() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            sandbox.getCommands().run("mkdir -p /tmp/cmd-work");
            sandbox.getFiles().write("/tmp/cmd-work/payload.txt", "cwd-ok");

            Map<String, String> envs = new HashMap<String, String>();
            envs.put("E2B_CMD_FLAG", "from-run-env");

            CommandResult result = sandbox.getCommands().run(
                    "printf '%s|%s' \"$E2B_CMD_FLAG\" \"$(cat payload.txt)\"",
                    envs,
                    "user",
                    "/tmp/cmd-work",
                    30,
                    false);

            assertEquals(0, result.getExitCode());
            assertEquals("from-run-env|cwd-ok", result.getStdout());
            assertEquals("", result.getStderr());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void runOrThrowOnSuccess() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            CommandResult result = sandbox.getCommands().runOrThrow("echo success");
            assertEquals(0, result.getExitCode());
            assertEquals("success", result.getStdout().trim());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void runOrThrowFailsOnNonZeroExit() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            CommandExitException ex = assertThrows(
                    CommandExitException.class,
                    () -> sandbox.getCommands().runOrThrow("exit 7"));
            assertEquals(7, ex.getResult().getExitCode());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void runDoesNotThrowOnNonZeroExitByDefault() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            CommandResult result = sandbox.getCommands().run("exit 3");
            assertEquals(3, result.getExitCode());
            assertFalse(result.isSuccess());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
