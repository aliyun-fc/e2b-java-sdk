package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.SandboxNetworkUpdate;
import dev.e2b.sdk.model.SnapshotInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J18/J19: snapshot and runtime network-update control-plane APIs.
 *
 * <p>NOTE on gateway behaviour: in the current sandbox-gateway, {@code POST
 * /sandboxes/{id}/snapshots} returns mock data (snapshots are not yet FC-backed and there is no
 * delete-snapshot route), and {@code PUT /sandboxes/{id}/network} is a 204 stub. These tests
 * therefore validate the SDK request/response contract; any runtime egress effect is asserted only
 * when the environment actually enforces it (otherwise skipped via assumptions).
 */
class SnapshotAndNetworkE2eTest extends E2eTestBase {

    @Test
    void createSnapshotReturnsId() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            String snapshotName = "java-e2e-" + UUID.randomUUID();
            SnapshotInfo snapshot = sandbox.createSnapshot(snapshotName).getSnapshot();
            assertNotNull(snapshot);
            assertNotNull(snapshot.getSnapshotId());
            assertFalse(snapshot.getSnapshotId().isEmpty());
            // No delete: the gateway exposes no delete-snapshot route (snapshots are mock/not FC-backed).
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void updateNetworkApiSucceeds() {
        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).allowInternetAccess(true).build());
        try {
            // SDK contract: the network-update calls must serialize correctly and be accepted (204).
            assertDoesNotThrow(() -> sandbox.updateNetwork(
                    SandboxNetworkUpdate.builder().allowInternetAccess(false).build()));

            // Runtime egress enforcement is a control-plane stub today; only assert the effect when
            // the environment actually blocks outbound traffic.
            boolean blocked = !sandbox.getCommands().run(
                    "curl -fsS --max-time 5 https://example.com/",
                    null, null, null, 15, false).isSuccess();
            Assumptions.assumeTrue(blocked,
                    "network update is a control-plane stub in this environment; egress not enforced");

            assertDoesNotThrow(() -> sandbox.updateNetwork(
                    SandboxNetworkUpdate.builder().allowInternetAccess(true).build()));
            assertTrue(sandbox.getCommands().run(
                    "curl -fsS --max-time 10 https://example.com/",
                    null, null, null, 20, false).isSuccess());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
