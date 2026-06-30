package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * VPC binding for a sandbox (serialized into metadata key {@code fc.sandbox.network.vpc}).
 *
 * <p>Binding a sandbox to a VPC lets it reach private endpoints (NAS, JuiceFS, internal services).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VpcConfig {

    @JsonProperty("vpcId")
    private String vpcId;

    @JsonProperty("securityGroupId")
    private String securityGroupId;

    @JsonProperty("vSwitchIds")
    private List<String> vSwitchIds;
}
