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

package stroom.ai.impl;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpToolset {

    private final McpSyncClient client = Mockito.mock(McpSyncClient.class);
    private final McpServerConnection connection = new McpServerConnection("test", null, client, null);
    private final McpToolset toolset = new McpToolset(List.of(connection), List.of(), true);

    @Test
    void progressIsDescribedByWhatItSaysAndHowFarItHasGot() {
        assertThat(describe(2.0, 5.0, "Reading documents")).isEqualTo("Reading documents (2 of 5)");
        assertThat(describe(0.4, 1.0, " Reading documents ")).isEqualTo("Reading documents (40%)");
        assertThat(describe(7.0, 5.0, null)).isEqualTo("7 of 5");
        assertThat(describe(0.5, 0.75, null)).isEqualTo("67%");
        assertThat(describe(9.0, 3.5, null)).isEqualTo("100%");
        // A count with nothing to say what of only means something beside a message.
        assertThat(describe(3.0, null, "Searching")).isEqualTo("Searching");
        assertThat(describe(3.0, null, "")).isNull();
        assertThat(describe(-1.0, 5.0, null)).isNull();
    }

    @Test
    void aCallNobodyIsFollowingIsMadeWithoutAProgressToken() {
        final ArgumentCaptor<CallToolRequest> request = ArgumentCaptor.forClass(CallToolRequest.class);
        Mockito.when(client.callTool(request.capture())).thenReturn(result());

        call("1");

        assertThat(request.getValue().progressToken()).isNull();
        assertThat(request.getValue().arguments()).containsEntry("query", "errors");
    }

    @Test
    void aCallBeingFollowedHearsOnlyItsOwnProgress() {
        final List<String> heard = new ArrayList<>();
        toolset.listenForProgress("1", heard::add);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class))).thenAnswer(invocation -> {
            final Object token = invocation.<CallToolRequest>getArgument(0).progressToken();
            if (token == null) {
                // A server only reports progress for a call given a token.
                return result();
            }
            connection.onProgress(new ProgressNotification(token, 1.0, 2.0, "Halfway", null));
            connection.onProgress(new ProgressNotification("someone else's", 1.0, 2.0, "Not ours", null));
            // Nothing to show.
            connection.onProgress(new ProgressNotification(token, 1.0, null, null, null));
            return result();
        });

        call("1");
        toolset.stopListening("1");
        call("1");

        assertThat(heard).containsExactly("Halfway (1 of 2)");
        final ArgumentCaptor<CallToolRequest> requests = ArgumentCaptor.forClass(CallToolRequest.class);
        Mockito.verify(client, Mockito.times(2)).callTool(requests.capture());
        assertThat(requests.getAllValues().get(0).progressToken()).isNotNull();
        assertThat(requests.getAllValues().get(1).progressToken()).isNull();
    }

    @Test
    void progressThatArrivesAfterNewerProgressIsDropped() {
        // The MCP client passes each notification on in a thread of its own, so they can arrive out of order.
        final List<String> heard = new ArrayList<>();
        toolset.listenForProgress("1", heard::add);
        Mockito.when(client.callTool(Mockito.any(CallToolRequest.class))).thenAnswer(invocation -> {
            final Object token = invocation.<CallToolRequest>getArgument(0).progressToken();
            connection.onProgress(new ProgressNotification(token, 1.0, null, "Searching", null));
            connection.onProgress(new ProgressNotification(token, 3.0, null, "Reading documents", null));
            connection.onProgress(new ProgressNotification(token, 2.0, null, "Sampling", null));
            connection.onProgress(new ProgressNotification(token, 3.0, null, "Reading documents again", null));
            connection.onProgress(new ProgressNotification(token, 4.0, null, "Profiling", null));
            return result();
        });

        call("1");

        assertThat(heard).containsExactly("Searching", "Reading documents", "Profiling");
    }

    @Test
    void eachCallSaysWhichModelIsCalling() {
        // So the server can record the model (e.g. in a version history) rather than relying on the model to say:
        // seen, Qwen 3.8 saying it was claude-opus-4-5.
        final McpToolset withModel = new McpToolset(List.of(connection), List.of(), true,
                " Qwen/Qwen3.8-Flash-Next-FP8 ");
        final ArgumentCaptor<CallToolRequest> request = ArgumentCaptor.forClass(CallToolRequest.class);
        Mockito.when(client.callTool(request.capture())).thenReturn(result());

        call(withModel, "1");
        withModel.listenForProgress("2", text -> {
        });
        call(withModel, "2");

        assertThat(request.getAllValues().get(0).meta())
                .containsExactly(Map.entry(McpToolset.MODEL_META_KEY, "Qwen/Qwen3.8-Flash-Next-FP8"));
        // With the progress token, when there is one.
        assertThat(request.getAllValues().get(1).meta())
                .containsEntry(McpToolset.MODEL_META_KEY, "Qwen/Qwen3.8-Flash-Next-FP8")
                .containsKey("progressToken");
        // Without a model, nothing is said of it.
        call("3");
        assertThat(request.getAllValues().get(2).meta()).isNull();
    }

    private String call(final String callId) {
        return call(toolset, callId);
    }

    private String call(final McpToolset toolset, final String callId) {
        Mockito.when(client.listTools()).thenReturn(new ListToolsResult(List.of(Tool.builder()
                .name("search")
                .description("run a search")
                .inputSchema(Map.of("type", "object"))
                .build()), null));
        final Map<ToolSpecification, ToolExecutor> tools = toolset.tools(Set.of());
        final ToolExecutor executor = tools.entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("search"))
                .findFirst()
                .orElseThrow()
                .getValue();
        return executor.execute(ToolExecutionRequest.builder()
                .id(callId)
                .name("search")
                .arguments("{\"query\": \"errors\"}")
                .build(), null);
    }

    private static CallToolResult result() {
        return CallToolResult.builder().content(List.of(new TextContent("42 errors"))).isError(false).build();
    }

    private static String describe(final Double progress, final Double total, final String message) {
        return McpToolset.describeProgress(new ProgressNotification("token", progress, total, message, null));
    }
}
