package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.NewSandbox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J11: in-sandbox HTTP server + getHost port forwarding hostname.
 */
class DynamicPortE2eTest extends E2eTestBase {

    @Test
    void httpServerAndGetHost() {
        int port = config.httpPort();
        String html = "<html><body><h1>Hello from sandbox!</h1></body></html>";

        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).allowInternetAccess(true).build());
        try {
            CommandResult mkdir = sandbox.getCommands().run("mkdir -p /tmp/www");
            assertEquals(0, mkdir.getExitCode(), mkdir.getStderr());

            sandbox.getFiles().write("/tmp/www/index.html", html);

            CommandResult server = sandbox.getCommands().run(
                    "sh -c 'cd /tmp/www && nohup python3 -m http.server " + port + " > /dev/null 2>&1 & echo $!'");
            assertEquals(0, server.getExitCode(), server.getStderr());
            assertFalse(server.getStdout().trim().isEmpty());

            E2eSupport.sleepMillis(2000);

            CommandResult local = sandbox.getCommands().run(
                    "curl -fsS http://localhost:" + port + "/index.html",
                    null, null, null, 10, false);
            assertEquals(0, local.getExitCode(), local.getStderr());
            assertEquals(html, local.getStdout().trim());

            String host = sandbox.getHost(port);
            assertNotNull(host);
            assertFalse(host.isEmpty());
            assertTrue(host.contains(String.valueOf(port)), "host should include port prefix: " + host);
            assertTrue(host.contains(sandbox.getSandboxId()), "host should include sandbox id: " + host);
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
