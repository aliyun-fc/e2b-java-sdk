package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
@Builder
public class SandboxNetworkUpdate {
    @JsonProperty("allowOut") private List<String> allowOut;
    @JsonProperty("denyOut") private List<String> denyOut;
    /** Replacement transform rules keyed by host, domain pattern, IP address, or CIDR. */
    private Map<String, List<SandboxNetworkRule>> rules;
    @JsonProperty("allowInternetAccess") private Boolean allowInternetAccess;
}
