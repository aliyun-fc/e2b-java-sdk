package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class TemplateBuild {
    @JsonProperty("buildID")
    private String buildId;

    private TemplateBuildStatus status;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("finishedAt")
    private Instant finishedAt;

    @JsonProperty("cpuCount")
    private int cpuCount;

    @JsonProperty("memoryMB")
    private int memoryMb;

    @JsonProperty("diskSizeMB")
    private int diskSizeMb;

    @JsonProperty("envdVersion")
    private String envdVersion;
}
