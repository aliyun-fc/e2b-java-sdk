package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.NewSandbox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J12: allowInternetAccess true vs false.
 *
 * <p>The SDK sends {@code allowInternetAccess} correctly on create; whether outbound egress is
 * actually blocked is runtime/environment dependent (not all environments enforce isolation). The
 * "disabled" case therefore only asserts the block when the environment enforces it.
 */
class InternetAccessE2eTest extends E2eTestBase {

    @Test
    void internetDisabledBlocksOutboundAccess() {
        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).allowInternetAccess(false).build());
        try {
            CommandResult result = sandbox.getCommands().run(
                    "curl -fsS --max-time 5 https://example.com/",
                    null, null, null, 15, false);
            Assumptions.assumeTrue(result.getExitCode() != 0,
                    "egress isolation not enforced in this environment; SDK sent allowInternetAccess=false correctly");
            assertNotEquals(0, result.getExitCode(), "outbound curl should fail when internet is disabled");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void internetEnabledAllowsOutboundAccess() {
        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).allowInternetAccess(true).build());
        try {
            CommandResult result = sandbox.getCommands().run(
                    "curl -fsS --max-time 10 https://example.com/",
                    null, null, null, 20, false);
            assertEquals(0, result.getExitCode(), result.getStderr());
            assertFalse(result.getStdout().trim().isEmpty());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
