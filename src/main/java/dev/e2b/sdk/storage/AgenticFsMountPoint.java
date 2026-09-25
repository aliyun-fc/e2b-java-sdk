package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/** A single AgenticFS mount point. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenticFsMountPoint {

    @JsonProperty("serverAddr")
    private String serverAddr;

    @JsonProperty("mountDir")
    private String mountDir;

    @JsonProperty("fileSystemID")
    private String fileSystemId;

    @JsonProperty("agenticSpaceID")
    private String agenticSpaceId;

    @JsonProperty("accessPointID")
    private String accessPointId;
}
