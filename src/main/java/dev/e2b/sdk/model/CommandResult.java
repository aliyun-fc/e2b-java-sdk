package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult {
    private String stdout;
    private String stderr;
    @JsonProperty("exit_code") private int exitCode;
    private String error;
    public boolean isSuccess() { return exitCode == 0; }
}
