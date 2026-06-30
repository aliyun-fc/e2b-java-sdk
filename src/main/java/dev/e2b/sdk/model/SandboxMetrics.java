package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
@Data
@NoArgsConstructor
public class SandboxMetrics {
    @JsonProperty("cpuCount") private int cpuCount;
    @JsonProperty("cpuUsedPct") private double cpuUsedPct;
    @JsonProperty("diskTotal") private long diskTotal;
    @JsonProperty("diskUsed") private long diskUsed;
    @JsonProperty("memTotal") private long memTotal;
    @JsonProperty("memUsed") private long memUsed;
    @JsonProperty("memCache") private long memCache;
    @JsonProperty("timestampUnix") private long timestampUnix;
    private Instant timestamp;
}
