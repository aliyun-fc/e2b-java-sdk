package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonValue;
public enum FilesystemEventType {
    CHMOD("chmod"), CREATE("create"), REMOVE("remove"), RENAME("rename"), WRITE("write");
    private final String value;
    FilesystemEventType(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}
