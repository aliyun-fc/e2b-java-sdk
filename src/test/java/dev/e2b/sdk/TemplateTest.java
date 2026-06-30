package dev.e2b.sdk;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateTest {

    private MockWebServer server;
    private ConnectionConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        config = ConnectionConfig.builder()
                .apiKey("e2b_test_key")
                .apiUrl(server.url("/").toString().replaceAll("/$", ""))
                .domain("localhost")
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void list_returnsTemplates() {
        server.enqueue(new MockResponse()
                .setBody("[{\"templateID\":\"tpl-1\",\"buildID\":\"b-1\",\"cpuCount\":2,"
                        + "\"memoryMB\":2048,\"diskSizeMB\":512,\"public\":false,"
                        + "\"aliases\":[\"base\"],\"names\":[\"base\"],\"buildStatus\":\"ready\"}]")
                .setHeader("Content-Type", "application/json"));

        List<TemplateInfo> templates = Template.list(config);
        assertEquals(1, templates.size());
        assertEquals("tpl-1", templates.get(0).getTemplateId());
    }

    @Test
    void get_returnsTemplateWithBuilds() {
        server.enqueue(new MockResponse()
                .setBody("{\"templateID\":\"tpl-1\",\"public\":true,\"names\":[\"base\"],"
                        + "\"builds\":[{\"buildID\":\"b-1\",\"status\":\"ready\",\"cpuCount\":2,\"memoryMB\":2048}]}")
                .setHeader("Content-Type", "application/json"));

        TemplateWithBuilds template = Template.get("base", config);
        assertEquals("tpl-1", template.getTemplateId());
        assertEquals(1, template.getBuilds().size());
    }

    @Test
    @SuppressWarnings("deprecation")
    void createAndStartBuild() {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .setBody("{\"templateID\":\"tpl-new\",\"buildID\":\"b-new\",\"cpuCount\":2,"
                        + "\"memoryMB\":2048,\"diskSizeMB\":512,\"public\":false,\"buildStatus\":\"waiting\"}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(202));

        TemplateLegacy created = Template.create(
                TemplateBuildRequestV2.builder().alias("my-template").cpuCount(2).memoryMb(2048).build(),
                config);
        assertEquals("tpl-new", created.getTemplateId());
        assertEquals("b-new", created.getBuildId());

        Template.startBuild(
                created.getTemplateId(),
                created.getBuildId(),
                TemplateBuildStartV2.builder().fromImage("python:3.11").build(),
                config);
    }

    @Test
    void updateAndDelete() {
        server.enqueue(new MockResponse()
                .setBody("{\"names\":[\"base\"]}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(204));

        TemplateUpdateResponse updated = Template.setPublic("tpl-1", true, config);
        assertEquals(Collections.singletonList("base"), updated.getNames());
        assertTrue(Template.delete("tpl-1", config));
    }
}
