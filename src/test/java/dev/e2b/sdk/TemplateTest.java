package dev.e2b.sdk;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Request-ID", "req-tpl-list-1"));

        ListTemplatesOutput output = Template.list(config);
        List<TemplateInfo> templates = output.getTemplates();
        assertEquals(1, templates.size());
        assertEquals("tpl-1", templates.get(0).getTemplateId());
        assertEquals("req-tpl-list-1", output.getRequestId());
        assertEquals("req-tpl-list-1", output.getHeaders().get("X-Request-ID"));
    }

    @Test
    void get_returnsTemplateWithBuilds() {
        server.enqueue(new MockResponse()
                .setBody("{\"templateID\":\"tpl-1\",\"public\":true,\"names\":[\"base\"],"
                        + "\"builds\":[{\"buildID\":\"b-1\",\"status\":\"ready\",\"cpuCount\":2,\"memoryMB\":2048}]}")
                .setHeader("Content-Type", "application/json")
                .setHeader("x-next-token", "builds-token-2")
                .setHeader("X-Request-ID", "req-tpl-get-1"));

        GetTemplateOutput output = Template.get("base", config, 1, null);
        TemplateWithBuilds template = output.getTemplate();
        assertEquals("tpl-1", template.getTemplateId());
        assertEquals(1, template.getBuilds().size());
        assertEquals("builds-token-2", output.getNextToken());
        assertEquals("req-tpl-get-1", output.getRequestId());
    }

    @Test
    void get_sendsNextTokenForPagination() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("{\"templateID\":\"tpl-1\",\"public\":true,\"names\":[\"base\"],\"builds\":[]}")
                .setHeader("Content-Type", "application/json"));

        Template.get("base", config, 5, "builds-cursor");

        okhttp3.mockwebserver.RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("nextToken=builds-cursor"), request.getPath());
        assertTrue(request.getPath().contains("limit=5"), request.getPath());
    }

    @Test
    @SuppressWarnings("deprecation")
    void createAndStartBuild() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .setBody("{\"templateID\":\"tpl-new\",\"buildID\":\"b-new\",\"cpuCount\":2,"
                        + "\"memoryMB\":2048,\"diskSizeMB\":512,\"public\":false,\"buildStatus\":\"waiting\"}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(202));

        TemplateLegacy created = Template.create(
                TemplateBuildRequestV2.builder().alias("my-template").cpuCount(2).memoryMb(2048).build(),
                config).getTemplate();
        assertEquals("tpl-new", created.getTemplateId());
        assertEquals("b-new", created.getBuildId());

        Map<String, String> envVars = new HashMap<String, String>();
        envVars.put("TEMPLATE_ENV", "from-template");
        Template.startBuild(
                created.getTemplateId(),
                created.getBuildId(),
                TemplateBuildStartV2.builder()
                        .fromImage("python:3.11")
                        .envVars(envVars)
                        .build(),
                config);

        okhttp3.mockwebserver.RecordedRequest buildReq = server.takeRequest();
        assertEquals("/v2/templates", buildReq.getPath());
        buildReq = server.takeRequest();
        assertTrue(buildReq.getPath().contains("/builds/"));
        String body = buildReq.getBody().readUtf8();
        assertTrue(body.contains("\"fromImage\":\"python:3.11\""), body);
        assertTrue(body.contains("\"envVars\""), body);
        assertTrue(body.contains("\"TEMPLATE_ENV\":\"from-template\""), body);
    }

    @Test
    void updateAndDelete() {
        server.enqueue(new MockResponse()
                .setBody("{\"names\":[\"base\"]}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(204));

        TemplateUpdateResponse updated = Template.setPublic("tpl-1", true, config).getResponse();
        assertEquals(Collections.singletonList("base"), updated.getNames());
        assertTrue(Template.delete("tpl-1", config).isDeleted());
    }
}
