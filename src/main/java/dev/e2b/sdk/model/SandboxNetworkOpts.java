package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
@Builder
public class SandboxNetworkOpts {
    @JsonProperty("allow_out") private List<String> allowOut;
    @JsonProperty("deny_out") private List<String> denyOut;
    private Map<String, Object> rules;
    @JsonProperty("allow_public_traffic") private Boolean allowPublicTraffic;
    @JsonProperty("mask_request_host") private String maskRequestHost;
}
