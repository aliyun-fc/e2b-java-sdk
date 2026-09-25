package dev.e2b.sdk;

import dev.e2b.sdk.model.LoggingConfig;
import dev.e2b.sdk.model.ManagedIdentityFailStrategy;
import dev.e2b.sdk.model.TracingConfig;
import dev.e2b.sdk.storage.AgenticFsConfig;
import dev.e2b.sdk.storage.AgenticFsMountPoint;
import dev.e2b.sdk.storage.NasConfig;
import dev.e2b.sdk.storage.NasMountPoint;
import dev.e2b.sdk.storage.PolarFsConfig;
import dev.e2b.sdk.storage.PolarFsMountPoint;
import dev.e2b.sdk.storage.PortForwardVpcConfig;
import dev.e2b.sdk.storage.VpcConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SandboxMetadataGatewayTypesTest {

    @Test
    void serializesAddedVpcAndNasFieldsWithExactJsonNames() {
        VpcConfig vpc = VpcConfig.builder()
                .role("acs:ram::1234567890123456:role/sandbox")
                .build();
        NasConfig nas = NasConfig.builder()
                .groupId(100)
                .userId(200)
                .mountPoints(Collections.singletonList(NasMountPoint.builder()
                        .serverAddr("nas.example:/share")
                        .mountDir("/mnt/nas")
                        .enableTLS(true)
                        .build()))
                .build();

        assertEquals("{\"role\":\"acs:ram::1234567890123456:role/sandbox\"}",
                SandboxMetadata.toJson(vpc));
        assertEquals("{\"groupId\":100,\"userId\":200,\"mountPoints\":[{\"serverAddr\":\"nas.example:/share\",\"mountDir\":\"/mnt/nas\",\"enableTLS\":true}]}",
                SandboxMetadata.toJson(nas));
    }

    @Test
    void omitsAddedVpcAndNasFieldsWhenUnset() {
        String vpcJson = SandboxMetadata.toJson(VpcConfig.builder().vpcId("vpc-1").build());
        String nasJson = SandboxMetadata.toJson(NasConfig.builder()
                .mountPoints(Collections.singletonList(NasMountPoint.builder()
                        .serverAddr("nas.example:/share")
                        .mountDir("/mnt/nas")
                        .build()))
                .build());

        assertEquals("{\"vpcId\":\"vpc-1\"}", vpcJson);
        assertFalse(vpcJson.contains("role"));
        assertEquals("{\"mountPoints\":[{\"serverAddr\":\"nas.example:/share\",\"mountDir\":\"/mnt/nas\"}]}", nasJson);
        assertFalse(nasJson.contains("groupId"));
        assertFalse(nasJson.contains("userId"));
        assertFalse(nasJson.contains("enableTLS"));
    }

    @Test
    void serializesNewGatewayTypesWithExactJsonNames() {
        PortForwardVpcConfig portForwardVpc = PortForwardVpcConfig.builder()
                .vpcId("vpc-1")
                .securityGroupId("sg-1")
                .vSwitchIds(Collections.singletonList("vsw-1"))
                .build();
        PolarFsConfig polarFs = PolarFsConfig.builder()
                .groupId(300)
                .userId(400)
                .mountPoints(Collections.singletonList(PolarFsMountPoint.builder()
                        .instanceId("instance-1")
                        .mountDir("/mnt/polarfs")
                        .remoteDir("/remote")
                        .readOnly(true)
                        .build()))
                .build();
        AgenticFsConfig agenticFs = AgenticFsConfig.builder()
                .groupId(500)
                .userId(600)
                .mountPoints(Collections.singletonList(AgenticFsMountPoint.builder()
                        .serverAddr("agenticfs.example:1234")
                        .mountDir("/mnt/agenticfs")
                        .fileSystemId("fs-1")
                        .agenticSpaceId("space-1")
                        .accessPointId("ap-1")
                        .build()))
                .build();

        assertEquals("{\"vpcId\":\"vpc-1\",\"securityGroupId\":\"sg-1\",\"vSwitchIds\":[\"vsw-1\"]}",
                SandboxMetadata.toJson(portForwardVpc));
        assertEquals("{\"groupId\":300,\"userId\":400,\"mountPoints\":[{\"instanceId\":\"instance-1\",\"mountDir\":\"/mnt/polarfs\",\"remoteDir\":\"/remote\",\"readOnly\":true}]}",
                SandboxMetadata.toJson(polarFs));
        assertEquals("{\"groupID\":500,\"userID\":600,\"mountPoints\":[{\"serverAddr\":\"agenticfs.example:1234\",\"mountDir\":\"/mnt/agenticfs\",\"fileSystemID\":\"fs-1\",\"agenticSpaceID\":\"space-1\",\"accessPointID\":\"ap-1\"}]}",
                SandboxMetadata.toJson(agenticFs));

        assertEquals("{}", SandboxMetadata.toJson(PortForwardVpcConfig.builder().build()));
        assertEquals("{}", SandboxMetadata.toJson(PolarFsConfig.builder().build()));
        assertEquals("{}", SandboxMetadata.toJson(PolarFsMountPoint.builder().build()));
        assertEquals("{}", SandboxMetadata.toJson(AgenticFsConfig.builder().build()));
        assertEquals("{}", SandboxMetadata.toJson(AgenticFsMountPoint.builder().build()));
    }

    @Test
    void exposesExactMetadataKeysAndIgnoresNullTypedInputs() {
        assertEquals("fc.sandbox.network.portforwardvpc",
                SandboxMetadata.PORT_FORWARD_VPC_METADATA_KEY);
        assertEquals("fc.sandbox.storage.polarfs", SandboxMetadata.POLARFS_METADATA_KEY);
        assertEquals("fc.sandbox.storage.agenticfs", SandboxMetadata.AGENTICFS_METADATA_KEY);

        Map<String, String> metadata = SandboxMetadata.builder()
                .portForwardVpc(null)
                .polarFs(null)
                .agenticFs(null)
                .build();

        assertEquals(Collections.emptyMap(), metadata);
    }

    @Test
    void typedAndRawGatewayMetadataUseLastWriteWins() {
        PortForwardVpcConfig portForwardVpc = PortForwardVpcConfig.builder().vpcId("vpc-1").build();
        PolarFsConfig polarFs = PolarFsConfig.builder().groupId(100).build();
        AgenticFsConfig agenticFs = AgenticFsConfig.builder().groupId(200).build();

        Map<String, String> rawOverrides = SandboxMetadata.builder()
                .portForwardVpc(portForwardVpc)
                .polarFs(polarFs)
                .agenticFs(agenticFs)
                .put(SandboxMetadata.PORT_FORWARD_VPC_METADATA_KEY, "raw-port-forward-vpc")
                .put(SandboxMetadata.POLARFS_METADATA_KEY, "raw-polarfs")
                .put(SandboxMetadata.AGENTICFS_METADATA_KEY, "raw-agenticfs")
                .build();
        assertEquals("raw-port-forward-vpc",
                rawOverrides.get(SandboxMetadata.PORT_FORWARD_VPC_METADATA_KEY));
        assertEquals("raw-polarfs", rawOverrides.get(SandboxMetadata.POLARFS_METADATA_KEY));
        assertEquals("raw-agenticfs", rawOverrides.get(SandboxMetadata.AGENTICFS_METADATA_KEY));

        Map<String, String> typedOverrides = SandboxMetadata.builder()
                .put(SandboxMetadata.PORT_FORWARD_VPC_METADATA_KEY, "raw-port-forward-vpc")
                .put(SandboxMetadata.POLARFS_METADATA_KEY, "raw-polarfs")
                .put(SandboxMetadata.AGENTICFS_METADATA_KEY, "raw-agenticfs")
                .portForwardVpc(portForwardVpc)
                .polarFs(polarFs)
                .agenticFs(agenticFs)
                .build();
        assertEquals(SandboxMetadata.toJson(portForwardVpc),
                typedOverrides.get(SandboxMetadata.PORT_FORWARD_VPC_METADATA_KEY));
        assertEquals(SandboxMetadata.toJson(polarFs),
                typedOverrides.get(SandboxMetadata.POLARFS_METADATA_KEY));
        assertEquals(SandboxMetadata.toJson(agenticFs),
                typedOverrides.get(SandboxMetadata.AGENTICFS_METADATA_KEY));
    }

    @Test
    void serializesObservabilityConfigsWithExactJsonNamesAndValues() {
        LoggingConfig logging = LoggingConfig.builder()
                .project("logging-project")
                .logstore("sandbox-logstore")
                .logBeginRule("DefaultRegex")
                .enableRequestMetrics(true)
                .enableInstanceMetrics(false)
                .enableLlmMetrics(true)
                .build();
        TracingConfig tracing = TracingConfig.builder()
                .type("OpenTelemetry")
                .params(Collections.singletonMap("endpoint", "https://tracing.example.com"))
                .build();

        assertEquals("{\"project\":\"logging-project\",\"logstore\":\"sandbox-logstore\",\"logBeginRule\":\"DefaultRegex\",\"enableRequestMetrics\":true,\"enableInstanceMetrics\":false,\"enableLlmMetrics\":true}",
                SandboxMetadata.toJson(logging));
        assertEquals("{\"type\":\"OpenTelemetry\",\"params\":{\"endpoint\":\"https://tracing.example.com\"}}",
                SandboxMetadata.toJson(tracing));
    }

    @Test
    void omitsNullableObservabilityFieldsWhenUnset() {
        assertEquals("{}", SandboxMetadata.toJson(LoggingConfig.builder().build()));
        assertEquals("{}", SandboxMetadata.toJson(TracingConfig.builder().build()));
    }

    @Test
    void exposesExactObservabilityMetadataKeysAndIgnoresNullTypedInputs() {
        assertEquals("fc.sandbox.observability.logging", SandboxMetadata.LOGGING_METADATA_KEY);
        assertEquals("fc.sandbox.observability.tracing", SandboxMetadata.TRACING_METADATA_KEY);

        Map<String, String> metadata = SandboxMetadata.builder()
                .logging(null)
                .tracing(null)
                .build();

        assertEquals(Collections.emptyMap(), metadata);
    }

    @Test
    void observabilityTypedAndRawMetadataUseLastWriteWins() {
        LoggingConfig logging = LoggingConfig.builder().project("typed-project").build();
        TracingConfig tracing = TracingConfig.builder().type("typed-tracing").build();

        Map<String, String> rawOverrides = SandboxMetadata.builder()
                .logging(logging)
                .tracing(tracing)
                .put(SandboxMetadata.LOGGING_METADATA_KEY, "raw-logging")
                .put(SandboxMetadata.TRACING_METADATA_KEY, "raw-tracing")
                .build();
        assertEquals("raw-logging", rawOverrides.get(SandboxMetadata.LOGGING_METADATA_KEY));
        assertEquals("raw-tracing", rawOverrides.get(SandboxMetadata.TRACING_METADATA_KEY));

        Map<String, String> typedOverrides = SandboxMetadata.builder()
                .put(SandboxMetadata.LOGGING_METADATA_KEY, "raw-logging")
                .put(SandboxMetadata.TRACING_METADATA_KEY, "raw-tracing")
                .logging(logging)
                .tracing(tracing)
                .build();
        assertEquals(SandboxMetadata.toJson(logging),
                typedOverrides.get(SandboxMetadata.LOGGING_METADATA_KEY));
        assertEquals(SandboxMetadata.toJson(tracing),
                typedOverrides.get(SandboxMetadata.TRACING_METADATA_KEY));
    }

    @Test
    void exposesExactManagedIdentityMetadataKeys() {
        assertEquals("fc.sandbox.id", SandboxMetadata.SANDBOX_ID_METADATA_KEY);
        assertEquals("fc.sandbox.identity.name", SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY);
        assertEquals("fc.sandbox.identity.role.policy", SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY);
        assertEquals("fc.sandbox.identity.role.domains", SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY);
        assertEquals("fc.sandbox.identity.role.failStrategy",
                SandboxMetadata.MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY);
    }

    @Test
    void serializesManagedIdentityMetadataEntriesIndependently() {
        Map<String, String> policy = Collections.singletonMap("Version", "1");

        Map<String, String> metadata = SandboxMetadata.builder()
                .sandboxId("sbx-123")
                .managedIdentityName("identity-name")
                .managedIdentityPolicy(policy)
                .managedIdentityDomains(Arrays.asList("example.com", "api.example.com"))
                .managedIdentityFailStrategy(ManagedIdentityFailStrategy.BLOCK)
                .build();

        assertEquals("sbx-123", metadata.get(SandboxMetadata.SANDBOX_ID_METADATA_KEY));
        assertEquals("identity-name", metadata.get(SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY));
        assertEquals("{\"Version\":\"1\"}",
                metadata.get(SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY));
        assertEquals("[\"example.com\",\"api.example.com\"]",
                metadata.get(SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY));
        assertEquals("Block", metadata.get(SandboxMetadata.MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY));
    }

    @Test
    void preservesEmptyManagedIdentityMetadataValues() {
        Map<String, String> metadata = SandboxMetadata.builder()
                .sandboxId("")
                .managedIdentityName("")
                .managedIdentityPolicy(Collections.<String, String>emptyMap())
                .managedIdentityDomains(Collections.<String>emptyList())
                .build();

        assertEquals("", metadata.get(SandboxMetadata.SANDBOX_ID_METADATA_KEY));
        assertEquals("", metadata.get(SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY));
        assertEquals("{}", metadata.get(SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY));
        assertEquals("[]", metadata.get(SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY));
    }

    @Test
    void ignoresNullManagedIdentityMetadataInputs() {
        Map<String, String> metadata = SandboxMetadata.builder()
                .sandboxId(null)
                .managedIdentityName(null)
                .managedIdentityPolicy(null)
                .managedIdentityDomains(null)
                .managedIdentityFailStrategy(null)
                .build();

        assertEquals(Collections.emptyMap(), metadata);
    }

    @Test
    void managedIdentityTypedAndRawMetadataUseLastWriteWins() {
        Map<String, String> policy = Collections.singletonMap("Statement", "typed");

        Map<String, String> rawOverrides = SandboxMetadata.builder()
                .sandboxId("typed-id")
                .managedIdentityName("typed-name")
                .managedIdentityPolicy(policy)
                .managedIdentityDomains(Collections.singletonList("typed.example.com"))
                .managedIdentityFailStrategy(ManagedIdentityFailStrategy.ALLOW)
                .put(SandboxMetadata.SANDBOX_ID_METADATA_KEY, "raw-id")
                .put(SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY, "raw-name")
                .put(SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY, "raw-policy")
                .put(SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY, "raw-domains")
                .put(SandboxMetadata.MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY, "raw-strategy")
                .build();
        assertEquals("raw-id", rawOverrides.get(SandboxMetadata.SANDBOX_ID_METADATA_KEY));
        assertEquals("raw-name", rawOverrides.get(SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY));
        assertEquals("raw-policy", rawOverrides.get(SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY));
        assertEquals("raw-domains", rawOverrides.get(SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY));
        assertEquals("raw-strategy",
                rawOverrides.get(SandboxMetadata.MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY));

        Map<String, String> typedOverrides = SandboxMetadata.builder()
                .put(SandboxMetadata.SANDBOX_ID_METADATA_KEY, "raw-id")
                .put(SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY, "raw-name")
                .put(SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY, "raw-policy")
                .put(SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY, "raw-domains")
                .put(SandboxMetadata.MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY, "raw-strategy")
                .sandboxId("typed-id")
                .managedIdentityName("typed-name")
                .managedIdentityPolicy(policy)
                .managedIdentityDomains(Collections.singletonList("typed.example.com"))
                .managedIdentityFailStrategy(ManagedIdentityFailStrategy.ALLOW)
                .build();
        assertEquals("typed-id", typedOverrides.get(SandboxMetadata.SANDBOX_ID_METADATA_KEY));
        assertEquals("typed-name", typedOverrides.get(SandboxMetadata.MANAGED_IDENTITY_NAME_METADATA_KEY));
        assertEquals("{\"Statement\":\"typed\"}",
                typedOverrides.get(SandboxMetadata.MANAGED_IDENTITY_POLICY_METADATA_KEY));
        assertEquals("[\"typed.example.com\"]",
                typedOverrides.get(SandboxMetadata.MANAGED_IDENTITY_DOMAINS_METADATA_KEY));
        assertEquals("Allow",
                typedOverrides.get(SandboxMetadata.MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY));
    }

    @Test
    void managedIdentityFailStrategyUsesExactWireValues() {
        assertEquals("Block", ManagedIdentityFailStrategy.BLOCK.getValue());
        assertEquals("Allow", ManagedIdentityFailStrategy.ALLOW.getValue());
        assertEquals("\"Block\"", SandboxMetadata.toJson(ManagedIdentityFailStrategy.BLOCK));
        assertEquals("\"Allow\"", SandboxMetadata.toJson(ManagedIdentityFailStrategy.ALLOW));
    }
}
