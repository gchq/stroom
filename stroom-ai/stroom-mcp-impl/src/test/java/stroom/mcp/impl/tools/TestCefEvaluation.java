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

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;

/// evaluate_pipeline's `cef` section: a CEF pipeline's stepped lines, reviewed.
class TestCefEvaluation {

    private static final String LINE = "CEF:0|TODO Device Vendor: the sample's Events have no System/Organisation"
                                       + "|Acme|1.0|Logon|User logon|3|suser=alice";

    private final DraftCefMappingTool cef = Mockito.mock(DraftCefMappingTool.class);
    private final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    private final CefEvaluation evaluation = new CefEvaluation(cef, keptMappings);

    TestCefEvaluation() {
        Mockito.when(keptMappings.keptXslt("p")).thenReturn(Optional.empty());
    }

    @Test
    void itsLinesAreReviewedWithAHeaderFieldStillTodoAndKafkaDeliveryNoted() {
        Mockito.when(cef.stepped(any(), Mockito.eq("p"), Mockito.eq(List.of(7L)), any(), anyInt()))
                .thenReturn(new DraftCefMappingTool.Stepped(List.of(LINE), List.of(), "cefFilter",
                        Map.of("output", "kafka", "topic", "arcsight"), false));

        final Map<String, Object> section = evaluation.cef(BuildToolCalls.context(), "p", List.of(7L), "kafka");

        assertThat(section).containsEntry("status", "checked").containsEntry("lines_stepped", 1)
                .containsEntry("output", Map.of("output", "kafka", "topic", "arcsight"));
        assertThat(section.get("header_todo")).isEqualTo(List.of(Map.of("field", "Device Vendor", "value",
                "TODO Device Vendor: the sample's Events have no System/Organisation")));
        assertThat((String) section.get("custom_keys")).startsWith("not known");
        assertThat(section).containsKeys("problems", "not_sent", "implied_mapping");
        assertThat(section.get("notes").toString()).contains("delivery to Kafka");
    }

    @Test
    void withNothingToStepOrNoStepItSaysWhy() {
        assertThat(evaluation.cef(BuildToolCalls.context(), "p", List.of(), null))
                .containsEntry("status", "not_checked");
        Mockito.verifyNoInteractions(cef);

        Mockito.when(cef.stepped(any(), any(), anyList(), any(), anyInt()))
                .thenThrow(new ToolRefusal("You don't have permission to step pipelines (Stepping)"));
        assertThat(evaluation.cef(BuildToolCalls.context(), "p", List.of(7L), null))
                .containsEntry("status", "unavailable")
                .containsEntry("reason", "You don't have permission to step pipelines (Stepping)");

        Mockito.reset(cef);
        Mockito.when(cef.stepped(any(), any(), anyList(), any(), anyInt()))
                .thenReturn(new DraftCefMappingTool.Stepped(List.of(), List.of(), "cefFilter", Map.of(), false));
        assertThat(evaluation.cef(BuildToolCalls.context(), "p", List.of(7L), null))
                .containsEntry("status", "no_lines");
    }

    @Test
    void onlyHeaderFieldsHoldingTodoAreReported() {
        assertThat(CefEvaluation.headerTodo("CEF:0|Acme|Gate|1.0|Logon|User logon|3|suser=alice")).isEmpty();
        assertThat(CefEvaluation.headerTodo("CEF:0|Acme|Gate|TODO version|Logon|User logon|3|x=y"))
                .extracting(entry -> entry.get("field")).containsExactly("Device Version");
    }
}
