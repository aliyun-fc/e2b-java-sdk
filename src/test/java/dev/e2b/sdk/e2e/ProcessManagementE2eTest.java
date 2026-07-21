package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.CommandResult;
import dev.e2b.sdk.model.ProcessInfo;
import dev.e2b.sdk.sandbox.CommandHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J06: background process lifecycle — start (tracked), list, sendStdin, kill.
 *
 * <p>Processes must be started via {@link dev.e2b.sdk.sandbox.Commands#runBackground} so envd tracks
 * them and exposes them through {@code Process/List} / {@code Process/SendInput} /
 * {@code Process/SendSignal}. Shell {@code &} background jobs are NOT envd-tracked, so they cannot be
 * listed or signalled by pid.
 */
class ProcessManagementE2eTest extends E2eTestBase {

    @Test
    void listKillAndSendStdin() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            // A tracked background reader that appends every stdin line to a file.
            CommandHandle handle = sandbox.getCommands().runBackground(
                    "python3 -u -c \"import sys\nf=open('/tmp/stdin-out.txt','w')\nfor line in sys.stdin:\n    f.write(line); f.flush()\"");
            int pid = handle.getPid();
            assertTrue(pid > 0, "background process should report a pid");

            E2eSupport.sleepMillis(500);

            List<ProcessInfo> processes = sandbox.getCommands().list().getProcesses();
            assertFalse(processes.isEmpty(), "process list should not be empty");
            assertTrue(processes.stream().anyMatch(p -> p.getPid() == pid),
                    "started background process should appear in list()");

            sandbox.getCommands().sendStdin(pid, "stdin-payload\n");
            E2eSupport.sleepMillis(1000);

            String content = sandbox.getFiles().read("/tmp/stdin-out.txt").getText();
            assertEquals("stdin-payload", content.trim());

            assertTrue(sandbox.getCommands().kill(pid).isKilled(), "kill() should succeed for a tracked process");
            handle.waitForExit(10, TimeUnit.SECONDS);
            E2eSupport.sleepMillis(300);

            List<ProcessInfo> afterKill = sandbox.getCommands().list().getProcesses();
            assertFalse(afterKill.stream().anyMatch(p -> p.getPid() == pid),
                    "killed process should no longer be listed");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void backgroundSleepProcessCanBeKilled() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            CommandHandle handle = sandbox.getCommands().runBackground("sleep 300");
            int pid = handle.getPid();
            assertTrue(pid > 0);

            E2eSupport.sleepMillis(300);
            assertTrue(sandbox.getCommands().list().getProcesses().stream().anyMatch(p -> p.getPid() == pid),
                    "sleep process should be tracked");

            assertTrue(sandbox.getCommands().kill(pid).isKilled(), "kill() should succeed");

            // The process stream completes once envd reports the kill.
            CommandResult result = handle.waitForExit(10, TimeUnit.SECONDS);
            assertNotEquals(0, result.getExitCode(), "killed process should exit non-zero");
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
