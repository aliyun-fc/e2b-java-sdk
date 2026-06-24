package dev.e2b.sdk.model;
import com.fasterxml.jackson.annotation.JsonValue;
public enum SandboxState {
    RUNNING("running"),
    PAUSED("paused");
    private final String value;
    SandboxState(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}
