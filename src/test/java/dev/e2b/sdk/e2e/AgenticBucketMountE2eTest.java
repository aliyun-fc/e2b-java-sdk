package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.storage.AgenticBucketConfig;
import dev.e2b.sdk.storage.AgenticBucketMountPoint;
import dev.e2b.sdk.storage.StorageMounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** AgenticBucket BucketSpace mount via metadata (optional, environment-specific). */
@EnabledIfEnvironmentVariable(named = "E2E_AGENTIC_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_AGENTIC_BUCKET_SPACE", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_AGENTIC_BUCKET_ENDPOINT", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_ROLE_ARN", matches = ".+")
class AgenticBucketMountE2eTest extends E2eTestBase {

    @Test
    void agenticBucketMountReadWrite() {
        String mountDir = env("E2E_AGENTIC_BUCKET_MOUNT_DIR", "/mnt/agentic-bucket");
        Map<String, String> metadata = StorageMounts.builder()
                .agenticBucket(AgenticBucketConfig.builder()
                        .mountPoints(Collections.singletonList(AgenticBucketMountPoint.builder()
                                .agenticBucket(System.getenv("E2E_AGENTIC_BUCKET"))
                                .bucketName(System.getenv("E2E_AGENTIC_BUCKET_SPACE"))
                                .bucketPath(env("E2E_AGENTIC_BUCKET_PATH", "/"))
                                .endpoint(System.getenv("E2E_AGENTIC_BUCKET_ENDPOINT"))
                                .mountDir(mountDir).readOnly(false).build()))
                        .build())
                .roleArn(System.getenv("E2E_ROLE_ARN"))
                .build();

        try (Sandbox sandbox = Sandbox.create(config.getTemplate(), config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadata).build())) {
            String marker = "java-agentic-" + UUID.randomUUID();
            String path = mountDir + "/" + marker + ".txt";
            sandbox.getFiles().write(path, marker);
            assertEquals(marker, sandbox.getFiles().read(path).trim());
            sandbox.getFiles().remove(path);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
