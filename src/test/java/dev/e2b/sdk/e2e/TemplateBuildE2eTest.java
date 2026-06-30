package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.Template;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.TemplateWithBuilds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J24: Template build from image, then create a sandbox from the built template.
 *
 * <p>Mirrors Python py-03 (image-based template create). Gated on {@code E2E_BUILD_IMAGE}
 * because building is slow/expensive; set it to a pullable image reference, e.g.
 * {@code E2E_BUILD_IMAGE=python:3.11-slim}.
 *
 * <p>Optional: {@code E2E_BUILD_TIMEOUT_SECONDS} (default 600).
 */
@EnabledIfEnvironmentVariable(named = "E2E_BUILD_IMAGE", matches = ".+")
class TemplateBuildE2eTest extends E2eTestBase {

    @Test
    void buildFromImageThenRunSandbox() {
        String image = System.getenv("E2E_BUILD_IMAGE");
        long timeout = parseLong(System.getenv("E2E_BUILD_TIMEOUT_SECONDS"), 600L);
        String alias = "java-e2e-" + UUID.randomUUID().toString().substring(0, 8);

        TemplateWithBuilds built = Template.buildFromImage(
                alias, image, config.toConnectionConfig(), timeout);
        assertNotNull(built.getTemplateId(), "built template must have a templateID");

        Sandbox sandbox = null;
        try {
            sandbox = Sandbox.create(alias, config.toConnectionConfig());
            CommandResult result = sandbox.getCommands().run("echo built-template-ok");
            assertEquals(0, result.getExitCode());
            assertEquals("built-template-ok\n", result.getStdout());
        } finally {
            E2eSupport.killQuietly(sandbox);
            try {
                Template.delete(built.getTemplateId(), config.toConnectionConfig());
            } catch (Exception ignored) {
            }
        }
    }

    private static long parseLong(String raw, long fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
