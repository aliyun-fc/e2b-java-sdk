package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.SandboxInfo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J10: metadata write/read and isolation across sandboxes.
 */
class MetadataE2eTest extends E2eTestBase {

    @Test
    void metadataRoundTripAndIsolation() {
        String markerA = "java-e2e-a-" + UUID.randomUUID();
        String markerB = "java-e2e-b-" + UUID.randomUUID();

        Map<String, String> metadataA = new HashMap<String, String>();
        metadataA.put("e2e.marker", markerA);

        Map<String, String> metadataB = new HashMap<String, String>();
        metadataB.put("e2e.marker", markerB);

        Sandbox sandboxA = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadataA).build());
        Sandbox sandboxB = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadataB).build());
        try {
            SandboxInfo infoA = sandboxA.getInfo().getSandbox();
            SandboxInfo infoB = sandboxB.getInfo().getSandbox();

            assertNotNull(infoA.getMetadata());
            assertNotNull(infoB.getMetadata());
            assertEquals(markerA, infoA.getMetadata().get("e2e.marker"));
            assertEquals(markerB, infoB.getMetadata().get("e2e.marker"));
            assertNotEquals(infoA.getSandboxId(), infoB.getSandboxId());
        } finally {
            E2eSupport.killQuietly(sandboxA);
            E2eSupport.killQuietly(sandboxB);
        }
    }
}
