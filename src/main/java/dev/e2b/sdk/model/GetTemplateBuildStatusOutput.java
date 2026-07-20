package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * Result of getting a template build status, including request tracing metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetTemplateBuildStatusOutput {

    /** Build status details from the response body. */
    private TemplateBuildInfo buildInfo;

    /** Request identifier from response header {@code X-Request-ID}. */
    private String requestId;

    /** Full HTTP response headers (first value per name). */
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
