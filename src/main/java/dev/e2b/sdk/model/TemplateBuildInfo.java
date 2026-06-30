package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TemplateBuildInfo {
    @JsonProperty("templateID")
    private String templateId;

    @JsonProperty("buildID")
    private String buildId;

    private TemplateBuildStatus status;

    private List<String> logs;

    @JsonProperty("logEntries")
    private List<BuildLogEntry> logEntries;
}
