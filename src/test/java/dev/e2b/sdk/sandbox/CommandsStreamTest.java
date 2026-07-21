package dev.e2b.sdk.sandbox;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.SandboxException;
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
import java.util.concurrent.TimeUnit;

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
    void tearDown() {
        try {
            envd.shutdown();
        } catch (IOException ignored) {
            // Throttled background streams can leave the dispatcher busy; force close.
            try {
                envd.close();
            } catch (IOException ignored2) {
            }
        }
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

        Commands commands = new Commands(api,
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

        Commands commands = new Commands(api,
                envd.url("").toString().replaceAll("/$", ""), "tok");

        CommandResult result = commands.run("exit 7");
        assertEquals(7, result.getExitCode());
        assertFalse(result.isSuccess());
    }

    @Test
    void closeActive_cancelsOpenBackgroundStreamAndReleasesDrainThread() throws Exception {
        // A background process that has started but not yet ended: the stream stays open, so the
        // drain thread blocks reading. Throttling keeps the body from completing while we cancel.
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            padding.append('x');
        }
        Buffer body = new Buffer();
        body.write(frame(0x00, "{\"event\":{\"start\":{\"pid\":42}}}"));
        body.write(frame(0x00, "{\"event\":{\"data\":{\"stdout\":\"" + b64(padding.toString()) + "\"}}}"));
        // Intentionally no end (0x02) frame — the stream would otherwise stay open forever.

        envd.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/connect+json")
                .setBody(body)
                // Deliver the first 64 bytes (enough for the start frame) immediately, then stall,
                // so runBackground returns a live handle whose drain thread is blocked on read.
                .throttleBody(64, 5, TimeUnit.SECONDS));

        Commands commands = new Commands(api,
                envd.url("").toString().replaceAll("/$", ""), "tok");

        CommandHandle handle = commands.runBackground("sleep 999");
        assertEquals(42, handle.getPid());
        assertFalse(handle.isDone(), "background command should still be running");

        // Simulate sandbox destruction: cancel in-flight background streams.
        long t0 = System.nanoTime();
        commands.closeActive();

        // The blocked drain read is interrupted, so the future completes (exceptionally) promptly
        // instead of hanging forever on the disabled read timeout (readTimeout=0).
        SandboxException ex = assertThrows(SandboxException.class,
                () -> handle.waitForExit(3, TimeUnit.SECONDS));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertTrue(handle.isDone(), "drain thread should have finished after cancel");
        assertNotNull(ex);
        // Fast completion (well under the 3s wait) proves the stream was actually cancelled rather
        // than left to hang; without the cancel the drain would never return.
        assertTrue(elapsedMs < 2000, "cancel should unblock the drain promptly, took " + elapsedMs + "ms");

        // Ensure the throttled MockWebServer connection is released before tearDown.
        handle.disconnect();
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
