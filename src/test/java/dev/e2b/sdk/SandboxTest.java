package dev.e2b.sdk;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.exception.AuthenticationException;
import dev.e2b.sdk.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the e2b Java SDK using MockWebServer.
 */
class SandboxTest {

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

    // -------------------------------------------------------------------------
    // Sandbox.create
    // -------------------------------------------------------------------------

    @Test
    void create_returnsRunningsandbox() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-abc\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"base\",\"clientID\":\"c-1\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-abc\"}")
                .setHeader("Content-Type", "application/json"));

        Sandbox sandbox = Sandbox.create("base", config);

        assertEquals("sbx-abc",            sandbox.getSandboxId());
        assertEquals("sandbox.e2b.app",    sandbox.getSandboxDomain());
        assertNotNull(sandbox.getCommands());
        assertNotNull(sandbox.getFiles());
        assertNotNull(sandbox.getGit());
    }

    @Test
    void create_missingApiKey_throwsAuthenticationException() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getenv("E2B_API_KEY") == null || System.getenv("E2B_API_KEY").isEmpty(),
                "Skip when E2B_API_KEY is set in the environment");

        ConnectionConfig badConfig = ConnectionConfig.builder()
                .apiUrl(server.url("/").toString().replaceAll("/$", ""))
                .build();

        assertThrows(AuthenticationException.class, () -> Sandbox.create("base", badConfig));
    }

    // -------------------------------------------------------------------------
    // Sandbox.connect
    // -------------------------------------------------------------------------

    @Test
    void connect_existingSandbox() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-xyz\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"python\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-xyz\"}")
                .setHeader("Content-Type", "application/json"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        assertEquals("sbx-xyz", sandbox.getSandboxId());
    }

    // -------------------------------------------------------------------------
    // Sandbox.getInfo
    // -------------------------------------------------------------------------

    @Test
    void getInfo_returnsSandboxAndRequestId() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-1\",\"templateID\":\"base\",\"state\":\"running\","
                        + "\"cpuCount\":2,\"memoryMB\":512,\"envdVersion\":\"0.1.0\",\"clientID\":\"c-1\","
                        + "\"startedAt\":\"2024-01-01T00:00:00Z\",\"endAt\":\"2024-01-01T00:05:00Z\"}")
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Request-ID", "req-get-1"));

        GetSandboxOutput output = Sandbox.getInfo("sbx-1", config);
        assertEquals("sbx-1", output.getSandbox().getSandboxId());
        assertEquals(SandboxState.RUNNING, output.getSandbox().getState());
        assertEquals("req-get-1", output.getRequestId());
        assertEquals("req-get-1", output.getHeaders().get("X-Request-ID"));
        assertEquals("application/json", output.getHeaders().get("Content-Type"));
    }

    @Test
    void getInfo_fallsBackToFcRequestId() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-1\",\"templateID\":\"base\",\"state\":\"running\","
                        + "\"cpuCount\":2,\"memoryMB\":512,\"envdVersion\":\"0.1.0\",\"clientID\":\"c-1\","
                        + "\"startedAt\":\"2024-01-01T00:00:00Z\",\"endAt\":\"2024-01-01T00:05:00Z\"}")
                .setHeader("Content-Type", "application/json")
                .setHeader("x-fc-request-id", "fc-req-get-1"));

        GetSandboxOutput output = Sandbox.getInfo("sbx-1", config);
        assertEquals("fc-req-get-1", output.getRequestId());
        assertEquals("fc-req-get-1", output.getHeaders().get("x-fc-request-id"));
    }

    // -------------------------------------------------------------------------
    // Sandbox.list
    // -------------------------------------------------------------------------

    @Test
    void list_returnsSandboxInfoList() {
        server.enqueue(new MockResponse()
                .setBody("[{\"sandboxID\":\"sbx-1\",\"templateID\":\"base\",\"state\":\"running\","
                        + "\"cpuCount\":2,\"memoryMB\":512,\"envdVersion\":\"0.1.0\",\"clientID\":\"c-1\","
                        + "\"startedAt\":\"2024-01-01T00:00:00Z\",\"endAt\":\"2024-01-01T00:05:00Z\"},"
                        + "{\"sandboxID\":\"sbx-2\",\"templateID\":\"python\",\"state\":\"paused\","
                        + "\"cpuCount\":4,\"memoryMB\":1024,\"envdVersion\":\"0.1.0\",\"clientID\":\"c-2\","
                        + "\"startedAt\":\"2024-01-01T00:00:00Z\",\"endAt\":\"2024-01-01T00:05:00Z\"}]")
                .setHeader("Content-Type", "application/json")
                .setHeader("x-next-token", "token-2")
                .setHeader("X-Request-ID", "req-abc"));

        ListSandboxesOutput output = Sandbox.list(config);
        List<SandboxInfo> sandboxes = output.getSandboxes();
        assertEquals(2, sandboxes.size());
        assertEquals("sbx-1",            sandboxes.get(0).getSandboxId());
        assertEquals(SandboxState.PAUSED, sandboxes.get(1).getState());
        assertEquals("token-2", output.getNextToken());
        assertEquals("req-abc", output.getRequestId());
        assertEquals("req-abc", output.getHeaders().get("X-Request-ID"));
        assertEquals("token-2", output.getHeaders().get("x-next-token"));
    }

    // -------------------------------------------------------------------------
    // Sandbox.listSnapshots
    // -------------------------------------------------------------------------

    @Test
    void listSnapshots_parsesAndSendsFilter() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("[{\"snapshotID\":\"snap-1\",\"sandboxID\":\"sbx-1\",\"names\":[\"a\",\"b\"]}]")
                .setHeader("Content-Type", "application/json"));

        List<SnapshotInfo> snapshots = Sandbox.listSnapshots(config, "sbx-1", 50, null).getSnapshots();

        assertEquals(1, snapshots.size());
        assertEquals("snap-1", snapshots.get(0).getSnapshotId());
        assertEquals("sbx-1", snapshots.get(0).getSandboxId());
        assertEquals(java.util.Arrays.asList("a", "b"), snapshots.get(0).getNames());

        okhttp3.mockwebserver.RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().startsWith("/snapshots"), request.getPath());
        assertTrue(request.getPath().contains("sandboxID=sbx-1"), request.getPath());
        assertTrue(request.getPath().contains("limit=50"), request.getPath());
    }

    // -------------------------------------------------------------------------
    // Sandbox.kill
    // -------------------------------------------------------------------------

    @Test
    void kill_byId_returnsTrue() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("X-Request-ID", "req-kill-1"));

        KillSandboxOutput result = Sandbox.kill("sbx-abc", config);
        assertTrue(result.isKilled());
        assertEquals("req-kill-1", result.getRequestId());
        assertEquals("req-kill-1", result.getHeaders().get("X-Request-ID"));
    }

    // -------------------------------------------------------------------------
    // SandboxInfo deserialization
    // -------------------------------------------------------------------------

    @Test
    void sandboxInfo_camelCaseFields() {
        SandboxInfo info = new SandboxInfo();
        info.setSandboxId("sbx-test");
        info.setCpuCount(4);
        info.setMemoryMb(2048);
        info.setAllowInternetAccess(true);
        info.setEnvdVersion("0.2.0");

        assertEquals("sbx-test", info.getSandboxId());
        assertEquals(4,          info.getCpuCount());
        assertEquals(2048,       info.getMemoryMb());
        assertTrue(              info.getAllowInternetAccess());
    }

    // -------------------------------------------------------------------------
    // Model: CommandResult
    // -------------------------------------------------------------------------

    @Test
    void commandResult_isSuccess() {
        CommandResult success = CommandResult.builder().exitCode(0).stdout("ok").stderr("").build();
        CommandResult failure = CommandResult.builder().exitCode(1).stdout("").stderr("err").build();

        assertTrue(success.isSuccess());
        assertFalse(failure.isSuccess());
    }

    // -------------------------------------------------------------------------
    // ConnectionConfig
    // -------------------------------------------------------------------------

    @Test
    void connectionConfig_resolvedApiUrl() {
        ConnectionConfig cfg = ConnectionConfig.builder()
                .apiKey("key")
                .domain("e2b.app")
                .build();

        assertEquals("https://api.e2b.app", cfg.resolvedApiUrl());
    }

    @Test
    void connectionConfig_getHost() {
        ConnectionConfig cfg = ConnectionConfig.builder().apiKey("k").build();
        assertEquals("3000-sbx-123.sandbox.e2b.app",
                cfg.getHost("sbx-123", "sandbox.e2b.app", 3000));
    }

    // -------------------------------------------------------------------------
    // NewSandbox builder
    // -------------------------------------------------------------------------

    @Test
    void newSandbox_builder() {
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("project", "my-project");
        Map<String, String> envVars = new HashMap<String, String>();
        envVars.put("DEBUG", "true");

        NewSandbox ns = NewSandbox.builder()
                .templateId("python")
                .timeout(600)
                .metadata(metadata)
                .envVars(envVars)
                .allowInternetAccess(false)
                .build();

        assertEquals("python",      ns.getTemplateId());
        assertEquals(600,           (int) ns.getTimeout());
        assertEquals("my-project",  ns.getMetadata().get("project"));
        assertEquals("true",        ns.getEnvVars().get("DEBUG"));
        assertFalse(ns.getAllowInternetAccess());
    }
}
