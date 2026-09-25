package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.e2b.sdk.util.EnvVarsNormalizeConverter;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Body for {@code POST /v2/templates/{templateID}/builds/{buildID}}.
 *
 * <p>Field names match sandbox-gateway {@code e2bTemplateBuildStartV2}.
 * {@code envVars} are baked into the template function; at sandbox create time,
 * request {@code envVars} override same-named template vars (gateway merge order).
 */
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

    /**
     * Default environment variables baked into the template (wire name {@code envVars}).
     * Normalized like gateway {@code e2bTrimEnvVars}: trim keys/values, drop empty keys.
     * Empty / all-blank maps are omitted from JSON ({@code omitempty} semantics).
     */
    @JsonProperty("envVars")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonSerialize(converter = EnvVarsNormalizeConverter.class)
    private Map<String, String> envVars;
}
