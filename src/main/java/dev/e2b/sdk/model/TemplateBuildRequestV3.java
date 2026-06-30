package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TemplateBuildRequestV3 {
    private String name;
    private List<String> tags;
    private String alias;

    @JsonProperty("teamID")
    private String teamId;

    @JsonProperty("cpuCount")
    private Integer cpuCount;

    @JsonProperty("memoryMB")
    private Integer memoryMb;
}
