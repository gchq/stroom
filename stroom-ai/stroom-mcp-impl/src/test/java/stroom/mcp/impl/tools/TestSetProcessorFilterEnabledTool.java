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

import stroom.ai.impl.tools.ToolResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

/// set_processor_filter_enabled: it only ever disables, and only a filter on a pipeline in a build.
class TestSetProcessorFilterEnabledTool {

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
    }

    @Test
    void aFilterOnABuildsPipelineIsDisabled() {
        w.pipeline(ProcessingWorld.P1, true, null);
        w.idFilter("p1", 775, 6);

        final Map<String, Object> result = ProcessingWorld.json(call("{\"filter_id\": 775, \"enabled\": false}"));

        assertThat(result).isEqualTo(Map.of("filter_id", 775, "enabled", false));
        Mockito.verify(w.filterService).setEnabled(775, false);
    }

    @Test
    void enablingIsRefusedAsTheUsers() {
        // Asked for by the user: the server enables no filter, bar reprocessing in a build that the user approves.
        w.pipeline(ProcessingWorld.P1, true, null);
        w.idFilter("p1", 775, 6);

        final ToolResult result = call("{\"filter_id\": 775, \"enabled\": true}");

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("Enabling a processor filter is the user's: ask them to enable filter 775 "
                                           + "on the Processors tab of pipeline 'Acme'")
                .contains("docUuid=p1")
                .contains("then wait_for_processing");
        Mockito.verify(w.filterService, Mockito.never()).setEnabled(anyInt(), anyBoolean());
    }

    @Test
    void aFilterOnAPipelineOutsideABuildIsRefused() {
        w.pipeline(ProcessingWorld.P1, false, null, WriteGuard.GENERATED);
        w.idFilter("p1", 775, 6);

        final ToolResult result = call("{\"filter_id\": 775}");

        assertThat(result.text()).contains("was not created by this server");
        Mockito.verify(w.filterService, Mockito.never()).setEnabled(anyInt(), anyBoolean());
    }

    @Test
    void aFilterThatIsNotThereIsRefused() {
        final ToolResult result = call("{\"filter_id\": 12}");

        assertThat(result.text()).contains("No processor filter 12 was found");
    }

    private ToolResult call(final String arguments) {
        return w.setEnabledTool().execute(BuildToolCalls.call(SetProcessorFilterEnabledTool.NAME, arguments),
                ProcessingWorld.context());
    }
}
