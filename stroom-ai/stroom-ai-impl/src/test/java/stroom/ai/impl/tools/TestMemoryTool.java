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

import stroom.ai.impl.memory.AiMemoryService;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMemoryTool {

    private final AiMemoryService memoryService = Mockito.mock(AiMemoryService.class);
    private final MemoryTool tool = new MemoryTool(memoryService);

    @Test
    void testCommands() {
        tool.execute(request("{\"command\":\"list\"}"), 3);
        Mockito.verify(memoryService).list(3);

        tool.execute(request("{\"command\":\"read\",\"path\":\"/memories/a\"}"), 3);
        Mockito.verify(memoryService).read(3, "/memories/a");

        // Content is passed exactly as given, not trimmed.
        tool.execute(request("{\"command\":\"write\",\"path\":\"/memories/a\",\"content\":\" x \\n\","
                             + "\"version\":2}"), 3);
        Mockito.verify(memoryService).write(3, "/memories/a", " x \n", 2);

        tool.execute(request("{\"command\":\"append\",\"path\":\"/memories/a\",\"content\":\"y\"}"), 3);
        Mockito.verify(memoryService).append(3, "/memories/a", "y");

        tool.execute(request("{\"command\":\"replace\",\"path\":\"/memories/a\",\"oldText\":\"y\","
                             + "\"newText\":\"\"}"), 3);
        Mockito.verify(memoryService).replace(3, "/memories/a", "y", "");

        tool.execute(request("{\"command\":\"delete\",\"path\":\"/memories/a\",\"version\":4}"), 3);
        Mockito.verify(memoryService).delete(3, "/memories/a", 4);

        assertThat(tool.execute(request("{\"command\":\"rm\"}"), 3)).startsWith("Error:");
        assertThat(tool.execute(request("{}"), 3)).startsWith("Error:");
        assertThat(tool.execute(request("{\"command\":\"list\"}"))).startsWith("Error:");
    }

    @Test
    void testTaint() {
        final BuiltInTools tools = new BuiltInTools(Set.of(tool, new GetCurrentDateTool(),
                new ReadTurnTool(null)));

        assertThat(tools.readsUntrustedContent(MemoryTool.NAME)).isFalse();
        assertThat(tools.readsUntrustedContent("get_current_date")).isFalse();
        // Earlier turns hold tool output.
        assertThat(tools.readsUntrustedContent(ReadTurnTool.NAME)).isTrue();
        // Anything else, e.g. an MCP tool.
        assertThat(tools.readsUntrustedContent("stroom__find_documents")).isTrue();
    }

    private static ToolExecutionRequest request(final String arguments) {
        return ToolExecutionRequest.builder().id("x").name(MemoryTool.NAME).arguments(arguments).build();
    }
}
