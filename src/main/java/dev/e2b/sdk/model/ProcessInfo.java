package dev.e2b.sdk.model;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
@Data
@NoArgsConstructor
public class ProcessInfo {
    private int pid;
    private String tag;
    private String cmd;
    private List<String> args;
    private Map<String, String> envs;
    private String cwd;
}
