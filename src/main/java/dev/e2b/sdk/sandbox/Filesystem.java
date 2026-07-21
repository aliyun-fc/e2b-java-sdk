package dev.e2b.sdk.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import dev.e2b.sdk.client.ApiResponse;
import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.model.EntryInfo;
import dev.e2b.sdk.model.ExistsOutput;
import dev.e2b.sdk.model.ListEntriesOutput;
import dev.e2b.sdk.model.MakeDirOutput;
import dev.e2b.sdk.model.ReadFileOutput;
import dev.e2b.sdk.model.RemoveFileOutput;
import dev.e2b.sdk.model.WriteInfo;
import lombok.RequiredArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Filesystem module: read, write, list, move, watch files inside the sandbox.
 *
 * <p>File content read/write use the envd REST endpoint {@code /files}; directory and
 * metadata operations use the Connect (unary) protocol under {@code /filesystem.Filesystem/*}.
 */
@RequiredArgsConstructor
public class Filesystem {

    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final MediaType JSON  = MediaType.get("application/json");

    private static final String FILES_ROUTE = "/files";

    private final E2bApiClient api;
    private final String envdUrl;
    private final String accessToken;

    /**
     * Read a file as UTF-8 text.
     *
     * <p>The returned {@link ReadFileOutput} sets {@code text} (and request metadata).
     * Raw bytes are not retained — use {@link #readBytes(String, String)} when you need them.
     */
    public ReadFileOutput read(String path, String user) {
        ReadFileOutput raw = readBytes(path, user);
        byte[] bytes = raw.getBytes() != null ? raw.getBytes() : new byte[0];
        return ReadFileOutput.builder()
                .text(new String(bytes, StandardCharsets.UTF_8))
                .requestId(raw.getRequestId())
                .headers(raw.getHeaders())
                .build();
    }

    public ReadFileOutput read(String path) {
        return read(path, null);
    }

    /**
     * Read a file as raw bytes (uses REST endpoint {@code /files}).
     */
    public ReadFileOutput readBytes(String path, String user) {
        HttpUrl url = buildFileUrl(path, user);
        Request req = new Request.Builder()
                .url(url)
                .get()
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "read");
            byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
            return ReadFileOutput.builder()
                    .bytes(bytes)
                    .requestId(ApiResponse.requestIdFrom(resp.headers()))
                    .headers(ApiResponse.headersAsMap(resp.headers()))
                    .build();
        } catch (IOException e) {
            throw new SandboxException("Failed to read file: " + path, e);
        }
    }

    /**
     * Write a string to a file.
     */
    public WriteInfo write(String path, String content, String user, Map<String, String> metadata) {
        return writeBytes(path, content.getBytes(StandardCharsets.UTF_8), user, metadata);
    }

    public WriteInfo write(String path, String content) {
        return write(path, content, null, null);
    }

    /**
     * Write raw bytes to a file using {@code multipart/form-data} (envd {@code /files}).
     *
     * <p>The envd response is a JSON array of written-file descriptors; we return the first.
     */
    public WriteInfo writeBytes(String path, byte[] data, String user, Map<String, String> metadata) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(envdUrl + FILES_ROUTE).newBuilder()
                .addQueryParameter("path", path);
        if (user != null) {
            urlBuilder.addQueryParameter("username", user);
        }

        RequestBody fileBody = RequestBody.create(data, OCTET);
        MultipartBody.Builder mp = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", path, fileBody);

        Request.Builder builder = new Request.Builder()
                .url(urlBuilder.build())
                .post(mp.build())
                .header("X-Access-Token", tok());
        if (metadata != null) {
            metadata.forEach((k, v) -> builder.header("X-Metadata-" + k, v));
        }
        try (Response resp = api.httpClient().newCall(builder.build()).execute()) {
            handleError(resp, "write");
            String body = resp.body() != null ? resp.body().string() : "[]";
            JsonNode root = api.mapper.readTree(body);
            JsonNode target = root.isArray() && root.size() > 0 ? root.get(0) : root;
            WriteInfo info = api.mapper.treeToValue(target, WriteInfo.class);
            return attachMeta(info, resp.headers());
        } catch (IOException e) {
            throw new SandboxException("Failed to write file: " + path, e);
        }
    }

    public WriteInfo write(String path, byte[] data) {
        return writeBytes(path, data, null, null);
    }

    /**
     * Write an InputStream to a file.
     */
    public WriteInfo write(String path, InputStream stream, String user) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = stream.read(buf)) != -1) {
                buffer.write(buf, 0, read);
            }
            return writeBytes(path, buffer.toByteArray(), user, null);
        } catch (IOException e) {
            throw new SandboxException("Failed to read InputStream for file: " + path, e);
        }
    }

    /**
     * List directory contents (Connect: {@code /filesystem.Filesystem/ListDir}).
     *
     * <p>Response shape is {@code {"entries": [EntryInfo, ...]}}.
     */
    public ListEntriesOutput list(String path, Integer depth, String user) {
        StringBuilder json = new StringBuilder("{\"path\":").append(q(path));
        if (depth != null) json.append(",\"depth\":").append(depth);
        if (user  != null) json.append(",\"user\":").append(q(user));
        json.append("}");

        Request req = buildConnectRequest("/filesystem.Filesystem/ListDir", json.toString(), user);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "list");
            String body = resp.body() != null ? resp.body().string() : "{}";
            JsonNode root = api.mapper.readTree(body);
            JsonNode entries = root.get("entries");
            List<EntryInfo> result = new ArrayList<EntryInfo>();
            if (entries != null && entries.isArray()) {
                for (JsonNode node : entries) {
                    result.add(api.mapper.treeToValue(node, EntryInfo.class));
                }
            }
            return ListEntriesOutput.builder()
                    .entries(result)
                    .requestId(ApiResponse.requestIdFrom(resp.headers()))
                    .headers(ApiResponse.headersAsMap(resp.headers()))
                    .build();
        } catch (IOException e) {
            throw new SandboxException("Failed to list directory: " + path, e);
        }
    }

    public ListEntriesOutput list(String path) {
        return list(path, 1, null);
    }

    /**
     * Check if a path exists.
     */
    public ExistsOutput exists(String path, String user) {
        try {
            EntryInfo info = getInfo(path, user);
            return ExistsOutput.builder()
                    .exists(true)
                    .requestId(info.getRequestId())
                    .headers(info.getHeaders())
                    .build();
        } catch (SandboxException e) {
            return ExistsOutput.builder()
                    .exists(false)
                    .requestId(e.getRequestId())
                    .headers(e.getHeaders())
                    .build();
        }
    }

    public ExistsOutput exists(String path) {
        return exists(path, null);
    }

    /**
     * Get metadata (stat) for a file or directory (Connect: {@code /filesystem.Filesystem/Stat}).
     *
     * <p>Response shape is {@code {"entry": EntryInfo}}.
     */
    public EntryInfo getInfo(String path, String user) {
        StringBuilder json = new StringBuilder("{\"path\":").append(q(path));
        if (user != null) json.append(",\"user\":").append(q(user));
        json.append("}");

        Request req = buildConnectRequest("/filesystem.Filesystem/Stat", json.toString(), user);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "getInfo");
            String body = resp.body() != null ? resp.body().string() : "{}";
            return attachMeta(extractEntry(body), resp.headers());
        } catch (IOException e) {
            throw new SandboxException("Failed to stat: " + path, e);
        }
    }

    public EntryInfo getInfo(String path) {
        return getInfo(path, null);
    }

    /**
     * Remove a file or directory (Connect: {@code /filesystem.Filesystem/Remove}).
     */
    public RemoveFileOutput remove(String path, String user) {
        StringBuilder json = new StringBuilder("{\"path\":").append(q(path));
        if (user != null) json.append(",\"user\":").append(q(user));
        json.append("}");

        Request req = buildConnectRequest("/filesystem.Filesystem/Remove", json.toString(), user);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "remove");
            return RemoveFileOutput.builder()
                    .requestId(ApiResponse.requestIdFrom(resp.headers()))
                    .headers(ApiResponse.headersAsMap(resp.headers()))
                    .build();
        } catch (IOException e) {
            throw new SandboxException("Failed to remove: " + path, e);
        }
    }

    public RemoveFileOutput remove(String path) {
        return remove(path, null);
    }

    /**
     * Rename / move a path (Connect: {@code /filesystem.Filesystem/Move}).
     *
     * <p>Response shape is {@code {"entry": EntryInfo}}.
     */
    public EntryInfo rename(String oldPath, String newPath, String user) {
        StringBuilder json = new StringBuilder("{\"source\":").append(q(oldPath))
                .append(",\"destination\":").append(q(newPath));
        if (user != null) json.append(",\"user\":").append(q(user));
        json.append("}");

        Request req = buildConnectRequest("/filesystem.Filesystem/Move", json.toString(), user);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "rename");
            String body = resp.body() != null ? resp.body().string() : "{}";
            return attachMeta(extractEntry(body), resp.headers());
        } catch (IOException e) {
            throw new SandboxException("Failed to rename " + oldPath + " -> " + newPath, e);
        }
    }

    public EntryInfo rename(String oldPath, String newPath) {
        return rename(oldPath, newPath, null);
    }

    /**
     * Create a directory (Connect: {@code /filesystem.Filesystem/MakeDir}).
     */
    public MakeDirOutput makeDir(String path, String user) {
        StringBuilder json = new StringBuilder("{\"path\":").append(q(path));
        if (user != null) json.append(",\"user\":").append(q(user));
        json.append("}");

        Request req = buildConnectRequest("/filesystem.Filesystem/MakeDir", json.toString(), user);
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "makeDir");
            // handleError above throws on non-2xx, so success here is always true
            return MakeDirOutput.builder()
                    .created(true)
                    .requestId(ApiResponse.requestIdFrom(resp.headers()))
                    .headers(ApiResponse.headersAsMap(resp.headers()))
                    .build();
        } catch (IOException e) {
            throw new SandboxException("Failed to makeDir: " + path, e);
        }
    }

    public MakeDirOutput makeDir(String path) {
        return makeDir(path, null);
    }

    /**
     * Get a download URL for a file (envd {@code /files}).
     */
    public String downloadUrl(String path, String user) {
        return buildFileUrl(path, user).toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EntryInfo extractEntry(String body) throws IOException {
        JsonNode root = api.mapper.readTree(body);
        JsonNode entry = root.get("entry");
        return api.mapper.treeToValue(entry != null ? entry : root, EntryInfo.class);
    }

    /** Relies on Lombok {@code @Data} setters on {@link WriteInfo} (not builder-only). */
    private static <T extends WriteInfo> T attachMeta(T info, Headers headers) {
        if (info != null) {
            info.setRequestId(ApiResponse.requestIdFrom(headers));
            info.setHeaders(ApiResponse.headersAsMap(headers));
        }
        return info;
    }

    private HttpUrl buildFileUrl(String path, String user) {
        HttpUrl.Builder b = HttpUrl.parse(envdUrl + FILES_ROUTE).newBuilder()
                .addQueryParameter("path", path);
        if (user != null) b.addQueryParameter("username", user);
        return b.build();
    }

    private Request buildConnectRequest(String rpcPath, String jsonBody, String user) {
        Request.Builder builder = new Request.Builder()
                .url(envdUrl + rpcPath)
                .post(RequestBody.create(jsonBody, JSON))
                .header("Connect-Protocol-Version", "1")
                .header("X-Access-Token", tok());
        if (user != null && !user.isEmpty()) {
            String basic = java.util.Base64.getEncoder()
                    .encodeToString((user + ":").getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + basic);
        }
        return builder.build();
    }

    private String tok() {
        return accessToken != null ? accessToken : "";
    }

    private void handleError(Response resp, String op) throws IOException {
        if (!resp.isSuccessful()) {
            String body = resp.body() != null ? resp.body().string() : "";
            throw new SandboxException(
                    "Filesystem." + op + " failed (" + resp.code() + "): " + body,
                    resp.code(),
                    ApiResponse.requestIdFrom(resp.headers()),
                    ApiResponse.headersAsMap(resp.headers()));
        }
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
