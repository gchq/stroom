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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// What the processing tools ask the user (the Stroom MCP server's `tests/test_processing.py`, its case for
/// `_committed`).
class TestProcessingApprovals {

    @Test
    void theUserCanGoOnWithAnIndexTemplateOfTheirOwn() {
        // Seen in testing: the user had committed their template, and the agreement still stopped indexing, however
        // they said so. They confirm it in a form (the agent's word for it isn't theirs), and it is kept with the
        // pipeline.
        final ProcessingWorld w = new ProcessingWorld();
        final PipelineDoc doc = w.pipeline(ProcessingWorld.P1, true, "Indexes Acme.");
        w.elastic("p1");
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("index name", "ecs-acme-v1");
        destination.put("cluster", "ES_PROD");
        final ToolCall call = PortedToolCalls.call(CreateProcessorFilterTool.NAME, "{}");
        final ToolCallContext context = ProcessingWorld.context();

        assertThatThrownBy(() -> w.approvals.committed(call, context, doc, destination,
                CreateProcessorFilterTool.NAME, false, null))
                .hasMessageContaining("template_committed_by_user=true");
        final ProcessingApprovals.Committed asked = w.approvals.committed(call, context, doc, destination,
                CreateProcessorFilterTool.NAME, true, null);
        assertThat(asked.round().get("status")).isEqualTo("needs_confirmation");
        assertThat(asked.round().get("details").toString()).contains("not checked");
        assertThat(w.pipelineDoc("p1").getDescription()).isEqualTo("Indexes Acme.");

        final ProcessingApprovals.Committed said = w.approvals.committed(call, context, doc, destination,
                CreateProcessorFilterTool.NAME, true, String.valueOf(asked.round().get("confirmation_id")));

        final Map<String, Object> kept = KeptMapping.readAgreedTemplate(w.pipelineDoc("p1").getDescription())
                .orElseThrow();
        assertThat(kept.get("by_user")).isEqualTo(true);
        assertThat(kept.get("index")).isEqualTo("ecs-acme-v1");
        assertThat(kept.get("xslt")).isEqualTo(XsltCode.digest());
        assertThat(said.text()).contains("the user's own index template");
        assertThat(w.pipelineDoc("p1").getDescription()).startsWith("Indexes Acme.");
        // Kept: the approval that follows, and later calls, aren't stopped again.
        assertThat(w.approvals.committed(call, context, said.pipeline(), destination, ReprocessStreamsTool.NAME,
                false, null).text()).contains("the user's own");
    }
}
