package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Template;
import dev.e2b.sdk.model.TemplateInfo;
import dev.e2b.sdk.model.TemplateWithBuilds;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J23: Template list/get against live control plane.
 */
class TemplateE2eTest extends E2eTestBase {

    @Test
    void listAndGetTemplate() {
        List<TemplateInfo> templates = Template.list(config.toConnectionConfig());
        assertNotNull(templates);
        assertFalse(templates.isEmpty(), "template list should not be empty");
        for (TemplateInfo t : templates) {
            assertNotNull(t.getTemplateId(), "every listed template must carry a templateID");
        }

        TemplateWithBuilds template = Template.get(config.getTemplate(), config.toConnectionConfig());
        assertNotNull(template.getTemplateId());
        assertFalse(template.getTemplateId().trim().isEmpty());
        assertNotNull(template.getBuilds());
        assertNotNull(template.getNames());
    }
}
