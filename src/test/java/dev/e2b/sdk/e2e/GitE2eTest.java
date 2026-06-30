package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.GitStatus;
import dev.e2b.sdk.model.NewSandbox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J16: Git module clone/add/commit workflow.
 */
class GitE2eTest extends E2eTestBase {

    @Test
    void cloneAddCommitAndCleanStatus() {
        Sandbox sandbox = Sandbox.create(
                config.getTemplate(),
                config.toConnectionConfig(),
                NewSandbox.builder().timeout(300).allowInternetAccess(true).build());
        try {
            CommandResult cloned = sandbox.getGit().clone(config.gitRepoUrl(), "/tmp/git-repo", null, 1, null, null);
            assertEquals(0, cloned.getExitCode(), cloned.getStderr());

            sandbox.getFiles().write("/tmp/git-repo/e2e-verify.txt", "verify\n");
            sandbox.getGit().configureUser("E2B Verify", "test@example.com", "local", "/tmp/git-repo");
            sandbox.getGit().add("/tmp/git-repo");
            CommandResult committed = sandbox.getGit().commit("/tmp/git-repo", "Add verify file");
            assertEquals(0, committed.getExitCode(), committed.getStderr());

            GitStatus status = sandbox.getGit().status("/tmp/git-repo");
            assertTrue(status.isClean(), "repository should be clean after commit");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
