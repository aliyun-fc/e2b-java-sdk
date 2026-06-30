package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.e2b.sdk.exception.SandboxException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed helpers for FC sandbox storage / network mounts that are configured through sandbox
 * {@code metadata} (JuiceFS, OSS, NAS, VPC). Use the {@link #builder()} to assemble the metadata map
 * and pass it to {@code NewSandbox.builder().metadata(...)}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Map<String, String> metadata = StorageMounts.builder()
 *     .vpc(VpcConfig.builder()
 *         .vpcId(vpcId).securityGroupId(sg)
 *         .vSwitchIds(List.of(vsw)).build())
 *     .juicefs(JuiceFsConfig.builder()
 *         .envs(Map.of("BASE_URL", baseUrl))
 *         .mountPoints(List.of(JuiceFsMountPoint.builder()
 *             .volumeName(vol).mountDir("/jfs").remoteDir("/data").token(token)
 *             .args(List.of("--writeback")).build()))
 *         .build())
 *     .build();
 *
 * Sandbox sbx = Sandbox.create(template, config,
 *     NewSandbox.builder().timeout(300).metadata(metadata).build());
 * }</pre>
 */
public final class StorageMounts {

    public static final String VPC_METADATA_KEY = "fc.sandbox.network.vpc";
    public static final String JUICEFS_METADATA_KEY = "fc.sandbox.storage.juicefs";
    public static final String OSS_METADATA_KEY = "fc.sandbox.storage.oss";
    public static final String NAS_METADATA_KEY = "fc.sandbox.storage.nas";
    public static final String ROLE_ARN_METADATA_KEY = "fc.sandbox.auth.role";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private StorageMounts() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Serialize a config object to its metadata JSON value. */
    public static String toJson(Object config) {
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            throw new SandboxException("Failed to serialize storage mount config", e);
        }
    }

    /** Builder that accumulates storage/network configs into a {@code metadata} map. */
    public static final class Builder {

        private final Map<String, String> metadata = new LinkedHashMap<String, String>();

        public Builder vpc(VpcConfig vpc) {
            if (vpc != null) {
                metadata.put(VPC_METADATA_KEY, toJson(vpc));
            }
            return this;
        }

        public Builder juicefs(JuiceFsConfig juicefs) {
            if (juicefs != null) {
                metadata.put(JUICEFS_METADATA_KEY, toJson(juicefs));
            }
            return this;
        }

        public Builder oss(OssConfig oss) {
            if (oss != null) {
                metadata.put(OSS_METADATA_KEY, toJson(oss));
            }
            return this;
        }

        public Builder nas(NasConfig nas) {
            if (nas != null) {
                metadata.put(NAS_METADATA_KEY, toJson(nas));
            }
            return this;
        }

        /** RAM role ARN used for OSS access (metadata key {@code fc.sandbox.auth.role}). */
        public Builder roleArn(String roleArn) {
            if (roleArn != null && !roleArn.isEmpty()) {
                metadata.put(ROLE_ARN_METADATA_KEY, roleArn);
            }
            return this;
        }

        /** Add an arbitrary extra metadata entry. */
        public Builder put(String key, String value) {
            if (key != null && value != null) {
                metadata.put(key, value);
            }
            return this;
        }

        /** Merge any pre-existing metadata entries. */
        public Builder putAll(Map<String, String> extra) {
            if (extra != null) {
                metadata.putAll(extra);
            }
            return this;
        }

        public Map<String, String> build() {
            return new LinkedHashMap<String, String>(metadata);
        }
    }
}
