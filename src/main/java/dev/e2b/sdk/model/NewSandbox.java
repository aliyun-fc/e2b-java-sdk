package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Parameters for creating a new sandbox.
 * All fields are optional unless noted; snake_case names map to API JSON fields.
 */
@Data
@Builder
public class NewSandbox {

    /** Template ID or name (e.g. "base", "python", your custom template ID). */
    @JsonProperty("template_id")
    private String templateId;

    /** Sandbox timeout in seconds. Default: 300. */
    private Integer timeout;

    /** Arbitrary key-value metadata attached to this sandbox. */
    private Map<String, String> metadata;

    /** Environment variables injected into the sandbox. */
    @JsonProperty("env_vars")
    private Map<String, String> envVars;

    /** Whether to run the sandbox in secure mode. Default: true. */
    private Boolean secure;

    /** Whether the sandbox may reach the public internet. Default: true. */
    @JsonProperty("allow_internet_access")
    private Boolean allowInternetAccess;

    /** Auto-pause the sandbox on timeout instead of killing it. */
    @JsonProperty("auto_pause")
    private Boolean autoPause;

    /** Auto-resume a paused sandbox when accessed. */
    @JsonProperty("auto_resume")
    private Boolean autoResume;

    /** Network isolation / egress rules. */
    private SandboxNetworkOpts network;

    /** Volume mounts: {mountPath -> volumeId}. */
    @JsonProperty("volume_mounts")
    private Map<String, String> volumeMounts;
}
