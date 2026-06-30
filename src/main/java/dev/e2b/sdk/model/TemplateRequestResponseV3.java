package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TemplateRequestResponseV3 {
    @JsonProperty("templateID")
    private String templateId;

    @JsonProperty("buildID")
    private String buildId;

    private boolean isPublic;

    @JsonProperty("public")
    public void setPublic(boolean value) {
        this.isPublic = value;
    }

    public boolean isPublic() {
        return isPublic;
    }

    private List<String> names;
    private List<String> tags;
    private List<String> aliases;
}
