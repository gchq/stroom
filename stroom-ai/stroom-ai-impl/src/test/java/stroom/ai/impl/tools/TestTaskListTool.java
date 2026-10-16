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

package stroom.ai.impl.tools;

import stroom.ai.api.AiService;
import stroom.ai.shared.AiMessageType;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestTaskListTool {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final TaskListTool tool = new TaskListTool(aiService);

    @Test
    void theListIsKeptForTheUserToSee() {
        final String result = run("""
                {"tasks":[{"title":"Find the feed","status":"completed"},
                          {"title":"Check  its\\npipeline","status":"in_progress"},
                          {"title":"Fix the XSLT","status":"not_started"}]}""");

        assertThat(result).isEqualTo("Task list updated: 1 of 3 done. In progress: Check its pipeline.");
        Mockito.verify(aiService).storeMessage(7, AiMessageType.TASK_LIST,
                "{\"tasks\":[{\"title\":\"Find the feed\",\"status\":\"completed\"},"
                + "{\"title\":\"Check its pipeline\",\"status\":\"in_progress\"},"
                + "{\"title\":\"Fix the XSLT\",\"status\":\"not_started\"}]}");
    }

    @Test
    void badListsAreRefusedAndNotKept() {
        assertThat(run("{\"tasks\":[]}")).startsWith("Error");
        assertThat(run("{\"tasks\":[{\"title\":\"x\",\"status\":\"done\"}]}")).startsWith("Error")
                .contains("in_progress");
        assertThat(run("{\"tasks\":[{\"status\":\"completed\"}]}")).startsWith("Error").contains("title");
        assertThat(run("{\"tasks\":\"not a list\"}")).startsWith("Error");
        assertThat(run("{\"tasks\":[" + "{\"title\":\"x\",\"status\":\"completed\"},".repeat(20)
                       + "{\"title\":\"x\",\"status\":\"completed\"}]}")).contains("at most 20");
        Mockito.verifyNoInteractions(aiService);
    }

    @Test
    void itIsOfferedInEveryModeAsItChangesNothing() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.readsUntrustedContent()).isFalse();
    }

    private String run(final String json) {
        return tool.execute(ToolExecutionRequest.builder().id("x").name("t").arguments(json).build(), 7);
    }
}
