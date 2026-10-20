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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMemoryTool {

    private final AiMemoryService memoryService = Mockito.mock(AiMemoryService.class);
    private final MemoryTool tool = new MemoryTool(memoryService);

    @Test
    void testCommands() {
        tool.execute(request("{\"command\":\"list\"}"), ToolCalls.inChat(3));
        Mockito.verify(memoryService).list(3);

        tool.execute(request("{\"command\":\"read\",\"path\":\"/memories/a\"}"), ToolCalls.inChat(3));
        Mockito.verify(memoryService).read(3, "/memories/a");

        // Content is passed exactly as given, not trimmed.
        tool.execute(request("{\"command\":\"write\",\"path\":\"/memories/a\",\"content\":\" x \\n\","
                             + "\"version\":2}"), ToolCalls.inChat(3));
        Mockito.verify(memoryService).write(3, "/memories/a", " x \n", 2);

        tool.execute(request("{\"command\":\"append\",\"path\":\"/memories/a\",\"content\":\"y\"}"),
                ToolCalls.inChat(3));
        Mockito.verify(memoryService).append(3, "/memories/a", "y");

        tool.execute(request("{\"command\":\"replace\",\"path\":\"/memories/a\",\"oldText\":\"y\","
                             + "\"newText\":\"\"}"), ToolCalls.inChat(3));
        Mockito.verify(memoryService).replace(3, "/memories/a", "y", "");

        tool.execute(request("{\"command\":\"delete\",\"path\":\"/memories/a\",\"version\":4}"), ToolCalls.inChat(3));
        Mockito.verify(memoryService).delete(3, "/memories/a", 4);

        assertThat(tool.execute(request("{\"command\":\"rm\"}"), ToolCalls.inChat(3)).text()).startsWith("Error:");
        assertThat(tool.execute(request("{}"), ToolCalls.inChat(3)).text()).startsWith("Error:");
        assertThat(tool.execute(request("{\"command\":\"list\"}"), ToolCalls.outsideChat()).text())
                .startsWith("Error:");
    }

    @Test
    void testTaint() {
        final BuiltInTools tools = new BuiltInTools(Set.of(tool, new GetCurrentDateTool(),
                new ReadTurnTool(null)), ToolCalls.chatContexts());

        assertThat(tools.readsUntrustedContent(MemoryTool.NAME)).isFalse();
        assertThat(tools.readsUntrustedContent("get_current_date")).isFalse();
        // Earlier turns hold tool output.
        assertThat(tools.readsUntrustedContent(ReadTurnTool.NAME)).isTrue();
        // Anything else, e.g. an MCP tool.
        assertThat(tools.readsUntrustedContent("stroom__find_documents")).isTrue();
    }

    @Test
    void onlyListAndReadAreReadOnlyCalls() {
        // While the user is away only calls that only read run (ToolConsent), so a change must not pass as one.
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.isReadOnly(request("{\"command\":\"list\"}"))).isTrue();
        assertThat(tool.isReadOnly(request("{\"command\":\"read\",\"path\":\"/memories/a\"}"))).isTrue();
        for (final String command : List.of("write", "append", "replace", "delete")) {
            assertThat(tool.isReadOnly(request("{\"command\":\"" + command + "\"}"))).as(command).isFalse();
        }
        // A command that can't be read is treated as a change.
        assertThat(tool.isReadOnly(request("{}"))).isFalse();
        assertThat(tool.isReadOnly(request("not json"))).isFalse();
        assertThat(tool.isReadOnly(request(null))).isFalse();
    }

    private static ToolCall request(final String arguments) {
        return new ToolCall("x", MemoryTool.NAME, arguments);
    }
}
