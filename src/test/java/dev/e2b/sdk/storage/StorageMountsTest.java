package dev.e2b.sdk.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the storage/network metadata serialization produces the exact wire keys expected by the
 * FC sandbox gateway (camelCase with Go-style casing, e.g. {@code vSwitchIds}).
 */
class StorageMountsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void juicefsMetadataHasExpectedShape() throws Exception {
        JuiceFsConfig cfg = JuiceFsConfig.builder()
                .envs(Collections.singletonMap("BASE_URL", "https://jfs.example"))
                .mountPoints(Collections.singletonList(JuiceFsMountPoint.builder()
                        .volumeName("vol1")
                        .mountDir("/jfs")
                        .remoteDir("/data")
                        .token("tok")
                        .args(Arrays.asList("--writeback", "--no-agent"))
                        .build()))
                .build();

        Map<String, String> metadata = StorageMounts.builder().juicefs(cfg).build();
        assertTrue(metadata.containsKey("fc.sandbox.storage.juicefs"));

        JsonNode node = mapper.readTree(metadata.get("fc.sandbox.storage.juicefs"));
        assertEquals("https://jfs.example", node.get("envs").get("BASE_URL").asText());
        JsonNode mp = node.get("mountPoints").get(0);
        assertEquals("vol1", mp.get("volumeName").asText());
        assertEquals("/jfs", mp.get("mountDir").asText());
        assertEquals("/data", mp.get("remoteDir").asText());
        assertEquals("tok", mp.get("token").asText());
        assertEquals("--writeback", mp.get("args").get(0).asText());
    }

    @Test
    void vpcMetadataUsesGoStyleKeys() throws Exception {
        VpcConfig vpc = VpcConfig.builder()
                .vpcId("vpc-1")
                .securityGroupId("sg-1")
                .vSwitchIds(Collections.singletonList("vsw-1"))
                .build();

        String json = StorageMounts.toJson(vpc);
        JsonNode node = mapper.readTree(json);
        assertEquals("vpc-1", node.get("vpcId").asText());
        assertEquals("sg-1", node.get("securityGroupId").asText());
        // Critical: must be exactly "vSwitchIds", not "VSwitchIds".
        assertTrue(node.has("vSwitchIds"), "expected key 'vSwitchIds' in: " + json);
        assertEquals("vsw-1", node.get("vSwitchIds").get(0).asText());
    }

    @Test
    void ossAndNasAndRoleArnAccumulate() throws Exception {
        Map<String, String> metadata = StorageMounts.builder()
                .oss(OssConfig.builder()
                        .mountPoints(Collections.singletonList(OssMountPoint.builder()
                                .bucketName("b").mountDir("/oss").bucketPath("/p")
                                .endpoint("http://oss").readOnly(false).build()))
                        .build())
                .nas(NasConfig.builder()
                        .mountPoints(Collections.singletonList(NasMountPoint.builder()
                                .serverAddr("nas:/x").mountDir("/nas").build()))
                        .build())
                .roleArn("acs:ram::1:role/r")
                .build();

        assertEquals("acs:ram::1:role/r", metadata.get("fc.sandbox.auth.role"));
        JsonNode oss = mapper.readTree(metadata.get("fc.sandbox.storage.oss"));
        assertEquals("b", oss.get("mountPoints").get(0).get("bucketName").asText());
        assertFalse(oss.get("mountPoints").get(0).get("readOnly").asBoolean());
        JsonNode nas = mapper.readTree(metadata.get("fc.sandbox.storage.nas"));
        assertEquals("nas:/x", nas.get("mountPoints").get(0).get("serverAddr").asText());
    }

    @Test
    void putAllMergesExistingMetadata() {
        Map<String, String> existing = new LinkedHashMap<String, String>();
        existing.put("k", "v");
        Map<String, String> metadata = StorageMounts.builder()
                .putAll(existing)
                .roleArn("arn")
                .build();
        assertEquals("v", metadata.get("k"));
        assertEquals("arn", metadata.get("fc.sandbox.auth.role"));
    }
}
