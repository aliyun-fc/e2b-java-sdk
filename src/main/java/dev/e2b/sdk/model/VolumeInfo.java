package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class VolumeInfo {
    @JsonProperty("volume_id") private String volumeId;
    private String name;
}
