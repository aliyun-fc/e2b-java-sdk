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
public class GitBranches {
    private List<String> branches;
    @JsonProperty("current_branch") private String currentBranch;
    @JsonIgnore
    private String requestId;
    @JsonIgnore
    private Map<String, String> headers = Collections.emptyMap();
}
