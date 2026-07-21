package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CommandResult {
    private String stdout;
    private String stderr;
    @JsonProperty("exitCode")
    private int exitCode;
    private String error;

    /** Request id from {@code X-Request-ID}, or {@code x-fc-request-id} if absent. */
    @JsonIgnore
    private String requestId;

    /** Full HTTP response headers (first value per name). */
    @JsonIgnore
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
