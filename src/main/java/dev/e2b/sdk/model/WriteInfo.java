package dev.e2b.sdk.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@NoArgsConstructor
public class WriteInfo {
    private String name;
    private FileType type;
    private String path;
    private Map<String, String> metadata;

    /** Request id from response headers ({@code X-Request-ID} or {@code X-Fc-Request-Id}). */
    private String requestId;

    private Map<String, String> headers = Collections.emptyMap();
}
