package dev.e2b.sdk.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "E2B_API_KEY", matches = ".+")
public abstract class E2eTestBase {

    protected static E2eConfig config;

    @BeforeAll
    static void loadE2eConfig() {
        config = E2eConfig.load();
    }
}
