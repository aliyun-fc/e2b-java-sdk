package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/** A direct AgenticFS mount point; only serverAddr and mountDir are sent to the gateway. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenticFsMountPoint {

    @JsonProperty("serverAddr")
    private String serverAddr;

    @JsonProperty("mountDir")
    private String mountDir;
}
