package dev.e2b.sdk.model;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;
@Data
@NoArgsConstructor
public class WriteInfo {
    private String name;
    private FileType type;
    private String path;
    private Map<String, String> metadata;
}
