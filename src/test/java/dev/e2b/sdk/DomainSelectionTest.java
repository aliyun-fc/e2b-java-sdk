package dev.e2b.sdk;

import dev.e2b.sdk.client.ConnectionConfig;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DomainSelectionTest {
    private static final String VPC_DOMAIN = "cn-shanghai-vpc.sandbox.aliyuncs.com";
    private static final String API_URL = "https://api.cn-shanghai.sandbox.aliyuncs.com";

    @Test
    void explicitAndResponseDomainsControlActualCreateAndConnectRequestUrls() throws Exception {
        runProbe(false);
    }

    @Test
    void environmentDomainOverridesResponseButExplicitDomainWins() throws Exception {
        runProbe(true);
    }

    private void runProbe(boolean environmentDomain) throws Exception {
        // Isolate real environment values without reflective changes to System.getenv.
        Path output = Files.createTempFile("domain-selection-", ".log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                    "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    Probe.class.getName(), Boolean.toString(environmentDomain));
            builder.environment().remove("E2B_API_URL");
            if (environmentDomain) {
                builder.environment().put("E2B_DOMAIN", VPC_DOMAIN);
            } else {
                builder.environment().remove("E2B_DOMAIN");
            }
            process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Domain checks timed out");
            assertEquals(0, process.exitValue(),
                    new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(output);
        }
    }

    public static class Probe {
        public static void main(String[] args) {
            boolean environmentDomain = Boolean.parseBoolean(args[0]);
            assertEquals("e2b.app", ConnectionConfig.builder().build().getDomain());
            String fallback = environmentDomain ? VPC_DOMAIN : "e2b.app";
            for (boolean connect : new boolean[]{false, true}) {
                verify(connect, VPC_DOMAIN, "cn-shanghai.sandbox.aliyuncs.com", VPC_DOMAIN, null);
                verify(connect, VPC_DOMAIN, null, VPC_DOMAIN, null);
                verify(connect, "e2b.app", "sandbox.e2b.app", "e2b.app", null);
                verify(connect, null, "sandbox.e2b.app",
                        environmentDomain ? VPC_DOMAIN : "sandbox.e2b.app", null);
                verify(connect, null, null, fallback, null);
                verify(connect, null, "", fallback, null);
                verify(connect, "", null, fallback, null);
                verify(connect, VPC_DOMAIN, "sandbox.e2b.app", VPC_DOMAIN,
                        "https://custom-sandbox.example.com");
            }
        }

        private static void verify(boolean connect, String domain, String responseDomain,
                                   String expectedDomain, String sandboxUrl) {
            List<String> urls = new ArrayList<>();
            String apiPath = connect ? "/sandboxes/sbx-domain/connect" : "/sandboxes";
            OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
                String url = chain.request().url().toString();
                urls.add(url);
                String body;
                if (url.equals(API_URL + apiPath)) {
                    body = "{\"sandboxID\":\"sbx-domain\""
                            + (responseDomain == null ? "" : ",\"domain\":\"" + responseDomain + "\"") + "}";
                } else {
                    body = "{\"processes\":[]}";
                }
                return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
            }).build();
            ConnectionConfig config = ConnectionConfig.builder()
                    .apiKey("test-key").apiUrl(API_URL).domain(domain)
                    .sandboxUrl(sandboxUrl).httpClient(http).build();
            if (domain != null && !domain.isEmpty()) {
                assertEquals(domain, config.resolvedDomain());
            }
            Sandbox sandbox = connect ? Sandbox.connect("sbx-domain", config) : Sandbox.create(config);
            assertEquals(expectedDomain, sandbox.getSandboxDomain());
            assertEquals("3000-sbx-domain." + expectedDomain, sandbox.getHost(3000));
            assertTrue(sandbox.getCommands().list().isEmpty());
            String base = sandboxUrl != null ? sandboxUrl : "https://49983-sbx-domain." + expectedDomain;
            assertEquals(Arrays.asList(API_URL + apiPath, base + "/process.Process/List"), urls);
        }
    }
}
