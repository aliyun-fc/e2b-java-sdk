package dev.e2b.sdk;

import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.TemplateException;
import dev.e2b.sdk.model.*;

import dev.e2b.sdk.util.EnvVars;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Template management — list, create, build, update, and delete templates.
 *
 * <p>Routes align with sandbox-gateway E2B-compatible control plane:
 * {@code /templates}, {@code /v2/templates}, {@code /v3/templates}.
 */
public final class Template {

    private Template() {
    }

    public static List<TemplateInfo> list(ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        TemplateInfo[] arr = api.get("/templates", TemplateInfo[].class);
        return arr != null ? Arrays.asList(arr) : Collections.emptyList();
    }

    public static TemplateWithBuilds get(String templateId, ConnectionConfig config) {
        return get(templateId, config, null, null);
    }

    public static TemplateWithBuilds get(String templateId,
                                         ConnectionConfig config,
                                         Integer limit,
                                         String nextToken) {
        E2bApiClient api = new E2bApiClient(config);
        Map<String, String> params = new HashMap<String, String>();
        if (limit != null) {
            params.put("limit", String.valueOf(limit));
        }
        if (nextToken != null) {
            params.put("nextToken", nextToken);
        }
        return api.get("/templates/" + encode(templateId), params, TemplateWithBuilds.class);
    }

    /**
     * @deprecated The legacy {@code POST /v2/templates} endpoint can hold the connection open
     * (hang) on some gateways. Use {@link #createV3(TemplateBuildRequestV3, ConnectionConfig)} or
     * the higher-level {@link #buildFromImage(String, String, ConnectionConfig, long)} instead,
     * both of which use the current {@code /v3/templates} path.
     */
    @Deprecated
    public static TemplateLegacy create(TemplateBuildRequestV2 request, ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        return api.post("/v2/templates", request, TemplateLegacy.class);
    }

    public static TemplateRequestResponseV3 createV3(TemplateBuildRequestV3 request, ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        return api.post("/v3/templates", request, TemplateRequestResponseV3.class);
    }

    public static TemplateUpdateResponse update(String templateId,
                                                TemplateUpdateRequest request,
                                                ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        // Canonical update path is PATCH /templates/{id} (no /v2), matching the e2b control plane.
        return api.patch("/templates/" + encode(templateId), request, TemplateUpdateResponse.class);
    }

    public static TemplateUpdateResponse setPublic(String templateId, boolean isPublic, ConnectionConfig config) {
        return update(templateId,
                TemplateUpdateRequest.builder().value(isPublic).build(),
                config);
    }

    public static boolean delete(String templateId, ConnectionConfig config) {
        return new E2bApiClient(config).delete("/templates/" + encode(templateId));
    }

    public static void startBuild(String templateId,
                                  String buildId,
                                  TemplateBuildStartV2 request,
                                  ConnectionConfig config) {
        TemplateBuildStartV2 body = request;
        if (request != null && request.getEnvVars() != null) {
            // Match gateway e2bTrimEnvVars before POST so empty/blank-key maps are omitted.
            body = TemplateBuildStartV2.builder()
                    .fromImage(request.getFromImage())
                    .fromTemplate(request.getFromTemplate())
                    .fromImageRegistry(request.getFromImageRegistry())
                    .force(request.getForce())
                    .steps(request.getSteps())
                    .startCmd(request.getStartCmd())
                    .readyCmd(request.getReadyCmd())
                    .envVars(EnvVars.normalize(request.getEnvVars()))
                    .build();
        }
        new E2bApiClient(config).post(
                "/v2/templates/" + encode(templateId) + "/builds/" + encode(buildId),
                body,
                Void.class);
    }

    public static TemplateBuildInfo getBuildStatus(String templateId,
                                                     String buildId,
                                                     ConnectionConfig config) {
        return getBuildStatus(templateId, buildId, config, null, null, null);
    }

    public static TemplateBuildInfo getBuildStatus(String templateId,
                                                     String buildId,
                                                     ConnectionConfig config,
                                                     Integer logsOffset,
                                                     Integer limit,
                                                     String level) {
        E2bApiClient api = new E2bApiClient(config);
        Map<String, String> params = new HashMap<String, String>();
        if (logsOffset != null) {
            params.put("logsOffset", String.valueOf(logsOffset));
        }
        if (limit != null) {
            params.put("limit", String.valueOf(limit));
        }
        if (level != null) {
            params.put("level", level);
        }
        return api.get(
                "/templates/" + encode(templateId) + "/builds/" + encode(buildId) + "/status",
                params,
                TemplateBuildInfo.class);
    }

    public static TemplateBuildLogsResponse getBuildLogs(String templateId,
                                                         String buildId,
                                                         ConnectionConfig config,
                                                         Integer logsOffset,
                                                         Integer limit,
                                                         String level) {
        E2bApiClient api = new E2bApiClient(config);
        Map<String, String> params = new HashMap<String, String>();
        if (logsOffset != null) {
            params.put("logsOffset", String.valueOf(logsOffset));
        }
        if (limit != null) {
            params.put("limit", String.valueOf(limit));
        }
        if (level != null) {
            params.put("level", level);
        }
        return api.get(
                "/templates/" + encode(templateId) + "/builds/" + encode(buildId) + "/logs",
                params,
                TemplateBuildLogsResponse.class);
    }

    /**
     * Create a template alias, start an image build, and wait until READY.
     */
    public static TemplateWithBuilds buildFromImage(String alias,
                                                    String image,
                                                    ConnectionConfig config,
                                                    long timeoutSeconds) {
        return buildFromImage(alias, image, null, config, timeoutSeconds);
    }

    /**
     * Create a template alias, start an image build with optional default env vars, and wait until READY.
     *
     * @param envVars template-level environment variables (may be null); sandbox create-time envVars override these
     */
    public static TemplateWithBuilds buildFromImage(String alias,
                                                    String image,
                                                    Map<String, String> envVars,
                                                    ConnectionConfig config,
                                                    long timeoutSeconds) {
        // Request a build via POST /v3/templates (the v2 create endpoint can hold the connection
        // open on some gateways; v3 is the current path used by the reference SDK).
        TemplateBuildRequestV3 createReq = TemplateBuildRequestV3.builder()
                .name(alias)
                .cpuCount(2)
                .memoryMb(2048)
                .build();
        TemplateRequestResponseV3 created = createV3(createReq, config);
        if (created.getTemplateId() == null || created.getBuildId() == null) {
            throw new TemplateException("Template create did not return templateID/buildID");
        }

        TemplateBuildStartV2 startReq = TemplateBuildStartV2.builder()
                .fromImage(image)
                .envVars(EnvVars.normalize(envVars))
                .build();
        startBuild(created.getTemplateId(), created.getBuildId(), startReq, config);

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            TemplateBuildInfo status = getBuildStatus(
                    created.getTemplateId(), created.getBuildId(), config);
            if (status.getStatus() == TemplateBuildStatus.READY) {
                return get(created.getTemplateId(), config);
            }
            if (status.getStatus() == TemplateBuildStatus.ERROR) {
                throw new TemplateException("Template build failed for " + alias);
            }
            sleepQuietly(1500);
        }
        throw new TemplateException("Timeout waiting for template build: " + alias);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new TemplateException("Failed to encode template path segment", e);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TemplateException("Interrupted while waiting for template build", e);
        }
    }
}
