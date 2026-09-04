package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Transformations applied to a matching egress HTTP or HTTPS request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxNetworkTransform {

    /** Headers to inject or replace on the outbound request. */
    private Map<String, String> headers;
}
