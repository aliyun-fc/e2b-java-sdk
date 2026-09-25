package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ManagedIdentityFailStrategy {
    BLOCK("Block"),
    ALLOW("Allow");

    private final String value;

    ManagedIdentityFailStrategy(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
