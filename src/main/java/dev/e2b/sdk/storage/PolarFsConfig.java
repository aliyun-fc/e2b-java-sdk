package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** PolarFS storage configuration. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolarFsConfig {

    @JsonProperty("groupId")
    private Integer groupId;

    @JsonProperty("userId")
    private Integer userId;

    @JsonProperty("mountPoints")
    private List<PolarFsMountPoint> mountPoints;
}
