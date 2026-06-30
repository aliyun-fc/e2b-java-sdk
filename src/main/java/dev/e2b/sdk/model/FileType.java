package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * File type as reported by envd. The wire format is the protobuf enum name
 * ({@code FILE_TYPE_FILE} / {@code FILE_TYPE_DIRECTORY}).
 */
public enum FileType {
    FILE("FILE_TYPE_FILE"),
    DIR("FILE_TYPE_DIRECTORY"),
    UNSPECIFIED("FILE_TYPE_UNSPECIFIED");

    private final String value;

    FileType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static FileType fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "FILE_TYPE_FILE":
            case "file":
                return FILE;
            case "FILE_TYPE_DIRECTORY":
            case "dir":
                return DIR;
            default:
                return UNSPECIFIED;
        }
    }
}
