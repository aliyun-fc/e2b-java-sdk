package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class TemplateLegacy {
    @JsonProperty("templateID")
    private String templateId;

    @JsonProperty("buildID")
    private String buildId;

    @JsonProperty("cpuCount")
    private int cpuCount;

    @JsonProperty("memoryMB")
    private int memoryMb;

    @JsonProperty("diskSizeMB")
    private int diskSizeMb;

    private boolean isPublic;

    @JsonProperty("public")
    public void setPublic(boolean value) {
        this.isPublic = value;
    }

    public boolean isPublic() {
        return isPublic;
    }

    private List<String> aliases;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("envdVersion")
    private String envdVersion;

    @JsonProperty("buildStatus")
    private TemplateBuildStatus buildStatus;
}
