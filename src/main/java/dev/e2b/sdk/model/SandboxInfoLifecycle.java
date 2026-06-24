package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class SandboxInfoLifecycle {
    @JsonProperty("on_timeout") private String onTimeout; // "pause" | "kill"
    @JsonProperty("auto_resume") private boolean autoResume;
}
