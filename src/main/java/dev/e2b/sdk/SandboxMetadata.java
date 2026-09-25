package dev.e2b.sdk;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.model.LoggingConfig;
import dev.e2b.sdk.model.ManagedIdentityFailStrategy;
import dev.e2b.sdk.model.TracingConfig;
import dev.e2b.sdk.storage.AgenticBucketConfig;
import dev.e2b.sdk.storage.AgenticFsConfig;
import dev.e2b.sdk.storage.JuiceFsConfig;
import dev.e2b.sdk.storage.NasConfig;
import dev.e2b.sdk.storage.OssConfig;
import dev.e2b.sdk.storage.PolarFsConfig;
import dev.e2b.sdk.storage.PortForwardVpcConfig;
import dev.e2b.sdk.storage.VpcConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed helpers for assembling sandbox storage, network, authentication, and custom metadata.
 */
public final class SandboxMetadata {

    public static final String SANDBOX_ID_METADATA_KEY = "fc.sandbox.id";
    public static final String MANAGED_IDENTITY_NAME_METADATA_KEY = "fc.sandbox.identity.name";
    public static final String MANAGED_IDENTITY_POLICY_METADATA_KEY = "fc.sandbox.identity.role.policy";
    public static final String MANAGED_IDENTITY_DOMAINS_METADATA_KEY = "fc.sandbox.identity.role.domains";
    public static final String MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY = "fc.sandbox.identity.role.failStrategy";
    public static final String VPC_METADATA_KEY = "fc.sandbox.network.vpc";
    public static final String PORT_FORWARD_VPC_METADATA_KEY = "fc.sandbox.network.portforwardvpc";
    public static final String JUICEFS_METADATA_KEY = "fc.sandbox.storage.juicefs";
    public static final String OSS_METADATA_KEY = "fc.sandbox.storage.oss";
    public static final String AGENTIC_BUCKET_METADATA_KEY = "fc.sandbox.storage.agenticbucket";
    public static final String NAS_METADATA_KEY = "fc.sandbox.storage.nas";
    public static final String POLARFS_METADATA_KEY = "fc.sandbox.storage.polarfs";
    public static final String AGENTICFS_METADATA_KEY = "fc.sandbox.storage.agenticfs";
    public static final String LOGGING_METADATA_KEY = "fc.sandbox.observability.logging";
    public static final String TRACING_METADATA_KEY = "fc.sandbox.observability.tracing";
    public static final String ROLE_ARN_METADATA_KEY = "fc.sandbox.auth.role";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private SandboxMetadata() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Serialize an object to its metadata JSON value. */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new SandboxException("Failed to serialize sandbox metadata", e);
        }
    }

    /** Builder that accumulates sandbox metadata entries. */
    public static final class Builder {

        private final Map<String, String> metadata = new LinkedHashMap<String, String>();

        public Builder sandboxId(String sandboxId) {
            if (sandboxId != null) {
                metadata.put(SANDBOX_ID_METADATA_KEY, sandboxId);
            }
            return this;
        }

        public Builder managedIdentityName(String name) {
            if (name != null) {
                metadata.put(MANAGED_IDENTITY_NAME_METADATA_KEY, name);
            }
            return this;
        }

        public Builder managedIdentityPolicy(Map<String, ?> policy) {
            if (policy != null) {
                metadata.put(MANAGED_IDENTITY_POLICY_METADATA_KEY, toJson(policy));
            }
            return this;
        }

        public Builder managedIdentityDomains(List<String> domains) {
            if (domains != null) {
                metadata.put(MANAGED_IDENTITY_DOMAINS_METADATA_KEY, toJson(domains));
            }
            return this;
        }

        public Builder managedIdentityFailStrategy(ManagedIdentityFailStrategy strategy) {
            if (strategy != null) {
                metadata.put(MANAGED_IDENTITY_FAIL_STRATEGY_METADATA_KEY, strategy.getValue());
            }
            return this;
        }

        public Builder vpc(VpcConfig vpc) {
            if (vpc != null) {
                metadata.put(VPC_METADATA_KEY, toJson(vpc));
            }
            return this;
        }

        public Builder portForwardVpc(PortForwardVpcConfig portForwardVpc) {
            if (portForwardVpc != null) {
                metadata.put(PORT_FORWARD_VPC_METADATA_KEY, toJson(portForwardVpc));
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

        public Builder agenticBucket(AgenticBucketConfig agenticBucket) {
            if (agenticBucket != null) {
                metadata.put(AGENTIC_BUCKET_METADATA_KEY, toJson(agenticBucket));
            }
            return this;
        }

        public Builder nas(NasConfig nas) {
            if (nas != null) {
                metadata.put(NAS_METADATA_KEY, toJson(nas));
            }
            return this;
        }

        public Builder polarFs(PolarFsConfig polarFs) {
            if (polarFs != null) {
                metadata.put(POLARFS_METADATA_KEY, toJson(polarFs));
            }
            return this;
        }

        public Builder agenticFs(AgenticFsConfig agenticFs) {
            if (agenticFs != null) {
                metadata.put(AGENTICFS_METADATA_KEY, toJson(agenticFs));
            }
            return this;
        }

        public Builder logging(LoggingConfig logging) {
            if (logging != null) {
                metadata.put(LOGGING_METADATA_KEY, toJson(logging));
            }
            return this;
        }

        public Builder tracing(TracingConfig tracing) {
            if (tracing != null) {
                metadata.put(TRACING_METADATA_KEY, toJson(tracing));
            }
            return this;
        }

        /** RAM role ARN used for OSS and AgenticBucket access. */
        public Builder roleArn(String roleArn) {
            if (roleArn != null && !roleArn.isEmpty()) {
                metadata.put(ROLE_ARN_METADATA_KEY, roleArn);
            }
            return this;
        }

        /** Add an arbitrary raw metadata entry. */
        public Builder put(String key, String value) {
            if (key != null && value != null) {
                metadata.put(key, value);
            }
            return this;
        }

        /** Serialize and add an arbitrary metadata entry. */
        public Builder putJson(String key, Object value) {
            if (key != null && value != null) {
                metadata.put(key, toJson(value));
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
