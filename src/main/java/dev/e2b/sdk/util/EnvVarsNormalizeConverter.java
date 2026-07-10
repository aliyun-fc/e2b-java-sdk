package dev.e2b.sdk.util;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.util.Map;

/**
 * Jackson converter that applies {@link EnvVars#normalize(Map)} before serialize,
 * matching sandbox-gateway {@code e2bTrimEnvVars}.
 */
public final class EnvVarsNormalizeConverter extends StdConverter<Map<String, String>, Map<String, String>> {
    @Override
    public Map<String, String> convert(Map<String, String> value) {
        return EnvVars.normalize(value);
    }
}
