package dev.e2b.sdk.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectSandbox {
    private Integer timeout;
}
