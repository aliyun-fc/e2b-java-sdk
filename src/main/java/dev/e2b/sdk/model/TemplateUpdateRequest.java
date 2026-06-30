package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateUpdateRequest {
    @JsonProperty("public")
    private Boolean value;
}
