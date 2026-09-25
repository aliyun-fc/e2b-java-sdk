package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AgenticBucket storage configuration (serialized into metadata key
 * {@code fc.sandbox.storage.agenticbucket}).
 *
 * <p>The gateway supplies internal ossfs2 execution options. Callers provide only the public
 * AgenticBucket and BucketSpace fields.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenticBucketConfig {

    @JsonProperty("mountPoints")
    private List<AgenticBucketMountPoint> mountPoints;
}
