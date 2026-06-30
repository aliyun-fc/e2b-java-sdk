package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.NewSandbox;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J09: env vars injected at sandbox creation.
 */
class EnvVarsE2eTest extends E2eTestBase {

    @Test
    void createEnvVarsVisibleInsideSandbox() {
        Map<String, String> envVars = new HashMap<String, String>();
        envVars.put("E2B_SMOKE_MODE", "custom-env");
        envVars.put("E2B_SMOKE_MESSAGE", "hello-from-java-sdk");

        NewSandbox opts = NewSandbox.builder()
                .timeout(300)
                .envVars(envVars)
                .build();

        Sandbox sandbox = Sandbox.create(config.getTemplate(), config.toConnectionConfig(), opts);
        try {
            CommandResult result = sandbox.getCommands().run(
                    "printf '%s|%s' \"$E2B_SMOKE_MODE\" \"$E2B_SMOKE_MESSAGE\"");
            assertEquals(0, result.getExitCode());
            assertEquals("custom-env|hello-from-java-sdk", result.getStdout());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
