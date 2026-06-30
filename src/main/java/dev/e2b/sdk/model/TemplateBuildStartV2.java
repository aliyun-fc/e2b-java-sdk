package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TemplateBuildStartV2 {
    @JsonProperty("fromImage")
    private String fromImage;

    @JsonProperty("fromTemplate")
    private String fromTemplate;

    @JsonProperty("fromImageRegistry")
    private Map<String, Object> fromImageRegistry;

    private Boolean force;

    private List<TemplateStep> steps;

    @JsonProperty("startCmd")
    private String startCmd;

    @JsonProperty("readyCmd")
    private String readyCmd;
}
