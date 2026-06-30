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
    private Map<String, List<Object>> rules;
    @JsonProperty("allowPublicTraffic") private Boolean allowPublicTraffic;
    @JsonProperty("maskRequestHost") private String maskRequestHost;
}
