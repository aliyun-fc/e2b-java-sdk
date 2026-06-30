package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
@Builder
public class SandboxNetworkOpts {
    @JsonProperty("allowOut") private List<String> allowOut;
    @JsonProperty("denyOut") private List<String> denyOut;
    private Map<String, Object> rules;
    @JsonProperty("allowPublicTraffic") private Boolean allowPublicTraffic;
    @JsonProperty("maskRequestHost") private String maskRequestHost;
}
