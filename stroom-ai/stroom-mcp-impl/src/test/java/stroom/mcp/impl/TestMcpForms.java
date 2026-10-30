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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpForms {

    private static final UserRef ALICE = UserRef.builder().uuid("alice").subjectId("alice").build();
    private static final UserRef BOB = UserRef.builder().uuid("bob").subjectId("bob").build();

    private final McpAsyncServerExchange exchange = Mockito.mock(McpAsyncServerExchange.class);

    @AfterEach
    void tearDown() {
        // Leave the test thread as it was found.
        Thread.interrupted();
    }

    @Test
    void aFormTooLargeToShowIsNotSentAndTheAgentIsToldTheUserWasntAsked() {
        // Seen in testing: a client declined a form whose message was too long, and the tool said the user made
        // no choice.
        final McpForms forms = forms(10);

        assertThatThrownBy(() -> forms.ask(session(ALICE), exchange, "x".repeat(2_001), McpForms.NO_FIELDS))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("as its message is too long, so the user wasn't asked and didn't decline");
        Mockito.verifyNoInteractions(exchange);
    }

    @Test
    void aFormTheClientAnswersWithAnErrorIsNotTakenForTheUsersAnswer() {
        Mockito.when(exchange.createElicitation(Mockito.any())).thenReturn(Mono.error(McpError.builder(
                ErrorCodes.INVALID_PARAMS).message("Form too large").build()));

        assertThatThrownBy(() -> forms(10).ask(session(ALICE), exchange, "Allow it?", McpForms.NO_FIELDS))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("as the client said: Form too large, so the user wasn't asked");
    }

    @Test
    void aCallCancelledWhileItsFormWaitsStops() {
        // Seen in review: block() wraps the interrupt, and the form was taken as unanswered, so the call went on.
        Mockito.when(exchange.createElicitation(Mockito.any())).thenReturn(Mono.never());
        final McpForms forms = forms(4);
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> forms.ask(session(ALICE), exchange, "Allow it?", McpForms.NO_FIELDS))
                .isInstanceOf(CancellationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(forms.waitingCount()).isZero();
    }

    @Test
    void oneUserMayHaveAtMostHalfTheNodesFormsWaiting() throws Exception {
        final List<Sinks.One<ElicitResult>> answers = new ArrayList<>();
        Mockito.when(exchange.createElicitation(Mockito.any())).thenAnswer(invocation -> {
            final Sinks.One<ElicitResult> answer = Sinks.one();
            synchronized (answers) {
                answers.add(answer);
            }
            return answer.asMono();
        });
        final McpForms forms = forms(4);
        final List<CompletableFuture<ElicitResult>> waiting = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            waiting.add(CompletableFuture.supplyAsync(() -> forms.ask(session(ALICE), exchange, "?",
                    McpForms.NO_FIELDS)));
        }
        waitFor(() -> forms.waitingCount() == 2);

        // Alice has her half: her next form isn't shown, but Bob's is.
        assertThat(forms.ask(session(ALICE), exchange, "?", McpForms.NO_FIELDS).action()).isEqualTo(Action.CANCEL);
        waiting.add(CompletableFuture.supplyAsync(() -> forms.ask(session(BOB), exchange, "?",
                McpForms.NO_FIELDS)));
        waitFor(() -> forms.waitingCount() == 3);

        synchronized (answers) {
            answers.forEach(answer -> answer.tryEmitValue(new ElicitResult(Action.ACCEPT, Map.of(), null)));
        }
        for (final CompletableFuture<ElicitResult> future : waiting) {
            assertThat(future.get(10, TimeUnit.SECONDS).action()).isEqualTo(Action.ACCEPT);
        }
        assertThat(forms.waitingCount()).isZero();
    }

    private static McpForms forms(final int maxPerNode) {
        final AiConfig aiConfig = Mockito.mock(AiConfig.class);
        Mockito.when(aiConfig.getMcpEndpoint()).thenReturn(new McpEndpointConfig(null, null, maxPerNode, null,
                null, null));
        return new McpForms(() -> aiConfig);
    }

    private static McpSession session(final UserRef user) {
        return new McpSession("s-" + user.getUuid(), user, new Implementation("client", "1"),
                ClientCapabilities.builder().elicitation().build());
    }

    private static void waitFor(final BooleanSupplier condition) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("waited too long").isLessThan(deadline);
            Thread.sleep(20);
        }
    }
}
