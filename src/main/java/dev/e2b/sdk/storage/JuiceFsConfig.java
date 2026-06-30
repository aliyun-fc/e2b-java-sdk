package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * JuiceFS storage configuration (serialized into metadata key {@code fc.sandbox.storage.juicefs}).
 *
 * <p>JuiceFS volumes typically require the sandbox to be bound to a {@link VpcConfig}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JuiceFsConfig {

    /** Environment variables for the JuiceFS client, e.g. {@code BASE_URL}, {@code GOGC}. */
    @JsonProperty("envs")
    private Map<String, String> envs;

    @JsonProperty("mountPoints")
    private List<JuiceFsMountPoint> mountPoints;
}
