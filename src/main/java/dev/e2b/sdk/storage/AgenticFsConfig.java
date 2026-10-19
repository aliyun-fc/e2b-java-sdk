package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** AgenticFS storage configuration. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenticFsConfig {

    @JsonProperty("groupID")
    private Integer groupId;

    @JsonProperty("userID")
    private Integer userId;

    @JsonProperty("mountPoints")
    private List<AgenticFsMountPoint> mountPoints;
}
