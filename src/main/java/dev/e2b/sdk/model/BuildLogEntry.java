package dev.e2b.sdk.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class BuildLogEntry {
    private Instant timestamp;
    private String message;
    private String level;
    private String step;
}
