package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Parameters for creating a new sandbox.
 *
 * <p>Field JSON names match the sandbox-gateway create request
 * ({@code e2bCreateSandboxRequest}): camelCase with Go-style acronyms.
 */
@Data
@Builder
public class NewSandbox {

    /** Template ID (opaque internal template ID). */
    @JsonProperty("templateID")
    private String templateId;

    /** Template name / alias (e.g. "base", "python"). */
    @JsonProperty("templateName")
    private String templateName;

    /** Sandbox timeout in seconds. Default: 300. */
    private Integer timeout;

    /** Arbitrary key-value metadata attached to this sandbox. */
    private Map<String, String> metadata;

    /** Environment variables injected into the sandbox. */
    @JsonProperty("envVars")
    private Map<String, String> envVars;

    /** Whether to run the sandbox in secure mode. Default: true. */
    private Boolean secure;

    /** Whether the sandbox may reach the public internet. Default: true. */
    @JsonProperty("allowInternetAccess")
    private Boolean allowInternetAccess;

    /** Auto-pause the sandbox on timeout instead of killing it. */
    @JsonProperty("autoPause")
    private Boolean autoPause;

    /** Auto-resume a paused sandbox when accessed. */
    @JsonProperty("autoResume")
    private SandboxAutoResumeConfig autoResume;

    /** Network isolation / egress rules. */
    private SandboxNetworkOpts network;

    /** MCP server configuration. */
    private Map<String, Object> mcp;

    /** Volume mounts attached to the sandbox. */
    @JsonProperty("volumeMounts")
    private List<SandboxVolumeMount> volumeMounts;
}
