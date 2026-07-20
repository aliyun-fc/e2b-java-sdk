package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of fetching sandbox metrics, including request tracing metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetSandboxMetricsOutput {

    /** Metrics samples from the response body. */
    @Builder.Default
    private List<SandboxMetrics> metrics = Collections.emptyList();

    /** Request identifier from response header {@code X-Request-ID}. */
    private String requestId;

    /** Full HTTP response headers (first value per name). */
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
