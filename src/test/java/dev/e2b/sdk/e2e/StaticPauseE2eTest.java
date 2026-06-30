package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J13 supplement: static {@link Sandbox#pause(String, ConnectionConfig)} helper.
 */
class StaticPauseE2eTest extends E2eTestBase {

    @Test
    void staticPauseThenConnect() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        String sandboxId = sandbox.getSandboxId();
        try {
            String content = "static-pause-" + sandboxId + "\n";
            sandbox.getFiles().write("/tmp/static-pause.txt", content);

            Sandbox.pause(sandboxId, config.toConnectionConfig());

            Sandbox resumed = Sandbox.connect(sandboxId, config.toConnectionConfig());
            try {
                CommandResult result = resumed.getCommands().run("cat /tmp/static-pause.txt");
                assertEquals(0, result.getExitCode());
                assertEquals(content, result.getStdout());
            } finally {
                E2eSupport.killQuietly(resumed);
            }
        } catch (Exception e) {
            E2eSupport.killQuietly(sandbox);
            throw e;
        }
    }
}
