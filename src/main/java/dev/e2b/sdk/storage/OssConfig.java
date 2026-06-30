package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OSS storage configuration (serialized into metadata key {@code fc.sandbox.storage.oss}).
 *
 * <p>OSS mounts using RAM role assumption also need the role ARN set via
 * {@link StorageMounts.Builder#roleArn(String)} ({@code fc.sandbox.auth.role}).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OssConfig {

    @JsonProperty("mountPoints")
    private List<OssMountPoint> mountPoints;
}
