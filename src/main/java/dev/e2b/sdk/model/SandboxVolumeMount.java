package dev.e2b.sdk.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SandboxVolumeMount {
    private String name;
    private String path;
}
