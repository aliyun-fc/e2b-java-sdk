package dev.e2b.sdk.codeinterpreter;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.exception.TimeoutException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeInterpreterTimeoutTest {

    private MockWebServer server;
    private ConnectionConfig config;
    private IOException executeFailure;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        Interceptor routeSandboxRequestsToMockServer = chain -> {
            Request request = chain.request();
            if ("/execute".equals(request.url().encodedPath())) {
                if (executeFailure != null) {
                    throw executeFailure;
                }
                request = request.newBuilder().url(server.url("/execute")).build();
            }
            return chain.proceed(request);
        };

        config = ConnectionConfig.builder()
                .apiKey("e2b_test_key")
                .apiUrl(server.url("/").toString().replaceAll("/$", ""))
                .domain("sandbox.e2b.test")
                .debug(true)
                .httpClient(new OkHttpClient.Builder()
                        .addInterceptor(routeSandboxRequestsToMockServer)
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void runCodeExplicitTimeoutStopsWaitingForLongExecution() throws InterruptedException {
        enqueueSandboxCreation();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBodyDelay(1500, TimeUnit.MILLISECONDS)
                .setBody("{\"type\":\"stdout\",\"text\":\"done\\n\",\"timestamp\":1}\n"));

        CodeInterpreter interpreter = CodeInterpreter.from(
                Sandbox.create(CodeInterpreter.DEFAULT_TEMPLATE, config));

        long startedAt = System.nanoTime();
        TimeoutException error = assertThrows(TimeoutException.class,
                () -> interpreter.runCode("slow()", "python", null, null, 1));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsedMillis >= 800, "timeout fired too early: " + elapsedMillis + "ms");
        assertTrue(elapsedMillis < 2500, "timeout was not enforced promptly: " + elapsedMillis + "ms");
        assertTrue(error.getMessage().contains("1"));

        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
        RecordedRequest execute = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(execute);
        assertFalse(execute.getBody().readUtf8().contains("\"timeout\""),
                "timeout is a client-side deadline and must not change the /execute payload");
    }

    @Test
    void runCodeExecutionTimeoutIncludesWaitingForResponseHeaders() {
        enqueueSandboxCreation();
        server.enqueue(new MockResponse()
                .setHeadersDelay(1200, TimeUnit.MILLISECONDS)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody("{\"type\":\"stdout\",\"text\":\"done\\n\",\"timestamp\":1}\n"));

        TimeoutException error = assertThrows(TimeoutException.class,
                () -> createInterpreter().runCode(
                        "slow start", "python", null, null, 1));

        assertTrue(error.getMessage().contains("execution timed out"));
    }

    @Test
    void runCodeRequestTimeoutUsesConnectionConfig() {
        config = withRequestTimeout(0.2);
        enqueueSandboxCreation();
        server.enqueue(new MockResponse()
                .setHeadersDelay(1, TimeUnit.SECONDS)
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody("{\"type\":\"stdout\",\"text\":\"late\\n\",\"timestamp\":1}\n"));

        TimeoutException error = assertThrows(TimeoutException.class,
                () -> createInterpreter().runCode("1 + 1", "python", null, null, 5));

        assertTrue(error.getMessage().contains("request"));
    }

    @Test
    void runCodeMapsTransportRequestTimeoutBeforeSchedulerFires() {
        executeFailure = new SocketTimeoutException("connect timed out");
        enqueueSandboxCreation();

        TimeoutException error = assertThrows(TimeoutException.class,
                () -> createInterpreter().runCode("1 + 1", "python", null, null, 5));

        assertTrue(error.getMessage().contains("request"));
    }

    @Test
    void runCodeTimeoutStillAppliesWhileServerStreamsKeepalives() {
        enqueueSandboxCreation();
        String keepalive = "{\"type\":\"keepalive\"}\n";
        StringBuilder stream = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            stream.append(keepalive);
        }
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody(stream.toString())
                .throttleBody(keepalive.getBytes().length, 200, TimeUnit.MILLISECONDS));

        CodeInterpreter interpreter = createInterpreter();

        assertThrows(TimeoutException.class,
                () -> interpreter.runCode("slow()", "python", null, null, 1));
    }

    @Test
    void runCodeTimeoutZeroAllowsLongExecution() {
        enqueueSandboxCreation();
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBodyDelay(300, TimeUnit.MILLISECONDS)
                .setBody("{\"type\":\"stdout\",\"text\":\"done\\n\",\"timestamp\":1}\n"));

        Execution execution = createInterpreter()
                .runCode("slow()", "python", null, null, 0);

        assertEquals("done\n", execution.getLogs().getStdout().get(0));
    }

    @Test
    void nullTimeoutResolvesToDocumentedDefault() {
        assertEquals(CodeInterpreter.DEFAULT_TIMEOUT_SECONDS,
                CodeInterpreter.resolveExecutionTimeoutSeconds(null));
        assertEquals(0, CodeInterpreter.resolveExecutionTimeoutSeconds(0));
        assertEquals(42, CodeInterpreter.resolveExecutionTimeoutSeconds(42));
    }

    @Test
    void runCodeRejectsNegativeTimeoutBeforeExecuteRequest() {
        enqueueSandboxCreation();
        CodeInterpreter interpreter = createInterpreter();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> interpreter.runCode("1 + 1", "python", null, null, -1));

        assertTrue(error.getMessage().contains("timeoutSeconds"));
        assertEquals(1, server.getRequestCount(), "only the sandbox creation request is expected");
    }

    private CodeInterpreter createInterpreter() {
        return CodeInterpreter.from(Sandbox.create(CodeInterpreter.DEFAULT_TEMPLATE, config));
    }

    private ConnectionConfig withRequestTimeout(double requestTimeoutSeconds) {
        return ConnectionConfig.builder()
                .apiKey(config.getApiKey())
                .apiUrl(config.getApiUrl())
                .domain(config.getDomain())
                .debug(config.isDebug())
                .requestTimeout(requestTimeoutSeconds)
                .httpClient(config.getHttpClient())
                .build();
    }

    private void enqueueSandboxCreation() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sandboxID\":\"sbx-timeout\","
                        + "\"domain\":\"sandbox.e2b.test\","
                        + "\"templateID\":\"code-interpreter-v1\","
                        + "\"envdVersion\":\"0.1.0\","
                        + "\"envdAccessToken\":\"token\"}"));
    }
}
