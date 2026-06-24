package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data
@NoArgsConstructor
public class GitBranches {
    private List<String> branches;
    @JsonProperty("current_branch") private String currentBranch;
}
