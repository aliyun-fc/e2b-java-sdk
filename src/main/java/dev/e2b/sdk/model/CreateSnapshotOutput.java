package dev.e2b.sdk.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * Result of creating a sandbox snapshot, including request tracing metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSnapshotOutput {

    /** Snapshot details from the response body. */
    private SnapshotInfo snapshot;

    /** Request identifier from {@code X-Request-ID}, or {@code x-fc-request-id} if absent. */
    private String requestId;

    /** Full HTTP response headers (first value per name). */
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
