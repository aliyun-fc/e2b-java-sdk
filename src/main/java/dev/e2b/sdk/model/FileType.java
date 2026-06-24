package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonValue;
public enum FileType {
    FILE("file"),
    DIR("dir");
    private final String value;
    FileType(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}
