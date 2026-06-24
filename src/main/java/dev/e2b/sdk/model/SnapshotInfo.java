package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data
@NoArgsConstructor
public class SnapshotInfo {
    @JsonProperty("snapshot_id") private String snapshotId;
    private List<String> names;
}
