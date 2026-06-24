package dev.e2b.sdk.model;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
@Data
@Builder
public class SandboxQuery {
    private Map<String, String> metadata;
    private List<SandboxState> state;
}
