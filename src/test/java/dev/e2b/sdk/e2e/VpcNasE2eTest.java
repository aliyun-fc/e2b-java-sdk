package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.NewSandbox;
import dev.e2b.sdk.storage.NasConfig;
import dev.e2b.sdk.storage.NasMountPoint;
import dev.e2b.sdk.storage.StorageMounts;
import dev.e2b.sdk.storage.VpcConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J20: VPC metadata and NAS reachability (optional, env-specific).
 *
 * <p>Mirrors Python py-08: a sandbox bound to the VPC can reach the NAS port, while a
 * sandbox WITHOUT VPC configuration cannot — proving VPC isolation is actually applied.
 *
 * <p>Requires: {@code E2E_VPC_ID}, {@code E2E_SECURITY_GROUP_ID}, {@code E2E_VSWITCH_ID},
 * {@code E2E_NAS_SERVER_ADDR}
 */
@EnabledIfEnvironmentVariable(named = "E2E_VPC_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "E2E_NAS_SERVER_ADDR", matches = ".+")
class VpcNasE2eTest extends E2eTestBase {

    private String nasHost() {
        return System.getenv("E2E_NAS_SERVER_ADDR").split(":", 2)[0].trim();
    }

    private VpcConfig vpcConfig() {
        return VpcConfig.builder()
                .vpcId(System.getenv("E2E_VPC_ID"))
                .securityGroupId(System.getenv("E2E_SECURITY_GROUP_ID"))
                .vSwitchIds(Collections.singletonList(System.getenv("E2E_VSWITCH_ID")))
                .build();
    }

    private String reachCommand(String host) {
        return "python3 -c \"import socket; s=socket.create_connection(('"
                + host + "', 2049), timeout=5); s.close(); print('ok')\"";
    }

    @Test
    void vpcSandboxCanReachNasPort() {
        if ("shanghai-spe".equals(config.getEnvName())) {
            return;
        }

        Map<String, String> metadata = StorageMounts.builder().vpc(vpcConfig()).build();

        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadata).build());
        try {
            assertEquals(0,
                    sandbox.getCommands().run(reachCommand(nasHost()), null, null, null, 15, false).getExitCode(),
                    "VPC-bound sandbox should reach the NAS port");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    /**
     * Contrast case: without VPC metadata the sandbox must NOT be able to reach the
     * private NAS endpoint. This guards against the VPC config being silently ignored.
     */
    @Test
    void nonVpcSandboxCannotReachNasPort() {
        if ("shanghai-spe".equals(config.getEnvName())) {
            return;
        }

        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).build());
        try {
            int exitCode = sandbox.getCommands()
                    .run(reachCommand(nasHost()), null, null, null, 15, false)
                    .getExitCode();
            assertNotEquals(0, exitCode,
                    "Non-VPC sandbox must NOT reach the private NAS port");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    /**
     * NAS mount read/write (mirrors Python py-12): mount NAS via metadata, then create a
     * directory and round-trip a file. Optional: {@code E2E_NAS_MOUNT_DIR} (default /mnt/nas).
     */
    @Test
    void nasMountReadWrite() {
        String mountDir = System.getenv("E2E_NAS_MOUNT_DIR");
        if (mountDir == null || mountDir.isEmpty()) {
            mountDir = "/mnt/nas";
        }
        String nasServerAddr = System.getenv("E2E_NAS_SERVER_ADDR").trim();

        Map<String, String> metadata = StorageMounts.builder()
                .vpc(vpcConfig())
                .nas(NasConfig.builder()
                        .mountPoints(Collections.singletonList(NasMountPoint.builder()
                                .serverAddr(nasServerAddr)
                                .mountDir(mountDir)
                                .build()))
                        .build())
                .build();

        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).metadata(metadata).build());
        try {
            String testDir = mountDir + "/java-e2e-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            assertEquals(0, sandbox.getCommands().run("sudo mkdir -p " + testDir).getExitCode());
            assertEquals(0, sandbox.getCommands().run("sudo chown user:user " + testDir).getExitCode());

            String testPath = testDir + "/hello.txt";
            sandbox.getFiles().write(testPath, "nas verify\n");
            assertEquals("nas verify", sandbox.getFiles().read(testPath).getText().trim());
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
