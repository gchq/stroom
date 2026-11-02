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

import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptArgument;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ListPromptsResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpPrompts {

    @Test
    void promptsBecomeSlashCommands() {
        final List<McpToolset.ServerPrompt> prompts = List.of(
                new McpToolset.ServerPrompt("fastmcp", prompt("onboard_data_source",
                        new PromptArgument("feed", "The feed", true))),
                new McpToolset.ServerPrompt("fastmcp", prompt("Fix Pipeline")),
                new McpToolset.ServerPrompt("fastmcp", prompt("compact")),
                new McpToolset.ServerPrompt("other", prompt("onboard_data_source")));

        final List<AiMcpPrompt> commands = McpPrompts.commands(prompts);

        assertThat(commands).extracting(AiMcpPrompt::getCommand).containsExactly(
                "/fastmcp.onboard_data_source",
                "/fix_pipeline",
                "/fastmcp.compact",
                "/other.onboard_data_source");
        assertThat(commands.getFirst().getArguments())
                .containsExactly(new AiMcpPromptArgument("feed", "The feed", true));
        assertThat(commands.get(1).getName()).isEqualTo("Fix Pipeline");
    }

    @Test
    void theToolsetListsAndFillsInPrompts() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.getServerCapabilities())
                .thenReturn(ServerCapabilities.builder().prompts(false).build());
        Mockito.when(client.listPrompts()).thenReturn(new ListPromptsResult(List.of(prompt("a")), "next"));
        Mockito.when(client.listPrompts("next")).thenReturn(new ListPromptsResult(List.of(prompt("b")), null));
        Mockito.when(client.getPrompt(Mockito.any())).thenReturn(new GetPromptResult("d", List.of(
                new PromptMessage(Role.USER, new TextContent("Onboard ACME.")),
                new PromptMessage(Role.USER, new TextContent("Then check it.")))));
        final McpToolset toolset = new McpToolset(
                List.of(new McpServerConnection("fastmcp", null, client, null)), List.of(), true);

        assertThat(toolset.prompts()).extracting(prompt -> prompt.prompt().name()).containsExactly("a", "b");
        assertThat(toolset.getPrompt("fastmcp", "a", Map.of("feed", "ACME")))
                .isEqualTo("Onboard ACME.\n\nThen check it.");
        final ArgumentCaptor<GetPromptRequest> request = ArgumentCaptor.forClass(GetPromptRequest.class);
        Mockito.verify(client).getPrompt(request.capture());
        assertThat(request.getValue().arguments()).isEqualTo(Map.of("feed", "ACME"));
    }

    @Test
    void serversWithoutPromptsAreLeftOut() {
        final McpSyncClient client = Mockito.mock(McpSyncClient.class);
        Mockito.when(client.getServerCapabilities()).thenReturn(ServerCapabilities.builder().build());
        final McpToolset toolset = new McpToolset(
                List.of(new McpServerConnection("fastmcp", null, client, null)), List.of(), true);

        assertThat(toolset.prompts()).isEmpty();
        Mockito.verify(client, Mockito.never()).listPrompts();
    }

    private static Prompt prompt(final String name, final PromptArgument... arguments) {
        return new Prompt(name, "Does " + name, List.of(arguments));
    }
}
