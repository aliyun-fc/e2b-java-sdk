package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TemplateStep {
    private String type;
    private List<String> args;

    @JsonProperty("filesHash")
    private String filesHash;

    private Boolean force;
}
