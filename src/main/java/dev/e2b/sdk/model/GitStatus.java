package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data
@NoArgsConstructor
public class GitStatus {
    @JsonProperty("current_branch") private String currentBranch;
    private String upstream;
    private int ahead;
    private int behind;
    private boolean detached;
    @JsonProperty("file_status") private List<GitFileStatus> fileStatus;
    public boolean isClean() { return fileStatus == null || fileStatus.isEmpty(); }
    public boolean hasChanges() { return !isClean(); }
    public long getStagedCount() { return fileStatus == null ? 0 : fileStatus.stream().filter(GitFileStatus::isStaged).count(); }
    public long getUntrackedCount() { return fileStatus == null ? 0 : fileStatus.stream().filter(f -> "?".equals(f.getIndexStatus())).count(); }
}
