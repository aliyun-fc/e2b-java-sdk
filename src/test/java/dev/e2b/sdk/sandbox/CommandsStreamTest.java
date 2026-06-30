package dev.e2b.sdk.sandbox;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.model.CommandResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the Connect server-streaming envelope parsing used by {@link Commands#run}.
 *
 * <p>The envd {@code process.Process/Start} RPC returns enveloped {@code ProcessEvent} frames:
 * {@code [1-byte flags][4-byte big-endian length][JSON payload]}, with the final frame carrying
 * the end-of-stream flag (0x02). stdout/stderr are base64-encoded protojson {@code bytes}.
 */
class CommandsStreamTest {

    private MockWebServer envd;
    private E2bApiClient api;

    @BeforeEach
    void setUp() throws IOException {
        envd = new MockWebServer();
        envd.start();
        ConnectionConfig cfg = ConnectionConfig.builder().apiKey("k").build();
        api = new E2bApiClient(cfg);
    }

    @AfterEach
    void tearDown() throws IOException {
        envd.shutdown();
    }

    @Test
    void run_parsesStreamedStdoutAndExitCode() throws Exception {
        Buffer body = new Buffer();
        body.write(frame(0x00, "{\"event\":{\"start\":{\"pid\":42}}}"));
        body.write(frame(0x00, "{\"event\":{\"data\":{\"stdout\":\""
                + b64("Hello, World!\n") + "\"}}}"));
        body.write(frame(0x00, "{\"event\":{\"data\":{\"stderr\":\""
                + b64("warn\n") + "\"}}}"));
        body.write(frame(0x00, "{\"event\":{\"end\":{\"exitCode\":0,\"exited\":true}}}"));
        body.write(frame(0x02, "{}"));

        envd.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/connect+json")
                .setBody(body));

        Commands commands = new Commands(api, "sbx-1",
                envd.url("").toString().replaceAll("/$", ""), "tok");

        CommandResult result = commands.run("echo 'Hello, World!'");

        assertEquals(0, result.getExitCode());
        assertEquals("Hello, World!\n", result.getStdout());
        assertEquals("warn\n", result.getStderr());
        assertTrue(result.isSuccess());

        RecordedRequest sent = envd.takeRequest();
        assertEquals("/process.Process/Start", sent.getPath());
        assertEquals("tok", sent.getHeader("X-Access-Token"));
        // request body is an envelope: skip 5-byte header, the JSON must wrap the command in bash -l -c
        byte[] reqBytes = sent.getBody().readByteArray();
        String reqJson = new String(reqBytes, 5, reqBytes.length - 5, StandardCharsets.UTF_8);
        assertTrue(reqJson.contains("\"/bin/bash\""), "request should invoke /bin/bash: " + reqJson);
        assertTrue(reqJson.contains("echo 'Hello, World!'"), "request should carry the command: " + reqJson);
    }

    @Test
    void run_nonZeroExitCode() throws Exception {
        Buffer body = new Buffer();
        body.write(frame(0x00, "{\"event\":{\"end\":{\"exitCode\":7,\"exited\":true}}}"));
        body.write(frame(0x02, "{}"));

        envd.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/connect+json")
                .setBody(body));

        Commands commands = new Commands(api, "sbx-1",
                envd.url("").toString().replaceAll("/$", ""), "tok");

        CommandResult result = commands.run("exit 7");
        assertEquals(7, result.getExitCode());
        assertFalse(result.isSuccess());
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] frame(int flags, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(flags & 0xFF);
        int len = payload.length;
        out.write((len >>> 24) & 0xFF);
        out.write((len >>> 16) & 0xFF);
        out.write((len >>> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(payload);
        return out.toByteArray();
    }
}
