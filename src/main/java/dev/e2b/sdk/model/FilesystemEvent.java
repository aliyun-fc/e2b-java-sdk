package dev.e2b.sdk.model;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
public class FilesystemEvent {
    private String name;
    private FilesystemEventType type;
    private EntryInfo entry;
}
