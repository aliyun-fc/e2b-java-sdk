package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class GitFileStatus {
    private String name;
    private String status;
    @JsonProperty("index_status") private String indexStatus;
    @JsonProperty("working_tree_status") private String workingTreeStatus;
    private boolean staged;
    @JsonProperty("renamed_from") private String renamedFrom;
}
