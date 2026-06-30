package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
public class TemplateWithBuilds {
    @JsonProperty("templateID")
    private String templateId;

    private boolean isPublic;

    @JsonProperty("public")
    public void setPublic(boolean value) {
        this.isPublic = value;
    }

    public boolean isPublic() {
        return isPublic;
    }

    private List<String> aliases;
    private List<String> names;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;

    @JsonProperty("lastSpawnedAt")
    private Instant lastSpawnedAt;

    @JsonProperty("spawnCount")
    private long spawnCount;

    private List<TemplateBuild> builds;

    @JsonProperty("buildStatus")
    private TemplateBuildStatus buildStatus;
}
