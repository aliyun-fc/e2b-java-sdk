package dev.e2b.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.storage.AgenticBucketConfig;
import dev.e2b.sdk.storage.JuiceFsConfig;
import dev.e2b.sdk.storage.NasConfig;
import dev.e2b.sdk.storage.OssConfig;
import dev.e2b.sdk.storage.StorageMounts;
import dev.e2b.sdk.storage.VpcConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxMetadataTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesExistingMetadataKeys() {
        assertEquals("fc.sandbox.network.vpc", StorageMounts.VPC_METADATA_KEY);
        assertEquals("fc.sandbox.storage.juicefs", StorageMounts.JUICEFS_METADATA_KEY);
        assertEquals("fc.sandbox.storage.oss", StorageMounts.OSS_METADATA_KEY);
        assertEquals("fc.sandbox.storage.agenticbucket", StorageMounts.AGENTIC_BUCKET_METADATA_KEY);
        assertEquals("fc.sandbox.storage.nas", StorageMounts.NAS_METADATA_KEY);
        assertEquals("fc.sandbox.auth.role", StorageMounts.ROLE_ARN_METADATA_KEY);

        assertEquals("fc.sandbox.network.vpc", SandboxMetadata.VPC_METADATA_KEY);
        assertEquals("fc.sandbox.storage.juicefs", SandboxMetadata.JUICEFS_METADATA_KEY);
        assertEquals("fc.sandbox.storage.oss", SandboxMetadata.OSS_METADATA_KEY);
        assertEquals("fc.sandbox.storage.agenticbucket", SandboxMetadata.AGENTIC_BUCKET_METADATA_KEY);
        assertEquals("fc.sandbox.storage.nas", SandboxMetadata.NAS_METADATA_KEY);
        assertEquals("fc.sandbox.auth.role", SandboxMetadata.ROLE_ARN_METADATA_KEY);
    }

    @Test
    void newAndLegacyBuildersProduceIdenticalMetadataForEveryExistingMethod() {
        VpcConfig vpc = VpcConfig.builder().vpcId("vpc-1").build();
        JuiceFsConfig juicefs = JuiceFsConfig.builder().mountPoints(Collections.emptyList()).build();
        OssConfig oss = OssConfig.builder().mountPoints(Collections.emptyList()).build();
        AgenticBucketConfig agenticBucket = AgenticBucketConfig.builder()
                .mountPoints(Collections.emptyList())
                .build();
        NasConfig nas = NasConfig.builder().mountPoints(Collections.emptyList()).build();
        Map<String, String> extra = Collections.singletonMap("custom", "value");

        assertEquals(StorageMounts.builder().vpc(vpc).build(),
                SandboxMetadata.builder().vpc(vpc).build());
        assertEquals(StorageMounts.builder().juicefs(juicefs).build(),
                SandboxMetadata.builder().juicefs(juicefs).build());
        assertEquals(StorageMounts.builder().oss(oss).build(),
                SandboxMetadata.builder().oss(oss).build());
        assertEquals(StorageMounts.builder().agenticBucket(agenticBucket).build(),
                SandboxMetadata.builder().agenticBucket(agenticBucket).build());
        assertEquals(StorageMounts.builder().nas(nas).build(),
                SandboxMetadata.builder().nas(nas).build());
        assertEquals(StorageMounts.builder().roleArn("acs:ram::1:role/test").build(),
                SandboxMetadata.builder().roleArn("acs:ram::1:role/test").build());
        assertEquals(StorageMounts.builder().put("custom", "value").build(),
                SandboxMetadata.builder().put("custom", "value").build());
        assertEquals(StorageMounts.builder().putAll(extra).build(),
                SandboxMetadata.builder().putAll(extra).build());

        Map<String, String> legacyMetadata = StorageMounts.builder()
                .vpc(vpc)
                .roleArn("acs:ram::1:role/test")
                .put("custom", "value")
                .build();
        assertEquals("{\"vpcId\":\"vpc-1\"}",
                legacyMetadata.get("fc.sandbox.network.vpc"));
        assertEquals("acs:ram::1:role/test",
                legacyMetadata.get("fc.sandbox.auth.role"));
        assertEquals("value", legacyMetadata.get("custom"));
    }

    @Test
    void preservesLegacyPublicMethodSignatures() throws Exception {
        Method builder = StorageMounts.class.getMethod("builder");
        assertEquals(StorageMounts.Builder.class, builder.getReturnType());
        assertTrue(Modifier.isStatic(builder.getModifiers()));

        Method toJson = StorageMounts.class.getMethod("toJson", Object.class);
        assertEquals(String.class, toJson.getReturnType());
        assertTrue(Modifier.isStatic(toJson.getModifiers()));

        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("vpc", VpcConfig.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("juicefs", JuiceFsConfig.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("oss", OssConfig.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("agenticBucket", AgenticBucketConfig.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("nas", NasConfig.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("roleArn", String.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("put", String.class, String.class).getReturnType());
        assertEquals(StorageMounts.Builder.class,
                StorageMounts.Builder.class.getMethod("putAll", Map.class).getReturnType());
        assertEquals(Map.class,
                StorageMounts.Builder.class.getMethod("build").getReturnType());
    }

    @Test
    void serializesJsonMetadataWithNullFieldsOmitted() throws Exception {
        VpcConfig vpc = VpcConfig.builder()
                .vpcId("vpc-1")
                .securityGroupId(null)
                .build();

        String json = SandboxMetadata.toJson(vpc);
        JsonNode node = mapper.readTree(json);
        assertEquals("vpc-1", node.get("vpcId").asText());
        assertFalse(node.has("securityGroupId"));

        Map<String, String> metadata = SandboxMetadata.builder().putJson("custom.json", vpc).build();
        assertEquals(json, metadata.get("custom.json"));
    }

    @Test
    void sandboxMetadataToJsonUsesGenericFailureMessage() {
        SandboxException exception = assertThrows(SandboxException.class,
                () -> SandboxMetadata.toJson(unserializableVpc()));

        assertEquals("Failed to serialize sandbox metadata", exception.getMessage());
    }

    @Test
    void sandboxMetadataPutJsonUsesGenericFailureMessage() {
        SandboxException exception = assertThrows(SandboxException.class,
                () -> SandboxMetadata.builder().putJson("custom.json", unserializableVpc()));

        assertEquals("Failed to serialize sandbox metadata", exception.getMessage());
    }

    @Test
    void storageMountsToJsonPreservesLegacyFailureMessage() {
        SandboxException exception = assertThrows(SandboxException.class,
                () -> StorageMounts.toJson(unserializableVpc()));

        assertEquals("Failed to serialize storage mount config", exception.getMessage());
        assertFalse(exception.getCause() instanceof SandboxException);
    }

    @Test
    void storageMountsTypedBuilderPreservesLegacyFailureMessage() {
        SandboxException exception = assertThrows(SandboxException.class,
                () -> StorageMounts.builder().vpc(unserializableVpc()));

        assertEquals("Failed to serialize storage mount config", exception.getMessage());
        assertFalse(exception.getCause() instanceof SandboxException);
    }

    @Test
    void rawJsonAndBulkMetadataFollowLastWriteWinsOrder() {
        VpcConfig firstVpc = VpcConfig.builder().vpcId("vpc-first").build();
        VpcConfig secondVpc = VpcConfig.builder().vpcId("vpc-second").build();
        Map<String, String> overrides = new LinkedHashMap<String, String>();
        overrides.put("raw", "bulk");
        overrides.put("custom.json", "bulk-json");

        Map<String, String> metadata = SandboxMetadata.builder()
                .vpc(firstVpc)
                .put(SandboxMetadata.VPC_METADATA_KEY, "raw-vpc")
                .put("raw", "first")
                .putJson("custom.json", firstVpc)
                .put("raw", "second")
                .putJson("custom.json", secondVpc)
                .putAll(overrides)
                .build();

        assertEquals("raw-vpc", metadata.get(SandboxMetadata.VPC_METADATA_KEY));
        assertEquals("bulk", metadata.get("raw"));
        assertEquals("bulk-json", metadata.get("custom.json"));

        Map<String, String> typedOverride = SandboxMetadata.builder()
                .put(SandboxMetadata.VPC_METADATA_KEY, "raw-vpc")
                .vpc(secondVpc)
                .build();
        assertEquals(SandboxMetadata.toJson(secondVpc),
                typedOverride.get(SandboxMetadata.VPC_METADATA_KEY));
    }

    @Test
    void ignoresNullInputsAndReturnsIndependentBuildCopies() {
        Map<String, String> first = SandboxMetadata.builder()
                .vpc(null)
                .juicefs(null)
                .oss(null)
                .agenticBucket(null)
                .nas(null)
                .roleArn(null)
                .roleArn("")
                .put(null, "value")
                .put("key", null)
                .putJson(null, VpcConfig.builder().build())
                .putJson("json", null)
                .putAll(null)
                .put("kept", "value")
                .build();

        assertEquals(Collections.singletonMap("kept", "value"), first);

        SandboxMetadata.Builder builder = SandboxMetadata.builder().put("key", "initial");
        Map<String, String> snapshot = builder.build();
        builder.put("key", "updated");
        snapshot.put("local", "change");

        assertEquals("initial", snapshot.get("key"));
        assertEquals("updated", builder.build().get("key"));
        assertNull(builder.build().get("local"));
    }

    private static VpcConfig unserializableVpc() {
        return VpcConfig.builder().vSwitchIds(new AbstractList<String>() {
            @Override
            public String get(int index) {
                throw new IllegalStateException("serialization failure");
            }

            @Override
            public int size() {
                return 1;
            }
        }).build();
    }
}
