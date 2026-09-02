package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
@Data
@NoArgsConstructor
public class SandboxNetworkInfo {
    @JsonProperty("allowOut") private List<String> allowOut;
    @JsonProperty("denyOut") private List<String> denyOut;
    /** Materialized transform rules keyed by host, domain pattern, IP address, or CIDR. */
    private Map<String, List<SandboxNetworkRule>> rules;
    @JsonProperty("allowPublicTraffic") private Boolean allowPublicTraffic;
    @JsonProperty("maskRequestHost") private String maskRequestHost;
}
