package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class GitStatus {
    @JsonProperty("current_branch") private String currentBranch;
    private String upstream;
    private int ahead;
    private int behind;
    private boolean detached;
    @JsonProperty("file_status") private List<GitFileStatus> fileStatus;
    @JsonIgnore
    private String requestId;
    @JsonIgnore
    private Map<String, String> headers = Collections.emptyMap();

    public boolean isClean() { return fileStatus == null || fileStatus.isEmpty(); }
    public boolean hasChanges() { return !isClean(); }
    public long getStagedCount() { return fileStatus == null ? 0 : fileStatus.stream().filter(GitFileStatus::isStaged).count(); }
    public long getUntrackedCount() { return fileStatus == null ? 0 : fileStatus.stream().filter(f -> "?".equals(f.getIndexStatus())).count(); }
}
