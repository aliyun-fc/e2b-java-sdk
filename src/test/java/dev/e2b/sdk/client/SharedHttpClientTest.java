package dev.e2b.sdk.client;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Verifies the shared/injectable OkHttpClient behaviour: every {@link E2bApiClient} derived from the
 * SDK reuses one dispatcher thread pool and one connection pool instead of allocating a fresh pool
 * per instance (which is what happened before, once per {@code Sandbox.create} and per static call).
 */
class SharedHttpClientTest {

    private static ConnectionConfig cfg() {
        return ConnectionConfig.builder().apiKey("k").domain("example.com").build();
    }

    @Test
    void apiClientsShareConnectionPoolAndDispatcher() {
        E2bApiClient a = new E2bApiClient(cfg());
        E2bApiClient b = new E2bApiClient(cfg());

        assertSame(a.httpClient().connectionPool(), b.httpClient().connectionPool(),
                "all E2bApiClients should share a single connection pool");
        assertSame(a.httpClient().dispatcher().executorService(),
                b.httpClient().dispatcher().executorService(),
                "all E2bApiClients should share a single dispatcher thread pool");
    }

    @Test
    void injectedClientIsHonoredAndPoolShared() {
        OkHttpClient injected = new OkHttpClient();
        ConnectionConfig c = ConnectionConfig.builder().apiKey("k").httpClient(injected).build();

        E2bApiClient api = new E2bApiClient(c);

        assertSame(injected.connectionPool(), api.httpClient().connectionPool(),
                "an injected client's connection pool must be reused");
        assertSame(injected.dispatcher().executorService(),
                api.httpClient().dispatcher().executorService(),
                "an injected client's dispatcher must be reused");
    }

    @Test
    void aFreshUnrelatedClientDoesNotShareThePool() {
        E2bApiClient shared = new E2bApiClient(cfg());
        OkHttpClient fresh = new OkHttpClient();

        assertNotSame(shared.httpClient().connectionPool(), fresh.connectionPool(),
                "a separately constructed OkHttpClient is expected to own a different pool");
    }
}
