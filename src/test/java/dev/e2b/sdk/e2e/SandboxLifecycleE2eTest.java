package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.SandboxInfo;
import dev.e2b.sdk.model.SandboxMetrics;
import dev.e2b.sdk.model.SandboxQuery;
import dev.e2b.sdk.model.SandboxState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J03/J14/J15/J17: list, getInfo, setTimeout, metrics, connect, close, static helpers.
 */
class SandboxLifecycleE2eTest extends E2eTestBase {

    @Test
    void listGetInfoAndIsRunning() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            assertTrue(sandbox.isRunning());

            SandboxInfo info = sandbox.getInfo();
            assertEquals(sandbox.getSandboxId(), info.getSandboxId());
            assertNotNull(info.getState());
            assertEquals(SandboxState.RUNNING, info.getState());

            Map<String, String> metadataFilter = new HashMap<String, String>();
            metadataFilter.put("e2e.lifecycle", "list-test-" + sandbox.getSandboxId());

            Sandbox listed = Sandbox.create(
                    config.getTemplate(),
                    config.toConnectionConfig(),
                    NewSandbox.builder().timeout(300).metadata(metadataFilter).build());
            try {
                SandboxQuery query = SandboxQuery.builder()
                        .metadata(metadataFilter)
                        .state(Arrays.asList(SandboxState.RUNNING))
                        .build();

                // The gateway indexes sandbox metadata with a short eventual-consistency window
                // (statesync IndexLagTolerance ~5s), so poll a few times before asserting.
                boolean found = false;
                for (int attempt = 0; attempt < 6 && !found; attempt++) {
                    List<SandboxInfo> page = Sandbox.list(config.toConnectionConfig(), query, 50, null);
                    found = page.stream().anyMatch(item -> listed.getSandboxId().equals(item.getSandboxId()));
                    if (!found) {
                        E2eSupport.sleepMillis(2000);
                    }
                }
                assertTrue(found, "listed sandbox should appear in Sandbox.list() results");
            } finally {
                E2eSupport.killQuietly(listed);
            }
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void setTimeoutUpdatesEndAt() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            Instant before = sandbox.getInfo().getEndAt();
            assertNotNull(before);

            sandbox.setTimeout(600);
            Instant after = sandbox.getInfo().getEndAt();
            assertNotNull(after);
            assertTrue(after.isAfter(before), "end_at should move forward after setTimeout(600)");

            Sandbox.setTimeout(sandbox.getSandboxId(), 900, config.toConnectionConfig());
            Instant afterStatic = sandbox.getInfo().getEndAt();
            assertTrue(afterStatic.isAfter(after), "static setTimeout should extend end_at again");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void getMetricsReturnsData() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            sandbox.getCommands().run("echo metrics-smoke");
            List<SandboxMetrics> metrics = sandbox.getMetrics();
            assertNotNull(metrics);

            Instant now = Instant.now();
            List<SandboxMetrics> ranged = sandbox.getMetrics(now.minusSeconds(300), now.plusSeconds(60));
            assertNotNull(ranged);
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void connectToRunningSandbox() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        String sandboxId = sandbox.getSandboxId();
        try {
            sandbox.getCommands().run("echo connect-smoke > /tmp/connect-smoke.txt");

            Sandbox connected = Sandbox.connect(sandboxId, config.toConnectionConfig());
            try {
                assertEquals(sandboxId, connected.getSandboxId());
                assertEquals("connect-smoke", connected.getCommands().run("cat /tmp/connect-smoke.txt").getStdout().trim());
            } finally {
                // connected instance shares the same sandbox; kill once at the end
            }
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void tryWithResourcesKillsSandbox() {
        String sandboxId;
        try (Sandbox sandbox = E2eSupport.createSandbox(config)) {
            sandboxId = sandbox.getSandboxId();
            assertTrue(sandbox.isRunning());
        }

        SandboxExceptionHolder holder = new SandboxExceptionHolder();
        try {
            Sandbox.connect(sandboxId, config.toConnectionConfig());
        } catch (Exception ex) {
            holder.exception = ex;
        }
        assertNotNull(holder.exception, "killed sandbox should not be connectable");
    }

    @Test
    void getMcpUrlUsesExpectedPort() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            String mcpUrl = sandbox.getMcpUrl();
            assertTrue(mcpUrl.startsWith("https://"));
            assertTrue(mcpUrl.contains(String.valueOf(Sandbox.MCP_PORT)));
            assertTrue(mcpUrl.contains(sandbox.getSandboxId()));
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void staticKillById() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        String sandboxId = sandbox.getSandboxId();
        assertTrue(Sandbox.kill(sandboxId, config.toConnectionConfig()));
    }

    private static final class SandboxExceptionHolder {
        private Exception exception;
    }
}
