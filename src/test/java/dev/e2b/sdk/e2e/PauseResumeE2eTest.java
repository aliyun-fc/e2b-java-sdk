package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J13: pause then connect preserves filesystem state.
 */
class PauseResumeE2eTest extends E2eTestBase {

    @Test
    void pauseAndConnectPreservesFiles() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        String sandboxId = sandbox.getSandboxId();
        try {
            String testContent = "Hello from " + sandboxId + "\n";
            sandbox.getFiles().write("/tmp/pause-test.txt", testContent);

            assertTrue(sandbox.pause());

            Sandbox resumed = Sandbox.connect(sandboxId, config.toConnectionConfig());
            try {
                CommandResult result = resumed.getCommands().run("cat /tmp/pause-test.txt");
                assertEquals(0, result.getExitCode());
                assertEquals(testContent, result.getStdout());
            } finally {
                E2eSupport.killQuietly(resumed);
            }
        } catch (Exception e) {
            E2eSupport.killQuietly(sandbox);
            throw e;
        }
    }
}
