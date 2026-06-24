package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
@Data
@NoArgsConstructor
public class SandboxMetrics {
    @JsonProperty("cpu_count") private int cpuCount;
    @JsonProperty("cpu_used_pct") private double cpuUsedPct;
    @JsonProperty("disk_total") private long diskTotal;
    @JsonProperty("disk_used") private long diskUsed;
    @JsonProperty("mem_total") private long memTotal;
    @JsonProperty("mem_used") private long memUsed;
    @JsonProperty("mem_cache") private long memCache;
    private Instant timestamp;
}
