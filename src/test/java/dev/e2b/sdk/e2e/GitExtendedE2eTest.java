package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.GitBranches;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J16 supplement: additional Git module APIs (init, branches, checkout).
 */
class GitExtendedE2eTest extends E2eTestBase {

    @Test
    void initCommitBranchWorkflow() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            sandbox.getGit().init("/tmp/local-repo", false, "main");
            sandbox.getGit().configureUser("E2B Verify", "test@example.com", "local", "/tmp/local-repo");
            sandbox.getFiles().write("/tmp/local-repo/README.md", "local repo\n");
            sandbox.getGit().add("/tmp/local-repo");
            sandbox.getGit().commit("/tmp/local-repo", "Initial commit");

            sandbox.getGit().createBranch("/tmp/local-repo", "feature/e2e");
            sandbox.getGit().checkoutBranch("/tmp/local-repo", "feature/e2e");
            sandbox.getFiles().write("/tmp/local-repo/feature.txt", "feature\n");
            sandbox.getGit().add("/tmp/local-repo");
            sandbox.getGit().commit("/tmp/local-repo", "Feature commit");

            GitBranches branches = sandbox.getGit().branches("/tmp/local-repo");
            assertNotNull(branches.getCurrentBranch());
            assertTrue(branches.getBranches().contains("feature/e2e"));

            CommandResult log = sandbox.getCommands().run("git -C /tmp/local-repo log --oneline");
            assertEquals(0, log.getExitCode());
            assertTrue(log.getStdout().contains("Feature commit"));
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
