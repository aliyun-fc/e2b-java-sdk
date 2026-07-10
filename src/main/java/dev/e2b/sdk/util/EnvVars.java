package dev.e2b.sdk.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Environment-variable helpers aligned with sandbox-gateway E2B handling.
 *
 * <p>Gateway template build ({@code e2bTrimEnvVars}) trims keys/values and drops empty keys
 * before baking vars into the template. Sandbox create merges template env vars with request
 * {@code envVars}, with request values winning on key conflicts.
 */
public final class EnvVars {

    private EnvVars() {
    }

    /**
     * Normalize env vars the same way gateway does for E2B template build:
     * trim keys and values, drop entries whose key is empty after trim.
     *
     * @param envVars raw map (may be null)
     * @return normalized map, or {@code null} when input is null / empty after normalize
     */
    public static Map<String, String> normalize(Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return null;
        }
        Map<String, String> trimmed = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().trim();
            if (key.isEmpty()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            trimmed.put(key, value);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(trimmed);
    }
}
