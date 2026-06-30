package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Detailed information about a running or paused sandbox.
 *
 * <p>JSON names match the sandbox-gateway responses ({@code e2bSandboxResponse},
 * {@code e2bSandboxDetailResponse}, {@code e2bListedSandboxResponse}): camelCase
 * with Go-style acronyms (templateID, sandboxID, memoryMB, ...).
 */
@Data
@NoArgsConstructor
public class SandboxInfo {

    /** Unique sandbox identifier. */
    @JsonProperty("sandboxID")
    private String sandboxId;

    /** Domain where the sandbox is hosted. */
    @JsonProperty("domain")
    @JsonAlias({"sandbox_domain", "sandboxDomain"})
    private String sandboxDomain;

    /** Template ID used to create this sandbox. */
    @JsonProperty("templateID")
    private String templateId;

    /** Template alias (user-facing name like "base"). */
    private String alias;

    /** Deprecated client identifier. */
    @JsonProperty("clientID")
    private String clientId;

    /** User-supplied metadata key-value pairs. */
    private Map<String, String> metadata;

    /** When this sandbox started. */
    @JsonProperty("startedAt")
    private Instant startedAt;

    /** When this sandbox is scheduled to end. */
    @JsonProperty("endAt")
    private Instant endAt;

    /** Current state (running / paused). */
    private SandboxState state;

    /** Number of virtual CPUs. */
    @JsonProperty("cpuCount")
    private int cpuCount;

    /** RAM in megabytes. */
    @JsonProperty("memoryMB")
    private int memoryMb;

    /** Disk size in megabytes. */
    @JsonProperty("diskSizeMB")
    private int diskSizeMb;

    /** envd daemon version running inside the sandbox. */
    @JsonProperty("envdVersion")
    private String envdVersion;

    /** Whether this sandbox can reach the public internet. */
    @JsonProperty("allowInternetAccess")
    private Boolean allowInternetAccess;

    /** Network configuration snapshot. */
    private SandboxNetworkInfo network;

    /** Lifecycle configuration for this sandbox. */
    private SandboxInfoLifecycle lifecycle;

    /** Attached volume mounts. */
    @JsonProperty("volumeMounts")
    private List<SandboxVolumeMount> volumeMounts;

    /** Access token for envd API calls inside the sandbox. */
    @JsonProperty("envdAccessToken")
    private String envdAccessToken;
}
