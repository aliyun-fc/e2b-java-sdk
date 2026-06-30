package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateBuildRequestV2 {
    private String alias;

    @JsonProperty("teamID")
    private String teamId;

    @JsonProperty("cpuCount")
    private Integer cpuCount;

    @JsonProperty("memoryMB")
    private Integer memoryMb;
}
