package dev.e2b.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.TemplateBuildStartV2;
import dev.e2b.sdk.util.EnvVars;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Env var wire behavior must match sandbox-gateway:
 * <ul>
 *   <li>JSON field name {@code envVars} (not {@code envs} / {@code environmentVariables})</li>
 *   <li>trim keys/values; drop empty keys (gateway {@code e2bTrimEnvVars})</li>
 *   <li>omit empty maps from JSON ({@code omitempty})</li>
 * </ul>
 */
class EnvVarsWireTest {

    private MockWebServer server;
    private ConnectionConfig config;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        config = ConnectionConfig.builder()
                .apiKey("e2b_test_key")
                .apiUrl(server.url("/").toString().replaceAll("/$", ""))
                .domain("localhost")
                .build();
        mapper = new E2bApiClient(config).mapper;
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void normalize_matchesGatewayTrim() {
        Map<String, String> raw = new HashMap<String, String>();
        raw.put(" FOO ", " bar ");
        raw.put("", "skip");
        raw.put("  ", "also-skip");
        raw.put("KEEP", "  value  ");

        Map<String, String> normalized = EnvVars.normalize(raw);
        assertEquals("bar", normalized.get("FOO"));
        assertEquals("value", normalized.get("KEEP"));
        assertEquals(2, normalized.size());
        assertNull(EnvVars.normalize(null));
        assertNull(EnvVars.normalize(Collections.<String, String>emptyMap()));
        assertNull(EnvVars.normalize(Collections.singletonMap("  ", "x")));
    }

    @Test
    void templateBuildStart_serializesEnvVarsAndOmitsEmpty() throws Exception {
        Map<String, String> env = new HashMap<String, String>();
        env.put(" TEMPLATE_ENV ", " from-template ");
        env.put("", "drop-me");

        String json = mapper.writeValueAsString(TemplateBuildStartV2.builder()
                .fromImage("python:3.11")
                .envVars(env)
                .build());
        assertTrue(json.contains("\"fromImage\":\"python:3.11\""), json);
        assertTrue(json.contains("\"envVars\""), json);
        assertTrue(json.contains("\"TEMPLATE_ENV\":\"from-template\""), json);
        assertFalse(json.contains("drop-me"), json);

        String emptyJson = mapper.writeValueAsString(TemplateBuildStartV2.builder()
                .fromImage("python:3.11")
                .envVars(Collections.<String, String>emptyMap())
                .build());
        assertFalse(emptyJson.contains("envVars"), emptyJson);

        String blankJson = mapper.writeValueAsString(TemplateBuildStartV2.builder()
                .fromImage("python:3.11")
                .envVars(Collections.singletonMap("  ", "x"))
                .build());
        assertFalse(blankJson.contains("envVars"), blankJson);
    }

    @Test
    void newSandbox_serializesEnvVarsAndOmitsEmpty() throws Exception {
        Map<String, String> env = new HashMap<String, String>();
        env.put(" USER_ENV ", " override ");

        String json = mapper.writeValueAsString(NewSandbox.builder()
                .templateName("base")
                .envVars(env)
                .build());
        assertTrue(json.contains("\"envVars\""), json);
        assertTrue(json.contains("\"USER_ENV\":\"override\""), json);

        String emptyJson = mapper.writeValueAsString(NewSandbox.builder()
                .templateName("base")
                .envVars(Collections.<String, String>emptyMap())
                .build());
        assertFalse(emptyJson.contains("envVars"), emptyJson);
    }

    @Test
    void startBuild_postsNormalizedEnvVars() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(202));

        Map<String, String> env = new HashMap<String, String>();
        env.put(" APP_ENV ", " prod ");
        env.put("", "ignored");

        Template.startBuild(
                "tpl-1",
                "bld-1",
                TemplateBuildStartV2.builder().fromImage("node:20").envVars(env).build(),
                config);

        RecordedRequest req = server.takeRequest();
        assertEquals("/v2/templates/tpl-1/builds/bld-1", req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"envVars\":{\"APP_ENV\":\"prod\"}"), body);
        assertFalse(body.contains("ignored"), body);
    }

    @Test
    void sandboxCreate_postsNormalizedEnvVars() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-1\",\"templateID\":\"base\",\"clientID\":\"c-1\","
                        + "\"envdVersion\":\"0.1.0\",\"envdAccessToken\":\"tok\"}")
                .setHeader("Content-Type", "application/json"));

        Map<String, String> env = new HashMap<String, String>();
        env.put(" SHARED ", " from-sandbox ");
        Sandbox.create("base", config, NewSandbox.builder().envVars(env).timeout(60).build());

        RecordedRequest req = server.takeRequest();
        assertEquals("/sandboxes", req.getPath());
        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"envVars\":{\"SHARED\":\"from-sandbox\"}"), body);
        assertTrue(body.contains("\"templateName\":\"base\"") || body.contains("\"templateID\":\"base\""), body);
    }
}
