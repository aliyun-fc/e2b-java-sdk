package dev.e2b.sdk;

import dev.e2b.sdk.client.ApiResponse;
import dev.e2b.sdk.client.ConnectionConfig;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.TemplateException;
import dev.e2b.sdk.model.*;

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

    private static final String HEADER_NEXT_TOKEN = "x-next-token";

    private Template() {
    }

    public static ListTemplatesOutput list(ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        ApiResponse<TemplateInfo[]> response = api.getWithResponse("/templates", null, TemplateInfo[].class);
        TemplateInfo[] arr = response.getBody();
        return ListTemplatesOutput.builder()
                .templates(arr != null ? Arrays.asList(arr) : Collections.emptyList())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static GetTemplateOutput get(String templateId, ConnectionConfig config) {
        return get(templateId, config, null, null);
    }

    public static GetTemplateOutput get(String templateId,
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
        ApiResponse<TemplateWithBuilds> response = api.getWithResponse(
                "/templates/" + encode(templateId), params, TemplateWithBuilds.class);
        return GetTemplateOutput.builder()
                .template(response.getBody())
                .nextToken(response.header(HEADER_NEXT_TOKEN))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * @deprecated The legacy {@code POST /v2/templates} endpoint can hold the connection open
     * (hang) on some gateways. Use {@link #createV3(TemplateBuildRequestV3, ConnectionConfig)} or
     * the higher-level {@link #buildFromImage(String, String, ConnectionConfig, long)} instead,
     * both of which use the current {@code /v3/templates} path.
     */
    @Deprecated
    public static CreateTemplateOutput create(TemplateBuildRequestV2 request, ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        ApiResponse<TemplateLegacy> response = api.postWithResponse("/v2/templates", request, TemplateLegacy.class);
        return CreateTemplateOutput.builder()
                .template(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static CreateTemplateV3Output createV3(TemplateBuildRequestV3 request, ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        ApiResponse<TemplateRequestResponseV3> response = api.postWithResponse(
                "/v3/templates", request, TemplateRequestResponseV3.class);
        return CreateTemplateV3Output.builder()
                .template(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static UpdateTemplateOutput update(String templateId,
                                              TemplateUpdateRequest request,
                                              ConnectionConfig config) {
        E2bApiClient api = new E2bApiClient(config);
        // Canonical update path is PATCH /templates/{id} (no /v2), matching the e2b control plane.
        ApiResponse<TemplateUpdateResponse> response = api.patchWithResponse(
                "/templates/" + encode(templateId), request, TemplateUpdateResponse.class);
        return UpdateTemplateOutput.builder()
                .response(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static UpdateTemplateOutput setPublic(String templateId, boolean isPublic, ConnectionConfig config) {
        return update(templateId,
                TemplateUpdateRequest.builder().value(isPublic).build(),
                config);
    }

    public static DeleteTemplateOutput delete(String templateId, ConnectionConfig config) {
        ApiResponse<Boolean> response = new E2bApiClient(config)
                .deleteWithResponse("/templates/" + encode(templateId));
        return DeleteTemplateOutput.builder()
                .deleted(Boolean.TRUE.equals(response.getBody()))
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static StartTemplateBuildOutput startBuild(String templateId,
                                                      String buildId,
                                                      TemplateBuildStartV2 request,
                                                      ConnectionConfig config) {
        ApiResponse<Void> response = new E2bApiClient(config).postWithResponse(
                "/v2/templates/" + encode(templateId) + "/builds/" + encode(buildId),
                request,
                Void.class);
        return StartTemplateBuildOutput.builder()
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static GetTemplateBuildStatusOutput getBuildStatus(String templateId,
                                                              String buildId,
                                                              ConnectionConfig config) {
        return getBuildStatus(templateId, buildId, config, null, null, null);
    }

    public static GetTemplateBuildStatusOutput getBuildStatus(String templateId,
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
        ApiResponse<TemplateBuildInfo> response = api.getWithResponse(
                "/templates/" + encode(templateId) + "/builds/" + encode(buildId) + "/status",
                params,
                TemplateBuildInfo.class);
        return GetTemplateBuildStatusOutput.builder()
                .buildInfo(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    public static GetTemplateBuildLogsOutput getBuildLogs(String templateId,
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
        ApiResponse<TemplateBuildLogsResponse> response = api.getWithResponse(
                "/templates/" + encode(templateId) + "/builds/" + encode(buildId) + "/logs",
                params,
                TemplateBuildLogsResponse.class);
        return GetTemplateBuildLogsOutput.builder()
                .logs(response.getBody())
                .requestId(response.requestId())
                .headers(response.headersAsMap())
                .build();
    }

    /**
     * Create a template alias, start an image build, and wait until READY.
     */
    public static TemplateWithBuilds buildFromImage(String alias,
                                                    String image,
                                                    ConnectionConfig config,
                                                    long timeoutSeconds) {
        // Request a build via POST /v3/templates (the v2 create endpoint can hold the connection
        // open on some gateways; v3 is the current path used by the reference SDK).
        TemplateBuildRequestV3 createReq = TemplateBuildRequestV3.builder()
                .name(alias)
                .cpuCount(2)
                .memoryMb(2048)
                .build();
        TemplateRequestResponseV3 created = createV3(createReq, config).getTemplate();
        if (created == null || created.getTemplateId() == null || created.getBuildId() == null) {
            throw new TemplateException("Template create did not return templateID/buildID");
        }

        TemplateBuildStartV2 startReq = TemplateBuildStartV2.builder()
                .fromImage(image)
                .build();
        startBuild(created.getTemplateId(), created.getBuildId(), startReq, config);

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            TemplateBuildInfo status = getBuildStatus(
                    created.getTemplateId(), created.getBuildId(), config).getBuildInfo();
            if (status.getStatus() == TemplateBuildStatus.READY) {
                return get(created.getTemplateId(), config).getTemplate();
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
