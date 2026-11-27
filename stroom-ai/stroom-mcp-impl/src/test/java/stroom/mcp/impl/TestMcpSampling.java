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

package stroom.mcp.impl;

import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.ModelSideCalls.Question;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A tool's side calls over `/mcp` ([McpSampling]): to the client's model by MCP sampling where the client said it
/// can, else none, saying why.
class TestMcpSampling {

    private static final UserRef USER = UserRef.builder().uuid("u1").build();

    private final McpAsyncServerExchange exchange = Mockito.mock(McpAsyncServerExchange.class);

    @Test
    void aClientThatSamplesAnswersWithItsModel() {
        Mockito.when(exchange.createMessage(Mockito.any())).thenReturn(Mono.just(new CreateMessageResult(Role.ASSISTANT,
                new TextContent(" [] "), "client-model", CreateMessageResult.StopReason.END_TURN)));
        final ModelSideCalls sampling = new McpSampling(session(ClientCapabilities.builder().sampling().build()),
                exchange, Duration.ofSeconds(5));

        final ModelSideCalls.Answer answer = sampling.ask(new Question("Classify.", "the items", 300));

        assertThat(sampling.available()).isTrue();
        assertThat(sampling.via()).isEqualTo("the client's model, by MCP sampling (client)");
        assertThat(answer.text()).isEqualTo("[]");
        final ArgumentCaptor<CreateMessageRequest> request = ArgumentCaptor.forClass(CreateMessageRequest.class);
        Mockito.verify(exchange).createMessage(request.capture());
        assertThat(request.getValue().systemPrompt()).isEqualTo("Classify.");
        assertThat(request.getValue().maxTokens()).isEqualTo(300);
        assertThat(((TextContent) request.getValue().messages().getFirst().content()).text()).isEqualTo("the items");
    }

    @Test
    void aClientThatDoesntSampleHasNoSideCallsAndIsNeverAsked() {
        final ModelSideCalls sampling = new McpSampling(session(ClientCapabilities.builder().elicitation().build()),
                exchange, Duration.ofSeconds(5));

        assertThat(sampling.available()).isFalse();
        assertThat(sampling.via()).isEqualTo("client doesn't offer MCP sampling, and Stroom has no model on /mcp");
        assertThatThrownBy(() -> sampling.ask(new Question("s", "u", 10)))
                .isInstanceOf(ModelSideCalls.SideCallException.class);
        Mockito.verifyNoInteractions(exchange);
    }

    @Test
    void aClientErrorOrNoTextIsASideCallException() {
        final ModelSideCalls sampling = new McpSampling(session(ClientCapabilities.builder().sampling().build()),
                exchange, Duration.ofSeconds(5));
        Mockito.when(exchange.createMessage(Mockito.any())).thenReturn(Mono.error(new IllegalStateException(
                "the user declined")));
        assertThatThrownBy(() -> sampling.ask(new Question("s", "u", 10)))
                .isInstanceOf(ModelSideCalls.SideCallException.class)
                .hasMessage("the client's model didn't answer: the user declined");

        Mockito.when(exchange.createMessage(Mockito.any())).thenReturn(Mono.empty());
        assertThatThrownBy(() -> sampling.ask(new Question("s", "u", 10)))
                .isInstanceOf(ModelSideCalls.SideCallException.class)
                .hasMessage("the client's model gave no text");
    }

    private static McpSession session(final ClientCapabilities capabilities) {
        return new McpSession("s1", USER, new Implementation("client", "1"), capabilities,
                McpSession.TransportKeeper.NONE);
    }
}
