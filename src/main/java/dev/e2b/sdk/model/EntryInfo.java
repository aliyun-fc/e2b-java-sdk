package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.time.Instant;
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class EntryInfo extends WriteInfo {
    private long size;
    private int mode;
    private String permissions;
    private String owner;
    private String group;
    @JsonProperty("modifiedTime") private Instant modifiedTime;
    @JsonProperty("symlinkTarget") private String symlinkTarget;
}
