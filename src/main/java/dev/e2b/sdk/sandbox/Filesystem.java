package dev.e2b.sdk.sandbox;

import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.model.EntryInfo;
import dev.e2b.sdk.model.FilesystemEvent;
import dev.e2b.sdk.model.WriteInfo;
import lombok.RequiredArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Filesystem module: read, write, list, move, watch files inside the sandbox.
 */
@RequiredArgsConstructor
public class Filesystem {

    private static final MediaType OCTET = MediaType.get("application/octet-stream");
    private static final MediaType JSON  = MediaType.get("application/json");

    private final E2bApiClient api;
    private final String sandboxId;
    private final String envdUrl;
    private final String accessToken;

    /**
     * Read a file as a UTF-8 string.
     *
     * @param path Path inside the sandbox
     * @param user Optional username (default: "user")
     * @return File contents
     */
    public String read(String path, String user) {
        byte[] raw = readBytes(path, user);
        return new String(raw, StandardCharsets.UTF_8);
    }

    public String read(String path) {
        return read(path, null);
    }

    /**
     * Read a file as raw bytes.
     */
    public byte[] readBytes(String path, String user) {
        HttpUrl url = buildFileUrl(path, user);
        Request req = new Request.Builder()
                .url(url)
                .get()
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "read");
            return resp.body() != null ? resp.body().bytes() : new byte[0];
        } catch (IOException e) {
            throw new SandboxException("Failed to read file: " + path, e);
        }
    }

    /**
     * Write a string to a file (creates parent directories automatically).
     *
     * @param path     Path inside the sandbox
     * @param content  File content
     * @param user     Optional username
     * @param metadata Optional metadata headers
     * @return WriteInfo for the created/updated file
     */
    public WriteInfo write(String path, String content, String user, Map<String, String> metadata) {
        return writeBytes(path, content.getBytes(StandardCharsets.UTF_8), user, metadata);
    }

    public WriteInfo write(String path, String content) {
        return write(path, content, null, null);
    }

    /**
     * Write raw bytes to a file.
     */
    public WriteInfo writeBytes(String path, byte[] data, String user, Map<String, String> metadata) {
        HttpUrl url = buildFileUrl(path, user);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(data, OCTET))
                .header("X-Access-Token", tok());
        if (metadata != null) {
            metadata.forEach((k, v) -> builder.header("X-Metadata-" + k, v));
        }
        try (Response resp = api.httpClient().newCall(builder.build()).execute()) {
            handleError(resp, "write");
            String body = resp.body() != null ? resp.body().string() : "{}";
            return api.mapper.readValue(body, WriteInfo.class);
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
     * List directory contents.
     *
     * @param path  Directory path
     * @param depth Recursion depth (default: 1)
     * @param user  Optional username
     * @return List of EntryInfo
     */
    public List<EntryInfo> list(String path, Integer depth, String user) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(envdUrl + "/api/filesystem/list").newBuilder()
                .addQueryParameter("path", path);
        if (depth != null)  urlBuilder.addQueryParameter("depth", String.valueOf(depth));
        if (user  != null)  urlBuilder.addQueryParameter("username", user);

        Request req = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "list");
            String body = resp.body() != null ? resp.body().string() : "[]";
            EntryInfo[] arr = api.mapper.readValue(body, EntryInfo[].class);
            return Arrays.asList(arr);
        } catch (IOException e) {
            throw new SandboxException("Failed to list directory: " + path, e);
        }
    }

    public List<EntryInfo> list(String path) {
        return list(path, 1, null);
    }

    /**
     * Check if a path exists.
     */
    public boolean exists(String path, String user) {
        try {
            getInfo(path, user);
            return true;
        } catch (SandboxException e) {
            return false;
        }
    }

    public boolean exists(String path) {
        return exists(path, null);
    }

    /**
     * Get metadata (stat) for a file or directory.
     */
    public EntryInfo getInfo(String path, String user) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(envdUrl + "/api/filesystem/stat").newBuilder()
                .addQueryParameter("path", path);
        if (user != null) urlBuilder.addQueryParameter("username", user);

        Request req = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "getInfo");
            String body = resp.body() != null ? resp.body().string() : "{}";
            return api.mapper.readValue(body, EntryInfo.class);
        } catch (IOException e) {
            throw new SandboxException("Failed to stat: " + path, e);
        }
    }

    public EntryInfo getInfo(String path) {
        return getInfo(path, null);
    }

    /**
     * Remove a file or directory.
     */
    public void remove(String path, String user) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(envdUrl + "/api/filesystem/remove").newBuilder()
                .addQueryParameter("path", path);
        if (user != null) urlBuilder.addQueryParameter("username", user);

        Request req = new Request.Builder()
                .url(urlBuilder.build())
                .delete()
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "remove");
        } catch (IOException e) {
            throw new SandboxException("Failed to remove: " + path, e);
        }
    }

    public void remove(String path) {
        remove(path, null);
    }

    /**
     * Rename / move a path.
     *
     * @param oldPath Source path
     * @param newPath Destination path
     * @param user    Optional username
     * @return EntryInfo of the moved path
     */
    public EntryInfo rename(String oldPath, String newPath, String user) {
        String json = "{\"old_path\":" + q(oldPath) + ",\"new_path\":" + q(newPath) +
                (user != null ? ",\"username\":" + q(user) : "") + "}";
        Request req = new Request.Builder()
                .url(envdUrl + "/api/filesystem/move")
                .post(RequestBody.create(json, JSON))
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "rename");
            String body = resp.body() != null ? resp.body().string() : "{}";
            return api.mapper.readValue(body, EntryInfo.class);
        } catch (IOException e) {
            throw new SandboxException("Failed to rename " + oldPath + " -> " + newPath, e);
        }
    }

    public EntryInfo rename(String oldPath, String newPath) {
        return rename(oldPath, newPath, null);
    }

    /**
     * Create a directory (and parents).
     *
     * @return true if created, false if already existed
     */
    public boolean makeDir(String path, String user) {
        String json = "{\"path\":" + q(path) +
                (user != null ? ",\"username\":" + q(user) : "") + "}";
        Request req = new Request.Builder()
                .url(envdUrl + "/api/filesystem/mkdir")
                .post(RequestBody.create(json, JSON))
                .header("X-Access-Token", tok())
                .build();
        try (Response resp = api.httpClient().newCall(req).execute()) {
            handleError(resp, "makeDir");
            return resp.code() == 201;
        } catch (IOException e) {
            throw new SandboxException("Failed to makeDir: " + path, e);
        }
    }

    public boolean makeDir(String path) {
        return makeDir(path, null);
    }

    /**
     * Get a pre-signed download URL for a file.
     *
     * @param path Path inside sandbox
     * @param user Optional username
     * @return Download URL
     */
    public String downloadUrl(String path, String user) {
        HttpUrl url = buildFileUrl(path, user);
        return url.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HttpUrl buildFileUrl(String path, String user) {
        HttpUrl.Builder b = HttpUrl.parse(envdUrl + "/api/files").newBuilder()
                .addQueryParameter("path", path);
        if (user != null) b.addQueryParameter("username", user);
        return b.build();
    }

    private String tok() {
        return accessToken != null ? accessToken : "";
    }

    private void handleError(Response resp, String op) throws IOException {
        if (!resp.isSuccessful()) {
            String body = resp.body() != null ? resp.body().string() : "";
            throw new SandboxException("Filesystem." + op + " failed (" + resp.code() + "): " + body);
        }
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
