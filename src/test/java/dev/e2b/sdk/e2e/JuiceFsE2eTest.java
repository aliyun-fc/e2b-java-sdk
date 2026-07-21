package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.storage.JuiceFsConfig;
import dev.e2b.sdk.storage.JuiceFsMountPoint;
import dev.e2b.sdk.storage.StorageMounts;
import dev.e2b.sdk.storage.VpcConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J10: JuiceFS mount via the typed {@link StorageMounts} helper (mirrors Python
 * {@code test_10_juicefs_vpc.py}). JuiceFS requires a VPC binding.
 *
 * <p>Requires: {@code E2E_JUICEFS_BASE_URL}, {@code E2E_JUICEFS_VOLUME_NAME},
 * {@code E2E_JUICEFS_MOUNT_DIR}, {@code E2E_JUICEFS_REMOTE_DIR}, {@code E2E_JUICEFS_TOKEN},
 * {@code E2E_JUICEFS_VPC_ID}, {@code E2E_JUICEFS_SECURITY_GROUP_ID}, {@code E2E_JUICEFS_VSWITCH_ID}.
 */
@EnabledIfEnvironmentVariable(named = "E2E_JUICEFS_BASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_JUICEFS_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_JUICEFS_VPC_ID", matches = ".+")
class JuiceFsE2eTest extends E2eTestBase {

    @Test
    void juiceFsMountReadWrite() {
        String mountDir = System.getenv("E2E_JUICEFS_MOUNT_DIR");

        Map<String, String> jfsEnvs = new LinkedHashMap<String, String>();
        jfsEnvs.put("BASE_URL", System.getenv("E2E_JUICEFS_BASE_URL"));
        jfsEnvs.put("GOGC", "50");
        jfsEnvs.put("JFS_MOUNT_PATH", "/tmp");

        JuiceFsConfig juicefs = JuiceFsConfig.builder()
                .envs(jfsEnvs)
                .mountPoints(Collections.singletonList(JuiceFsMountPoint.builder()
                        .volumeName(System.getenv("E2E_JUICEFS_VOLUME_NAME"))
                        .mountDir(mountDir)
                        .remoteDir(System.getenv("E2E_JUICEFS_REMOTE_DIR"))
                        .token(System.getenv("E2E_JUICEFS_TOKEN"))
                        .args(Arrays.asList(
                                "--no-bgjob=true",
                                "--no-agent",
                                "--writeback",
                                "--no-sharing",
                                "--conf-dir /tmp/jfsConf",
                                "--log /tmp/juicefs.sys.log",
                                "--cache-dir /tmp/jfsCache"))
                        .build()))
                .build();

        VpcConfig vpc = VpcConfig.builder()
                .vpcId(System.getenv("E2E_JUICEFS_VPC_ID"))
                .securityGroupId(System.getenv("E2E_JUICEFS_SECURITY_GROUP_ID"))
                .vSwitchIds(Collections.singletonList(System.getenv("E2E_JUICEFS_VSWITCH_ID")))
                .build();

        Map<String, String> metadata = StorageMounts.builder()
                .vpc(vpc)
                .juicefs(juicefs)
                .build();

        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadata).build());
        try {
            String marker = "juicefs verify " + UUID.randomUUID();
            String testPath = mountDir + "/e2b-test-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
            sandbox.getFiles().write(testPath, marker + "\n");
            assertEquals(marker, sandbox.getFiles().read(testPath).getText().trim());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
