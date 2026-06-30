package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from {@code POST /sandboxes/{sandboxID}/connect} (gateway {@code Sandbox} schema).
 */
@Data
@NoArgsConstructor
public class SandboxConnectResponse {
    @JsonProperty("templateID")
    private String templateId;

    @JsonProperty("sandboxID")
    private String sandboxId;

    private String alias;

    @JsonProperty("clientID")
    private String clientId;

    @JsonProperty("envdVersion")
    private String envdVersion;

    @JsonProperty("envdAccessToken")
    private String envdAccessToken;

    @JsonProperty("domain")
    @JsonAlias({"sandbox_domain", "sandboxDomain"})
    private String sandboxDomain;
}
