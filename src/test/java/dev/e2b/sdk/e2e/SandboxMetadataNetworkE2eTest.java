package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.SandboxMetadata;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.SandboxInfo;
import dev.e2b.sdk.model.SandboxNetworkOpts;
import dev.e2b.sdk.model.SandboxNetworkRule;
import dev.e2b.sdk.model.SandboxNetworkTransform;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxMetadataNetworkE2eTest extends E2eTestBase {

    @Test
    void createsSandboxWithMetadataAllowOutAndRules() {
        String marker = "java-metadata-e2e-" + UUID.randomUUID();
        Map<String, String> metadata = SandboxMetadata.builder()
                .put("e2e.marker", marker)
                .build();
        SandboxNetworkRule rule = SandboxNetworkRule.builder()
                .transform(SandboxNetworkTransform.builder()
                        .headers(Collections.singletonMap("X-E2E-Rule", "enabled"))
                        .build())
                .build();
        SandboxNetworkOpts network = SandboxNetworkOpts.builder()
                .allowOut(Collections.singletonList("example.com"))
                .rules(Collections.singletonMap("example.com", Collections.singletonList(rule)))
                .build();

        Sandbox sandbox = Sandbox.create(config.getTemplate(), config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadata).network(network).build());
        try {
            assertNotNull(sandbox.getSandboxId());
            assertEquals("sandbox-ok", sandbox.getCommands().run("printf sandbox-ok").getStdout());
            SandboxInfo info = sandbox.getInfo().getSandbox();
            assertEquals(marker, info.getMetadata().get("e2e.marker"));
            assertNotNull(info.getNetwork());
            assertTrue(info.getNetwork().getAllowOut().contains("example.com"));
            assertTrue(info.getNetwork().getRules().containsKey("example.com"));
            assertEquals(0, sandbox.getCommands()
                    .run("curl -fsSI --max-time 10 https://example.com/").getExitCode());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
