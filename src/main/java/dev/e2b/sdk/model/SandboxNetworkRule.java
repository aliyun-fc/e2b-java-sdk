package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Rule applied to egress requests matching its entry in the network rules map. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxNetworkRule {

    /** Optional request transformation for this rule. */
    private SandboxNetworkTransform transform;
}
