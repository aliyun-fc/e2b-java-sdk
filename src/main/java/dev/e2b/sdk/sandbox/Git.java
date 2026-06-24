package dev.e2b.sdk.sandbox;

import dev.e2b.sdk.client.E2bApiClient;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.model.*;
import lombok.RequiredArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

/**
 * Git module: perform git operations inside the sandbox.
 */
@RequiredArgsConstructor
public class Git {

    private static final MediaType JSON = MediaType.get("application/json");

    private final Commands commands;
    private final String defaultCwd;

    // -------------------------------------------------------------------------
    // Repository management
    // -------------------------------------------------------------------------

    /**
     * Clone a repository into the sandbox.
     *
     * @param url    Repository URL
     * @param path   Target path (optional)
     * @param branch Branch to checkout (optional)
     * @param depth  Shallow clone depth (optional)
     */
    public CommandResult clone(String url, String path, String branch, Integer depth,
                               String username, String password) {
        StringBuilder cmd = new StringBuilder("git clone");
        if (branch != null) cmd.append(" -b ").append(branch);
        if (depth  != null) cmd.append(" --depth ").append(depth);
        if (username != null && password != null) {
            // Embed credentials in URL
            String credUrl = url.replace("https://", "https://" + username + ":" + password + "@");
            cmd.append(" ").append(credUrl);
        } else {
            cmd.append(" ").append(url);
        }
        if (path != null) cmd.append(" ").append(path);
        return commands.runOrThrow(cmd.toString());
    }

    public CommandResult clone(String url, String path) {
        return clone(url, path, null, null, null, null);
    }

    /**
     * Initialize a new git repository.
     */
    public CommandResult init(String path, boolean bare, String initialBranch) {
        StringBuilder cmd = new StringBuilder("git init");
        if (bare) cmd.append(" --bare");
        if (initialBranch != null) cmd.append(" -b ").append(initialBranch);
        cmd.append(" ").append(path);
        return commands.runOrThrow(cmd.toString());
    }

    /**
     * Get git status for a repo path.
     */
    public GitStatus status(String path) {
        CommandResult result = commands.run("git -C " + path + " status --porcelain=v2 --branch");
        return parseGitStatus(result.getStdout());
    }

    /**
     * List branches in a repo.
     */
    public GitBranches branches(String path) {
        CommandResult result = commands.run("git -C " + path + " branch --all");
        return parseGitBranches(result.getStdout());
    }

    /**
     * Stage files.
     *
     * @param path  Repo path
     * @param files Specific files to stage (null = stage all)
     * @param all   If true, stage all changes including deletions
     */
    public CommandResult add(String path, java.util.List<String> files, boolean all) {
        if (files != null && !files.isEmpty()) {
            return commands.runOrThrow("git -C " + path + " add " + String.join(" ", files));
        }
        return commands.runOrThrow("git -C " + path + " add " + (all ? "-A" : "."));
    }

    public CommandResult add(String path) {
        return add(path, null, true);
    }

    /**
     * Create a commit.
     *
     * @param path         Repo path
     * @param message      Commit message
     * @param authorName   Optional author name
     * @param authorEmail  Optional author email
     * @param allowEmpty   Allow empty commits
     */
    public CommandResult commit(String path, String message, String authorName,
                                String authorEmail, boolean allowEmpty) {
        StringBuilder cmd = new StringBuilder("git -C ").append(path);
        if (authorName  != null) cmd.append(" -c user.name=").append(q(authorName));
        if (authorEmail != null) cmd.append(" -c user.email=").append(q(authorEmail));
        cmd.append(" commit -m ").append(q(message));
        if (allowEmpty) cmd.append(" --allow-empty");
        return commands.runOrThrow(cmd.toString());
    }

    public CommandResult commit(String path, String message) {
        return commit(path, message, null, null, false);
    }

    /**
     * Push to a remote.
     */
    public CommandResult push(String path, String remote, String branch,
                              boolean setUpstream, String username, String password) {
        Map<String, String> envs = credentialEnvs(username, password);
        StringBuilder cmd = new StringBuilder("git -C ").append(path).append(" push");
        if (setUpstream) cmd.append(" -u");
        if (remote != null) cmd.append(" ").append(remote);
        if (branch != null) cmd.append(" ").append(branch);
        return commands.run(cmd.toString(), envs, null, null, 120, true);
    }

    public CommandResult push(String path) {
        return push(path, null, null, true, null, null);
    }

    /**
     * Pull from a remote.
     */
    public CommandResult pull(String path, String remote, String branch,
                              String username, String password) {
        Map<String, String> envs = credentialEnvs(username, password);
        StringBuilder cmd = new StringBuilder("git -C ").append(path).append(" pull");
        if (remote != null) cmd.append(" ").append(remote);
        if (branch != null) cmd.append(" ").append(branch);
        return commands.run(cmd.toString(), envs, null, null, 120, true);
    }

    public CommandResult pull(String path) {
        return pull(path, null, null, null, null);
    }

    /** Create a new branch. */
    public CommandResult createBranch(String path, String branch) {
        return commands.runOrThrow("git -C " + path + " branch " + branch);
    }

    /** Checkout a branch. */
    public CommandResult checkoutBranch(String path, String branch) {
        return commands.runOrThrow("git -C " + path + " checkout " + branch);
    }

    /** Delete a branch. */
    public CommandResult deleteBranch(String path, String branch, boolean force) {
        String flag = force ? "-D" : "-d";
        return commands.runOrThrow("git -C " + path + " branch " + flag + " " + branch);
    }

    /** Reset the repo. */
    public CommandResult reset(String path, String mode, String target) {
        String m = mode != null ? "--" + mode : "--mixed";
        String t = target != null ? " " + target : "";
        return commands.runOrThrow("git -C " + path + " reset " + m + t);
    }

    /** Add a remote. */
    public CommandResult remoteAdd(String path, String name, String url, boolean fetch) {
        StringBuilder cmd = new StringBuilder("git -C ").append(path).append(" remote add");
        if (fetch) cmd.append(" -f");
        cmd.append(" ").append(name).append(" ").append(url);
        return commands.runOrThrow(cmd.toString());
    }

    /** Set git config (global or local). */
    public CommandResult setConfig(String key, String value, String scope, String path) {
        String scopeFlag = "local".equals(scope) && path != null ? "-C " + path + " " : "--global ";
        return commands.runOrThrow("git " + scopeFlag + "config " + key + " " + q(value));
    }

    /** Configure user name and email. */
    public CommandResult configureUser(String name, String email, String scope, String path) {
        setConfig("user.name", name, scope, path);
        return setConfig("user.email", email, scope, path);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private GitStatus parseGitStatus(String output) {
        GitStatus status = new GitStatus();
        // Minimal porcelain v2 parser - real implementation would be more complete
        for (String line : output.split("\n")) {
            if (line.startsWith("# branch.head")) {
                status.setCurrentBranch(line.split(" ")[2]);
            } else if (line.startsWith("# branch.upstream")) {
                status.setUpstream(line.split(" ")[2]);
            } else if (line.startsWith("# branch.ab")) {
                String[] parts = line.split(" ");
                status.setAhead(Integer.parseInt(parts[2].substring(1)));
                status.setBehind(Integer.parseInt(parts[3].substring(1)));
            }
        }
        return status;
    }

    private GitBranches parseGitBranches(String output) {
        GitBranches result = new GitBranches();
        java.util.List<String> branches = new java.util.ArrayList<>();
        String current = null;
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("* ")) {
                current = trimmed.substring(2);
                branches.add(current);
            } else if (!trimmed.isEmpty()) {
                branches.add(trimmed);
            }
        }
        result.setBranches(branches);
        result.setCurrentBranch(current);
        return result;
    }

    private Map<String, String> credentialEnvs(String username, String password) {
        if (username == null || password == null) return null;
        Map<String, String> envs = new java.util.HashMap<String, String>();
        envs.put("GIT_USERNAME", username);
        envs.put("GIT_PASSWORD", password);
        return envs;
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
