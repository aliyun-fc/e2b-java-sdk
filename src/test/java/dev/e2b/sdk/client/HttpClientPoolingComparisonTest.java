package dev.e2b.sdk.client;

import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs entirely locally (against a {@link MockWebServer}, no live E2B) to make the effect of the
 * shared-client fix tangible: it fires the same number of requests two ways —
 *
 * <ul>
 *   <li><b>old</b>: a brand-new {@code OkHttpClient} per call (what {@code new E2bApiClient(config)}
 *       used to do, once per {@code Sandbox.create} and per static API call);</li>
 *   <li><b>new</b>: a single shared {@code OkHttpClient} (what the SDK does now).</li>
 * </ul>
 *
 * and prints new-TCP-connections / elapsed / thread growth / distinct connection pools so the
 * difference is directly visible.
 */
class HttpClientPoolingComparisonTest {

    private static final int N = 300;

    static final class Result {
        long elapsedMs;
        int threadDelta;
        int distinctPools;
        List<Integer> sequenceNumbers;
    }

    @Test
    void sharedClientReusesConnectionsWhilePerCallClientDoesNot() throws Exception {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setBody("ok");
            }
        });
        server.start();
        try {
            HttpUrl url = server.url("/ping");

            Result oldR = run(server, url, /* freshEachCall */ true);
            Result newR = run(server, url, /* freshEachCall */ false);

            int oldConns = countNewConnections(oldR.sequenceNumbers);
            int newConns = countNewConnections(newR.sequenceNumbers);

            System.out.println();
            System.out.println("================ OkHttpClient 复用对比  (N=" + N + " 次请求) ================");
            System.out.printf("%-26s | %-20s | %-20s%n", "指标", "每次新建 client (旧)", "共享 client (新)");
            System.out.println("---------------------------+----------------------+---------------------");
            System.out.printf("%-26s | %-20d | %-20d%n", "新建 TCP 连接数", oldConns, newConns);
            System.out.printf("%-26s | %-20d | %-20d%n", "耗时 (ms)", oldR.elapsedMs, newR.elapsedMs);
            System.out.printf("%-26s | %-20d | %-20d%n", "线程峰值增量", oldR.threadDelta, newR.threadDelta);
            System.out.printf("%-26s | %-20d | %-20d%n", "distinct ConnectionPool", oldR.distinctPools, newR.distinctPools);
            System.out.println("=========================================================================");
            System.out.println();

            // The core guarantee: the shared client reuses one keep-alive connection for all N calls,
            // whereas a fresh client per call opens a brand-new TCP connection every time.
            assertEquals(1, newConns, "shared client should reuse a single keep-alive connection");
            assertEquals(1, newR.distinctPools, "shared client should use exactly one connection pool");
            assertTrue(oldConns >= N, "per-call client should open a new connection for every request");
            assertTrue(oldR.distinctPools >= N, "per-call client should allocate a pool per client");
        } finally {
            server.shutdown();
        }
    }

    private Result run(MockWebServer server, HttpUrl url, boolean freshEachCall) throws Exception {
        Set<ConnectionPool> pools = Collections.newSetFromMap(new IdentityHashMap<ConnectionPool, Boolean>());
        OkHttpClient shared = freshEachCall ? null : new OkHttpClient();
        if (shared != null) {
            pools.add(shared.connectionPool());
        }

        int threadsBefore = ManagementFactory.getThreadMXBean().getThreadCount();
        int peakThreads = threadsBefore;

        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) {
            OkHttpClient client = freshEachCall ? new OkHttpClient() : shared;
            if (freshEachCall) {
                pools.add(client.connectionPool());
            }
            Request req = new Request.Builder().url(url).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.body() != null) {
                    resp.body().string();
                }
            }
            int now = ManagementFactory.getThreadMXBean().getThreadCount();
            if (now > peakThreads) {
                peakThreads = now;
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        List<Integer> seqs = new ArrayList<Integer>(N);
        for (int i = 0; i < N; i++) {
            RecordedRequest rr = server.takeRequest(5, TimeUnit.SECONDS);
            if (rr != null) {
                seqs.add((int) rr.getSequenceNumber());
            }
        }

        Result r = new Result();
        r.elapsedMs = elapsedMs;
        r.threadDelta = peakThreads - threadsBefore;
        r.distinctPools = pools.size();
        r.sequenceNumbers = seqs;
        return r;
    }

    /**
     * A {@code RecordedRequest} whose sequence number is 0 is the first request on a freshly opened
     * connection, so counting zeros counts the number of distinct TCP connections used.
     */
    private static int countNewConnections(List<Integer> sequenceNumbers) {
        int n = 0;
        for (int seq : sequenceNumbers) {
            if (seq == 0) {
                n++;
            }
        }
        return n;
    }
}
