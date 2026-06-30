package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data
@NoArgsConstructor
public class SnapshotInfo {
    @JsonProperty("snapshotID") private String snapshotId;
    private List<String> names;
    @JsonAlias({"sandboxID"}) @JsonProperty("sandboxId") private String sandboxId;
}
