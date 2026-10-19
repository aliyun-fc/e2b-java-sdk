package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.Template;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.TemplateWithBuilds;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J09: env vars injected at sandbox creation.
 *
 * <p>Mirrors Python e2b-e2e {@code test_06_env}: when {@code E2E_BASE_TEMPLATE_IMAGE} is set,
 * build a temporary template from that image first (so create-time env can be verified against
 * a known runtime image), otherwise fall back to {@code E2B_CLI_TEMPLATE}.
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

        TemplateWithBuilds built = null;
        String template = config.getTemplate();
        String image = config.baseTemplateImage();
        if (image != null) {
            String alias = "java-e2e-env-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            long timeout = parseLong(System.getenv("E2E_BUILD_TIMEOUT_SECONDS"), 600L);
            built = Template.buildFromImage(alias, image, config.toConnectionConfig(), timeout);
            assertNotNull(built.getTemplateId());
            template = alias;
        }

        Sandbox sandbox = null;
        try {
            sandbox = Sandbox.create(template, config.toConnectionConfig(), opts);
            CommandResult result = sandbox.getCommands().run(
                    "printf '%s|%s' \"$E2B_SMOKE_MODE\" \"$E2B_SMOKE_MESSAGE\"");
            assertEquals(0, result.getExitCode());
            assertEquals("custom-env|hello-from-java-sdk", result.getStdout());
        } finally {
            E2eSupport.killQuietly(sandbox);
            if (built != null && built.getTemplateId() != null) {
                try {
                    Template.delete(built.getTemplateId(), config.toConnectionConfig());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long parseLong(String raw, long defaultValue) {
        if (raw == null || raw.isEmpty()) {
            return defaultValue;
        }
        return Long.parseLong(raw);
    }
}
