/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.shared.AiToolCategory;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestFindPipelineTemplatesTool {

    private final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
    private final FindPipelineTemplatesTool tool = new FindPipelineTemplatesTool(templates);

    @Test
    void testTheStageIsSearchedFor() {
        Mockito.when(templates.find("reference")).thenReturn(Map.of("stage", "reference", "candidates", List.of()));
        final ToolResult result = call("{\"stage\": \"reference\"}");
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).containsEntry("stage", "reference");
        assertThat(result.text()).contains("\"candidates\":[]");
    }

    @Test
    void testAStageThatIsntOneIsRefused() {
        final ToolResult result = call("{\"stage\": \"cooking\"}");
        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("translation, indexing, discovery, reference, records, forwarding");
        Mockito.verifyNoInteractions(templates);
    }

    @Test
    void testTheToolOnlyReads() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.category()).isEqualTo(AiToolCategory.CONTENT);
        assertThat(tool.definition().name()).isEqualTo("find_pipeline_templates");
    }

    private ToolResult call(final String arguments) {
        return tool.execute(new ToolCall("1", FindPipelineTemplatesTool.NAME, arguments),
                ToolCallContext.builder(ToolSession.chat(1)).build());
    }
}
