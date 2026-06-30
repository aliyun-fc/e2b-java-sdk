package dev.e2b.sdk.e2e;

import dev.e2b.sdk.client.ConnectionConfig;

/**
 * E2E connection settings loaded from environment variables.
 *
 * <p>Required: {@code E2B_API_KEY}
 * <p>Optional: {@code E2B_DOMAIN}, {@code E2B_API_URL}, {@code E2B_CLI_TEMPLATE}, {@code E2B_VERIFY_ENV}
 */
public final class E2eConfig {

    private final String envName;
    private final String apiKey;
    private final String domain;
    private final String apiUrl;
    private final String template;

    private E2eConfig(String envName, String apiKey, String domain, String apiUrl, String template) {
        this.envName = envName;
        this.apiKey = apiKey;
        this.domain = domain;
        this.apiUrl = apiUrl;
        this.template = template;
    }

    public static E2eConfig load() {
        String apiKey = System.getenv("E2B_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "E2B_API_KEY is required for E2E tests. Export it or set it in your shell environment.");
        }
        String envName = envOrDefault("E2B_VERIFY_ENV", "local");
        String domain = emptyToNull(System.getenv("E2B_DOMAIN"));
        String apiUrl = emptyToNull(System.getenv("E2B_API_URL"));
        String template = envOrDefault("E2B_CLI_TEMPLATE", "base");
        return new E2eConfig(envName, apiKey.trim(), domain, apiUrl, template);
    }

    public ConnectionConfig toConnectionConfig() {
        ConnectionConfig.ConnectionConfigBuilder builder = ConnectionConfig.builder()
                .apiKey(apiKey)
                .requestTimeout(120.0);
        if (domain != null) {
            builder.domain(domain);
        }
        if (apiUrl != null) {
            builder.apiUrl(apiUrl);
        }
        return builder.build();
    }

    public String getEnvName() {
        return envName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDomain() {
        return domain;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getTemplate() {
        return template;
    }

    public int httpPort() {
        String raw = System.getenv("E2E_HTTP_PORT");
        return raw != null && !raw.isEmpty() ? Integer.parseInt(raw) : 8080;
    }

    public String gitRepoUrl() {
        return envOrDefault("E2E_GIT_REPO_URL", "https://gitee.com/aliyunfc/Hello-World.git");
    }

    /** Template (alias or id) for the code-interpreter sandbox; defaults to {@code code-interpreter-v1}. */
    public String codeInterpreterTemplate() {
        return envOrDefault("E2E_CI_TEMPLATE", "code-interpreter-v1");
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    private static String emptyToNull(String value) {
        return value != null && !value.isEmpty() ? value : null;
    }
}
