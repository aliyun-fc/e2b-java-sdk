package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/** Logging configuration. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoggingConfig {

    @JsonProperty("project")
    private String project;

    @JsonProperty("logstore")
    private String logstore;

    @JsonProperty("logBeginRule")
    private String logBeginRule;

    @JsonProperty("enableRequestMetrics")
    private Boolean enableRequestMetrics;

    @JsonProperty("enableInstanceMetrics")
    private Boolean enableInstanceMetrics;

    @JsonProperty("enableLlmMetrics")
    private Boolean enableLlmMetrics;
}
