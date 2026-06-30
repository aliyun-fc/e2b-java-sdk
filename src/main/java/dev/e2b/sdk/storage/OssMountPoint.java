package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/** A single OSS mount point. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OssMountPoint {

    @JsonProperty("bucketName")
    private String bucketName;

    @JsonProperty("mountDir")
    private String mountDir;

    @JsonProperty("bucketPath")
    private String bucketPath;

    @JsonProperty("endpoint")
    private String endpoint;

    @JsonProperty("readOnly")
    private Boolean readOnly;
}
