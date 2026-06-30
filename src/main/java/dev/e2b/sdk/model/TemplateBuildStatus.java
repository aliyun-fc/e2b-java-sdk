package dev.e2b.sdk.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TemplateBuildStatus {
    BUILDING("building"),
    WAITING("waiting"),
    READY("ready"),
    ERROR("error");

    private final String value;

    TemplateBuildStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
