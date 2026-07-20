package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of listing snapshots, including pagination and request tracing metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListSnapshotsOutput {

    /** Snapshots from the response body. */
    @Builder.Default
    private List<SnapshotInfo> snapshots = Collections.emptyList();

    /** Pagination cursor for the next page, from response header {@code x-next-token}. */
    private String nextToken;

    /** Request identifier from {@code X-Request-ID}, or {@code x-fc-request-id} if absent. */
    private String requestId;

    /** Full HTTP response headers (first value per name). */
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
