package dev.e2b.sdk.sandbox;

import dev.e2b.sdk.exception.CommandExitException;
import dev.e2b.sdk.exception.SandboxException;
import dev.e2b.sdk.model.CommandResult;
import okhttp3.Call;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handle to a background command started via {@link Commands#runBackground}.
 *
 * <p>The underlying envd {@code process.Process/Start} RPC is server-streaming: the process keeps
 * running as long as the stream is open. This handle exposes the OS {@code pid} (so the process can
 * be listed / signalled / fed stdin via {@link Commands}) and lets callers await the final
 * {@link CommandResult} or disconnect from the stream.
 */
public class CommandHandle {

    private final int pid;
    private final CompletableFuture<CommandResult> result;
    private final Call call;
    private final String requestId;
    private final Map<String, String> headers;

    CommandHandle(int pid, CompletableFuture<CommandResult> result, Call call,
                  String requestId, Map<String, String> headers) {
        this.pid = pid;
        this.result = result;
        this.call = call;
        this.requestId = requestId;
        this.headers = headers == null ? Collections.emptyMap() : headers;
    }

    /** OS process id of the background command (assigned by envd at start). */
    public int getPid() {
        return pid;
    }

    /** Request id from the Start response headers. */
    public String getRequestId() {
        return requestId;
    }

    /** Full HTTP response headers from the Start call. */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /** True once the process has exited and the final result is available. */
    public boolean isDone() {
        return result.isDone();
    }

    /**
     * Block until the background process exits and return its result.
     *
     * @param throwOnError if true, throw {@link CommandExitException} on non-zero exit code
     */
    public CommandResult waitForExit(boolean throwOnError) {
        CommandResult r = join();
        if (throwOnError && r.getExitCode() != 0) {
            throw new CommandExitException(r);
        }
        return r;
    }

    /** Block (up to {@code timeout}) until the process exits and return its result. */
    public CommandResult waitForExit(long timeout, TimeUnit unit) {
        try {
            return result.get(timeout, unit);
        } catch (TimeoutException e) {
            throw new SandboxException("Timed out waiting for background command (pid=" + pid + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while waiting for background command", e);
        } catch (ExecutionException e) {
            throw asSandboxException(e);
        }
    }

    /**
     * Disconnect from the process stream without waiting for it to finish. Note that cancelling the
     * Connect stream signals envd to terminate the process.
     */
    public void disconnect() {
        if (!call.isCanceled()) {
            call.cancel();
        }
    }

    private CommandResult join() {
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while waiting for background command", e);
        } catch (ExecutionException e) {
            throw asSandboxException(e);
        }
    }

    private SandboxException asSandboxException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SandboxException) {
            return (SandboxException) cause;
        }
        return new SandboxException("Background command failed (pid=" + pid + ")", cause);
    }
}
