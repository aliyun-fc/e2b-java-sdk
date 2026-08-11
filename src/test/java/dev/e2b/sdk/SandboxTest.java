package dev.e2b.sdk;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.exception.AuthenticationException;
import dev.e2b.sdk.exception.RateLimitException;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.exception.SandboxNotFoundException;
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
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Request-ID", "req-create-1"));

        Sandbox sandbox = Sandbox.create("base", config);

        assertEquals("sbx-abc",            sandbox.getSandboxId());
        assertEquals("sandbox.e2b.app",    sandbox.getSandboxDomain());
        assertEquals("req-create-1",       sandbox.getRequestId());
        assertEquals("req-create-1",       sandbox.getHeaders().get("X-Request-ID"));
        assertNull(sandbox.getTrafficAccessToken());
        assertNotNull(sandbox.getCommands());
        assertNotNull(sandbox.getFiles());
        assertNotNull(sandbox.getGit());
    }

    @Test
    void create_parsesTrafficAccessTokenWhenPublicTrafficRestricted() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-secure\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"base\",\"clientID\":\"c-1\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-abc\","
                        + "\"trafficAccessToken\":\"traffic-secret\","
                        + "\"network\":{\"allowPublicTraffic\":false}}")
                .setHeader("Content-Type", "application/json"));

        NewSandbox opts = NewSandbox.builder()
                .network(SandboxNetworkOpts.builder().allowPublicTraffic(false).build())
                .build();
        Sandbox sandbox = Sandbox.create("base", config, opts);

        assertEquals("sbx-secure", sandbox.getSandboxId());
        assertEquals("traffic-secret", sandbox.getTrafficAccessToken());
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

    @Test
    void getInfo_notFound_preservesRequestIdAndHeaders() {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"message\":\"not found\"}")
                .setHeader("Content-Type", "application/json")
                .setHeader("X-Request-ID", "req-404")
                .setHeader("x-custom", "v1"));

        SandboxNotFoundException ex = assertThrows(
                SandboxNotFoundException.class, () -> Sandbox.getInfo("missing", config));
        assertEquals(404, ex.getStatusCode());
        assertEquals("req-404", ex.getRequestId());
        assertEquals("req-404", ex.getHeaders().get("X-Request-ID"));
        assertEquals("v1", ex.getHeaders().get("x-custom"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void getInfo_serverError_fallsBackToFcRequestId() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("boom")
                .setHeader("x-fc-request-id", "fc-req-500"));

        SandboxException ex = assertThrows(SandboxException.class, () -> Sandbox.getInfo("sbx-1", config));
        assertEquals(500, ex.getStatusCode());
        assertEquals("fc-req-500", ex.getRequestId());
        assertEquals("fc-req-500", ex.getHeaders().get("x-fc-request-id"));
        assertFalse(ex instanceof SandboxNotFoundException);
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
                .setHeader("Content-Type", "application/json")
                .setHeader("x-fc-request-id", "fc-req-connect-1"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        assertEquals("sbx-xyz", sandbox.getSandboxId());
        assertEquals("fc-req-connect-1", sandbox.getRequestId());
        assertEquals("fc-req-connect-1", sandbox.getHeaders().get("x-fc-request-id"));
        assertNull(sandbox.getTrafficAccessToken());
    }

    @Test
    void connect_parsesTrafficAccessTokenWhenPresent() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-xyz\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"python\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-xyz\","
                        + "\"trafficAccessToken\":\"connect-traffic-tok\"}")
                .setHeader("Content-Type", "application/json"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        assertEquals("sbx-xyz", sandbox.getSandboxId());
        assertEquals("connect-traffic-tok", sandbox.getTrafficAccessToken());
    }

    // -------------------------------------------------------------------------
    // Sandbox.isRunning
    // -------------------------------------------------------------------------

    @Test
    void isRunning_returnsFalseOnNotFound() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-xyz\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"python\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-xyz\"}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setResponseCode(404).setBody("gone"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        assertFalse(sandbox.isRunning());
    }

    @Test
    void isRunning_rethrowsAuthenticationException() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-xyz\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"python\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-xyz\"}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("unauthorized")
                .setHeader("X-Request-ID", "req-401"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        AuthenticationException ex = assertThrows(AuthenticationException.class, sandbox::isRunning);
        assertEquals(401, ex.getStatusCode());
        assertEquals("req-401", ex.getRequestId());
    }

    @Test
    void isRunning_rethrowsRateLimitException() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-xyz\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"python\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-xyz\"}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("slow down")
                .setHeader("X-Request-ID", "req-429"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        RateLimitException ex = assertThrows(RateLimitException.class, sandbox::isRunning);
        assertEquals(429, ex.getStatusCode());
        assertEquals("req-429", ex.getRequestId());
    }

    @Test
    void isRunning_rethrowsServerError() {
        server.enqueue(new MockResponse()
                .setBody("{\"sandboxID\":\"sbx-xyz\",\"domain\":\"sandbox.e2b.app\","
                        + "\"templateID\":\"python\",\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"tok-xyz\"}")
                .setHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("unavailable")
                .setHeader("x-fc-request-id", "fc-req-503"));

        Sandbox sandbox = Sandbox.connect("sbx-xyz", config);
        SandboxException ex = assertThrows(SandboxException.class, sandbox::isRunning);
        assertEquals(503, ex.getStatusCode());
        assertEquals("fc-req-503", ex.getRequestId());
        assertFalse(ex instanceof SandboxNotFoundException);
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
                .setHeader("Content-Type", "application/json")
                .setHeader("x-next-token", "snap-token-2")
                .setHeader("X-Request-ID", "req-snap-1"));

        ListSnapshotsOutput output = Sandbox.listSnapshots(config, "sbx-1", 50, null);
        List<SnapshotInfo> snapshots = output.getSnapshots();

        assertEquals(1, snapshots.size());
        assertEquals("snap-1", snapshots.get(0).getSnapshotId());
        assertEquals("sbx-1", snapshots.get(0).getSandboxId());
        assertEquals(java.util.Arrays.asList("a", "b"), snapshots.get(0).getNames());
        assertEquals("snap-token-2", output.getNextToken());
        assertEquals("req-snap-1", output.getRequestId());

        okhttp3.mockwebserver.RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().startsWith("/snapshots"), request.getPath());
        assertTrue(request.getPath().contains("sandboxID=sbx-1"), request.getPath());
        assertTrue(request.getPath().contains("limit=50"), request.getPath());
    }

    @Test
    void listSnapshots_sendsNextTokenForPagination() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setBody("[]")
                .setHeader("Content-Type", "application/json"));

        Sandbox.listSnapshots(config, null, 10, "cursor-abc");

        okhttp3.mockwebserver.RecordedRequest request = server.takeRequest();
        assertTrue(request.getPath().contains("nextToken=cursor-abc"), request.getPath());
        assertTrue(request.getPath().contains("limit=10"), request.getPath());
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
