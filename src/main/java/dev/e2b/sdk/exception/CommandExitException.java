package dev.e2b.sdk.exception;
import dev.e2b.sdk.model.CommandResult;
import lombok.Getter;
@Getter
public class CommandExitException extends SandboxException {
    private final CommandResult result;
    public CommandExitException(CommandResult result) {
        super("Command exited with code " + result.getExitCode() + ": " + result.getStderr());
        this.result = result;
    }
}
