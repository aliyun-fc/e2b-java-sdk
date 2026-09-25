package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.Template;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.model.TemplateWithBuilds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;
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
 *
 * <p>Also verifies gateway env merge: template {@code envVars} are baked in, and
 * sandbox-create {@code envVars} override same-named keys.
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

    @Test
    void buildFromImageWithEnvVars_templateThenSandboxOverride() {
        String image = System.getenv("E2E_BUILD_IMAGE");
        long timeout = parseLong(System.getenv("E2E_BUILD_TIMEOUT_SECONDS"), 600L);
        String alias = "java-e2e-env-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, String> templateEnvs = new HashMap<String, String>();
        templateEnvs.put("TPL_ONLY", "from-template");
        templateEnvs.put("SHARED", "template-value");

        TemplateWithBuilds built = Template.buildFromImage(
                alias, image, templateEnvs, config.toConnectionConfig(), timeout);
        assertNotNull(built.getTemplateId());

        Sandbox sandbox = null;
        try {
            // Template-only var should be visible without create-time envVars.
            sandbox = Sandbox.create(alias, config.toConnectionConfig());
            CommandResult tplOnly = sandbox.getCommands().run("printf '%s' \"$TPL_ONLY\"");
            assertEquals(0, tplOnly.getExitCode());
            assertEquals("from-template", tplOnly.getStdout());
            CommandResult sharedTpl = sandbox.getCommands().run("printf '%s' \"$SHARED\"");
            assertEquals(0, sharedTpl.getExitCode());
            assertEquals("template-value", sharedTpl.getStdout());
            E2eSupport.killQuietly(sandbox);
            sandbox = null;

            // Create-time envVars override same-named template vars (gateway merge).
            Map<String, String> sandboxEnvs = new HashMap<String, String>();
            sandboxEnvs.put("SHARED", "sandbox-override");
            sandboxEnvs.put("SBX_ONLY", "from-sandbox");
            sandbox = Sandbox.create(
                    alias,
                    config.toConnectionConfig(),
                    NewSandbox.builder().timeout(300).envVars(sandboxEnvs).build());
            CommandResult merged = sandbox.getCommands().run(
                    "printf '%s|%s|%s' \"$TPL_ONLY\" \"$SHARED\" \"$SBX_ONLY\"");
            assertEquals(0, merged.getExitCode());
            assertEquals("from-template|sandbox-override|from-sandbox", merged.getStdout());
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
