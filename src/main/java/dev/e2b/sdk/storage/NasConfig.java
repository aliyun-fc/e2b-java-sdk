package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** NAS storage configuration (serialized into metadata key {@code fc.sandbox.storage.nas}). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NasConfig {

    @JsonProperty("mountPoints")
    private List<NasMountPoint> mountPoints;
}
