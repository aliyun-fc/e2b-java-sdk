package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/** A single PolarFS mount point. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolarFsMountPoint {

    @JsonProperty("instanceId")
    private String instanceId;

    @JsonProperty("mountDir")
    private String mountDir;

    @JsonProperty("remoteDir")
    private String remoteDir;

    @JsonProperty("readOnly")
    private Boolean readOnly;
}
