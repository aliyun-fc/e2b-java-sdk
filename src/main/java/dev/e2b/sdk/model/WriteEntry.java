package dev.e2b.sdk.model;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class WriteEntry {
    private String path;
    private Object data; // String, byte[], or InputStream
}
