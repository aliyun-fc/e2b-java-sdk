package dev.e2b.sdk.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TemplateUpdateResponse {
    private List<String> names;
}
