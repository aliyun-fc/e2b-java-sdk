package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of listing sandboxes, including pagination and request tracing metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListSandboxesOutput {

    /** Sandboxes returned for the current page. */
    @Builder.Default
    private List<SandboxInfo> sandboxes = Collections.emptyList();

    /** Pagination cursor for the next page, from response header {@code x-next-token}. */
    private String nextToken;

    /** Request identifier from {@code X-Request-ID}, or {@code x-fc-request-id} if absent. */
    private String requestId;

    /** Full HTTP response headers (first value per name). */
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
