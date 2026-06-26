package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detailed information about a running or paused sandbox.
 * Field names are camelCase in Java; serialized as snake_case to/from the API.
 */
@Data
@NoArgsConstructor
public class SandboxInfo {

    /** Unique sandbox identifier. */
    @JsonProperty("sandbox_id")
    private String sandboxId;

    /** Domain where the sandbox is hosted. */
    @JsonProperty("sandbox_domain")
    private String sandboxDomain;

    /** Template ID used to create this sandbox. */
    @JsonProperty("template_id")
    private String templateId;

    /** Optional display name. */
    private String name;

    /** User-supplied metadata key-value pairs. */
    private Map<String, String> metadata;

    /** When this sandbox started. */
    @JsonProperty("started_at")
    private Instant startedAt;

    /** When this sandbox is scheduled to end. */
    @JsonProperty("end_at")
    private Instant endAt;

    /** Current state (running / paused). */
    private SandboxState state;

    /** Number of virtual CPUs. */
    @JsonProperty("cpu_count")
    private int cpuCount;

    /** RAM in megabytes. */
    @JsonProperty("memory_mb")
    private int memoryMb;

    /** envd daemon version running inside the sandbox. */
    @JsonProperty("envd_version")
    private String envdVersion;

    /** Whether this sandbox can reach the public internet. */
    @JsonProperty("allow_internet_access")
    private Boolean allowInternetAccess;

    /** Network configuration snapshot. */
    private SandboxNetworkInfo network;

    /** Lifecycle configuration for this sandbox. */
    private SandboxInfoLifecycle lifecycle;

    /** Attached volume mounts. */
    @JsonProperty("volume_mounts")
    private List<Map<String, String>> volumeMounts;

    /** Access token for envd API calls inside the sandbox. */
    @JsonProperty("envd_access_token")
    private String envdAccessToken;
}
