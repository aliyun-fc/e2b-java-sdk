package dev.e2b.sdk.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that sandbox responses containing volume mounts can be deserialized.
 *
 * <p>{@code SandboxVolumeMount} historically had only {@code @Data @Builder}, which makes
 * Lombok generate a package-private all-args constructor and no default constructor, so
 * Jackson failed with {@code InvalidDefinitionException} on every {@code Sandbox.getInfo}
 * and {@code Sandbox.list} response that included {@code volumeMounts}.
 */
class SandboxVolumeMountTest {

    // Mirrors the mapper configuration used by E2bApiClient.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JavaTimeModule());

    @Test
    void deserializesInsideSandboxInfoDetailResponse() throws Exception {
        // Shape mirrors the gateway e2bSandboxDetailResponse (camelCase with Go-style acronyms).
        String json = "{"
                + "\"sandboxID\":\"sbx-123\","
                + "\"templateID\":\"tpl-456\","
                + "\"clientID\":\"client-1\","
                + "\"startedAt\":\"2026-09-04T00:00:00Z\","
                + "\"endAt\":\"2026-09-05T00:00:00Z\","
                + "\"cpuCount\":2,"
                + "\"memoryMB\":512,"
                + "\"diskSizeMB\":1024,"
                + "\"state\":\"running\","
                + "\"envdVersion\":\"0.0.1\","
                + "\"volumeMounts\":[{\"name\":\"myvol\",\"path\":\"/mnt/data\"}]"
                + "}";

        SandboxInfo info = mapper.readValue(json, SandboxInfo.class);

        assertEquals("sbx-123", info.getSandboxId());
        assertEquals("tpl-456", info.getTemplateId());
        assertNotNull(info.getVolumeMounts());
        assertEquals(1, info.getVolumeMounts().size());
        assertEquals("myvol", info.getVolumeMounts().get(0).getName());
        assertEquals("/mnt/data", info.getVolumeMounts().get(0).getPath());
    }

    @Test
    void deserializesInsideListedSandboxResponse() throws Exception {
        // The list endpoint returns one object per sandbox; each entry may carry volumeMounts.
        String json = "{"
                + "\"sandboxID\":\"sbx-789\","
                + "\"templateID\":\"tpl-456\","
                + "\"state\":\"paused\","
                + "\"volumeMounts\":[{\"name\":\"afs\",\"path\":\"/workspace\"}]"
                + "}";

        SandboxInfo info = mapper.readValue(json, SandboxInfo.class);

        assertEquals("sbx-789", info.getSandboxId());
        assertEquals(1, info.getVolumeMounts().size());
        assertEquals("afs", info.getVolumeMounts().get(0).getName());
        assertEquals("/workspace", info.getVolumeMounts().get(0).getPath());
    }

    @Test
    void builderStillWorks() {
        SandboxVolumeMount vm = SandboxVolumeMount.builder()
                .name("myvol")
                .path("/mnt/data")
                .build();

        assertEquals("myvol", vm.getName());
        assertEquals("/mnt/data", vm.getPath());
    }
}
