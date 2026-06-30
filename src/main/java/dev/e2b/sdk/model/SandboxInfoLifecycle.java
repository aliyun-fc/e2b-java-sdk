package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class SandboxInfoLifecycle {
    @JsonProperty("onTimeout") private String onTimeout; // "pause" | "kill"
    @JsonProperty("autoResume") private boolean autoResume;
}
