package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KillProcessOutput {

    private boolean killed;

    private String requestId;

    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
