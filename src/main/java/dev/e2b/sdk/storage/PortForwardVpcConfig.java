package dev.e2b.sdk.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Port-forward VPC configuration. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortForwardVpcConfig {

    @JsonProperty("vpcId")
    private String vpcId;

    @JsonProperty("securityGroupId")
    private String securityGroupId;

    @JsonProperty("vSwitchIds")
    private List<String> vSwitchIds;
}
