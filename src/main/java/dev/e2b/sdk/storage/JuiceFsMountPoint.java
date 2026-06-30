package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** A single JuiceFS mount point. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JuiceFsMountPoint {

    @JsonProperty("volumeName")
    private String volumeName;

    @JsonProperty("mountDir")
    private String mountDir;

    @JsonProperty("remoteDir")
    private String remoteDir;

    @JsonProperty("token")
    private String token;

    /** Extra juicefs mount args, e.g. {@code --writeback}, {@code --cache-dir /tmp/jfsCache}. */
    @JsonProperty("args")
    private List<String> args;
}
