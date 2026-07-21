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
public class ReadFileOutput {

    /** File contents as UTF-8 text (when read via {@code read}). */
    private String text;

    /** Raw file bytes (when read via {@code readBytes}). */
    private byte[] bytes;

    private String requestId;

    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
