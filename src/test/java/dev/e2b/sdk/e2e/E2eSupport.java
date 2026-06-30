package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.model.NewSandbox;

import java.util.Collections;

final class E2eSupport {

    private E2eSupport() {
    }

    static Sandbox createSandbox(E2eConfig config) {
        return Sandbox.create(config.getTemplate(), config.toConnectionConfig());
    }

    static Sandbox createSandbox(E2eConfig config, NewSandbox opts) {
        return Sandbox.create(config.getTemplate(), config.toConnectionConfig(), opts);
    }

    static void killQuietly(Sandbox sandbox) {
        if (sandbox == null) {
            return;
        }
        try {
            sandbox.kill();
        } catch (Exception ignored) {
        }
    }

    static NewSandbox defaultOpts(int timeoutSeconds) {
        return NewSandbox.builder()
                .timeout(timeoutSeconds)
                .build();
    }

    static NewSandbox optsWithEnvs(java.util.Map<String, String> envVars) {
        return NewSandbox.builder()
                .timeout(300)
                .envVars(envVars)
                .build();
    }

    static NewSandbox optsWithMetadata(java.util.Map<String, String> metadata) {
        return NewSandbox.builder()
                .timeout(300)
                .metadata(metadata)
                .build();
    }

    static ConnectionConfig connection(E2eConfig config) {
        return config.toConnectionConfig();
    }

    static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    static int parseBackgroundPid(String stdout) {
        String trimmed = stdout.trim();
        int newline = trimmed.indexOf('\n');
        String line = newline >= 0 ? trimmed.substring(0, newline) : trimmed;
        return Integer.parseInt(line.trim());
    }

    static java.util.Map<String, String> singleMetadata(String key, String value) {
        java.util.Map<String, String> metadata = new java.util.HashMap<String, String>();
        metadata.put(key, value);
        return metadata;
    }

    static java.util.Map<String, String> emptyMetadata() {
        return Collections.emptyMap();
    }
}
