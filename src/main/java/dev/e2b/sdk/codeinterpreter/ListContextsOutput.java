package dev.e2b.sdk.codeinterpreter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListContextsOutput {

    @Builder.Default
    private List<Context> contexts = Collections.emptyList();

    private String requestId;

    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
}
