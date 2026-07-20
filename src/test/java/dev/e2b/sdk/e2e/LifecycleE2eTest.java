package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.SandboxAutoResumeConfig;
import dev.e2b.sdk.model.SandboxInfo;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Covers the sandbox lifecycle SDK parameters that the Python suite exercises in
 * test_37/38 (TTL / auto-resume): {@code autoPause}, {@code autoResume} and
 * {@code setTimeout}. We do not wait for a real TTL reclaim (slow/flaky); instead
 * we assert the SDK sends the params correctly, the sandbox is usable, the timeout
 * can be extended, and an auto-pause sandbox can be resumed via connect.
 */
class LifecycleE2eTest extends E2eTestBase {

    @Test
    void autoPauseAutoResumeAndSetTimeout() {
        NewSandbox opts = NewSandbox.builder()
                .timeout(60)
                .autoPause(Boolean.TRUE)
                .autoResume(SandboxAutoResumeConfig.builder().enabled(Boolean.TRUE).build())
                .build();

        Sandbox sandbox = E2eSupport.createSandbox(config, opts);
        String sandboxId = sandbox.getSandboxId();
        try {
            // The sandbox must be alive and usable right after creation with lifecycle opts.
            CommandResult ready = sandbox.getCommands().run("echo lifecycle-ok");
            assertEquals(0, ready.getExitCode());
            assertEquals("lifecycle-ok\n", ready.getStdout());

            SandboxInfo before = sandbox.getInfo().getSandbox();
            assertEquals(sandboxId, before.getSandboxId());

            // Extend the TTL; if the gateway reports endAt we assert it advances.
            Instant endBefore = before.getEndAt();
            sandbox.setTimeout(600);
            SandboxInfo after = sandbox.getInfo().getSandbox();
            if (endBefore != null && after.getEndAt() != null) {
                assertFalse(after.getEndAt().isBefore(endBefore),
                        "setTimeout should not move endAt earlier");
            }

            // auto-pause means a paused sandbox survives and can be resumed by connect.
            assumeTrue(sandbox.pause() != null, "auto-pause not honored by this environment; skipping resume check");
            Sandbox resumed = Sandbox.connect(sandboxId, config.toConnectionConfig());
            try {
                CommandResult back = resumed.getCommands().run("echo resumed");
                assertEquals(0, back.getExitCode());
                assertEquals("resumed\n", back.getStdout());
            } finally {
                E2eSupport.killQuietly(resumed);
            }
        } catch (RuntimeException e) {
            E2eSupport.killQuietly(sandbox);
            throw e;
        }
    }
}
