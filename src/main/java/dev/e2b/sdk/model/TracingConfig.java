package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** Tracing configuration. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TracingConfig {

    @JsonProperty("type")
    private String type;

    @JsonProperty("params")
    private Map<String, String> params;
}
