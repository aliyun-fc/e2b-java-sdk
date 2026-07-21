package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.storage.OssConfig;
import dev.e2b.sdk.storage.OssMountPoint;
import dev.e2b.sdk.storage.StorageMounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J21: OSS mount via metadata (optional, env-specific).
 *
 * <p>Requires: {@code E2E_OSS_BUCKET}, {@code E2E_OSS_MOUNT_DIR}, {@code E2E_OSS_BUCKET_PATH},
 * {@code E2E_OSS_ENDPOINT}, {@code E2E_ROLE_ARN}
 */
@EnabledIfEnvironmentVariable(named = "E2E_OSS_BUCKET", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_ROLE_ARN", matches = ".+")
class OssMountE2eTest extends E2eTestBase {

    @Test
    void ossMountReadWrite() {
        String mountDir = System.getenv("E2E_OSS_MOUNT_DIR");
        String bucket = System.getenv("E2E_OSS_BUCKET");
        String bucketPath = System.getenv("E2E_OSS_BUCKET_PATH");
        String endpoint = System.getenv("E2E_OSS_ENDPOINT");
        String roleArn = System.getenv("E2E_ROLE_ARN");

        Map<String, String> metadata = StorageMounts.builder()
                .oss(OssConfig.builder()
                        .mountPoints(Collections.singletonList(OssMountPoint.builder()
                                .bucketName(bucket)
                                .mountDir(mountDir)
                                .bucketPath(bucketPath)
                                .endpoint(endpoint)
                                .readOnly(false)
                                .build()))
                        .build())
                .roleArn(roleArn)
                .build();

        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadata).build());
        try {
            String marker = "java-e2e-" + UUID.randomUUID();
            String filePath = mountDir + "/e2e-" + marker + ".txt";
            sandbox.getFiles().write(filePath, marker);
            assertEquals(marker, sandbox.getFiles().read(filePath).getText().trim());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
