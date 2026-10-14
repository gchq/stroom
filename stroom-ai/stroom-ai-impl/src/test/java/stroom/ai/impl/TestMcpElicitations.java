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

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestMcpElicitations {

    private static final int CHAT_ID = 3;
    private static final Map<String, Object> FORM = Map.of(
            "type", "object",
            "properties", Map.of("feed", Map.of("type", "string")),
            "required", List.of("feed"));

    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AiChatRunDao runDao = Mockito.mock(AiChatRunDao.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final Map<Integer, AiChatMessage> messages = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(100);
    private final AiChatRun run = new AiChatRun(9, CHAT_ID, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
    private final AiChatRun waitingRun = new AiChatRun(9, CHAT_ID, AiChatRunStatus.WAITING_FOR_USER,
            AiChatRunStopReason.NEEDS_INPUT, 0L, null, false, null);

    {
        Mockito.when(aiDao.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString())).thenAnswer(inv -> {
            final AiChatMessage message = new AiChatMessage(nextId.getAndIncrement(), inv.getArgument(0), 0L,
                    inv.getArgument(1), null, inv.getArgument(2), 1, null);
            messages.put(message.getId(), message);
            return message;
        });
        Mockito.when(aiDao.getMessagesSince(Mockito.anyInt(), Mockito.anyInt())).thenAnswer(inv ->
                messages.values().stream()
                        .filter(message -> message.getId() > (int) inv.getArgument(1))
                        .toList());
        Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> {
                    final AiChatMessage current = messages.get((int) inv.getArgument(0));
                    if (current == null || !current.getMessage().equals(inv.getArgument(1))) {
                        return false;
                    }
                    messages.put(current.getId(), new AiChatMessage(current.getId(), current.getChatId(), 0L,
                            current.getMessageType(), null, inv.getArgument(2), 1, null));
                    return true;
                });
        // The run is running when the form is shown, then waits for the user.
        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.of(run), Optional.of(waitingRun));
        Mockito.when(runDao.compareAndSetStatus(Mockito.anyInt(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any())).thenReturn(true);
    }

    @Test
    void theUsersAnswerGoesToTheServerAndTheRunWaitsMeanwhile() throws Exception {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);

        final CompletableFuture<ElicitResult> result = CompletableFuture.supplyAsync(() ->
                elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?", FORM, null)));
        final AiChatMessage card = waitForCard();
        assertThat(elicitations.waitingCount()).isEqualTo(1);

        elicitations.answer(CHAT_ID, card.getId(), "ACCEPT", Map.of("feed", "ACME"));

        final ElicitResult elicitResult = result.get(10, TimeUnit.SECONDS);
        assertThat(elicitResult.action()).isEqualTo(Action.ACCEPT);
        assertThat(elicitResult.content()).isEqualTo(Map.of("feed", "ACME"));
        assertThat(elicitations.waitingCount()).isZero();
        // The chat needed the user while the form waited, and the run carried on afterwards.
        Mockito.verify(runDao).compareAndSetStatus(9, List.of(AiChatRunStatus.RUNNING),
                AiChatRunStatus.WAITING_FOR_USER, AiChatRunStopReason.NEEDS_INPUT, null);
        Mockito.verify(runDao).compareAndSetStatus(9, List.of(AiChatRunStatus.WAITING_FOR_USER),
                AiChatRunStatus.RUNNING, null, null);
        // Only one answer counts.
        assertThatThrownBy(() -> elicitations.answer(CHAT_ID, card.getId(), "DECLINE", null))
                .hasMessageContaining("already been answered");
    }

    @Test
    void anAnswerThatDoesNotFitTheFormIsRefusedAndTheFormStillWaits() throws Exception {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final CompletableFuture<ElicitResult> result = CompletableFuture.supplyAsync(() ->
                elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?", FORM, null)));
        final AiChatMessage card = waitForCard();

        assertThatThrownBy(() -> elicitations.answer(CHAT_ID, card.getId(), "ACCEPT", Map.of()))
                .hasMessageContaining("Fill in 'feed'");

        elicitations.answer(CHAT_ID, card.getId(), "DECLINE", null);
        assertThat(result.get(10, TimeUnit.SECONDS).action()).isEqualTo(Action.DECLINE);
    }

    @Test
    void aFormCannotBeAnsweredOnceNoToolIsWaitingForIt() throws Exception {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final CompletableFuture<ElicitResult> result = CompletableFuture.supplyAsync(() ->
                elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?", FORM, null)));
        final AiChatMessage card = waitForCard();
        // E.g. the node running the tool has stopped.
        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> elicitations.answer(CHAT_ID, card.getId(), "ACCEPT", Map.of("feed", "ACME")))
                .hasMessageContaining("no longer waiting");
        assertThat(result.get(10, TimeUnit.SECONDS).action()).isEqualTo(Action.CANCEL);
    }

    @Test
    void anUnansweredFormExpires() {
        final McpElicitations elicitations = elicitations(StroomDuration.ZERO, 5);

        final ElicitResult result = elicitations.handle(CHAT_ID, "fastmcp",
                new ElicitFormRequest("Which feed?", FORM, null));

        assertThat(result.action()).isEqualTo(Action.CANCEL);
        assertThat(status(messages.values().iterator().next())).isEqualTo(ElicitationRecord.Status.EXPIRED);
    }

    @Test
    void stoppingTheRunCancelsTheForm() {
        Mockito.when(runManager.isStopRequested(CHAT_ID)).thenReturn(true);
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);

        final ElicitResult result = elicitations.handle(CHAT_ID, "fastmcp",
                new ElicitFormRequest("Which feed?", FORM, null));

        assertThat(result.action()).isEqualTo(Action.CANCEL);
        assertThat(status(messages.values().iterator().next())).isEqualTo(ElicitationRecord.Status.CANCELLED);
    }

    @Test
    void formsPastTheLimitAndUnsupportedFormsAreDeclined() {
        final McpElicitations full = elicitations(StroomDuration.ofMinutes(1), 0);
        assertThat(full.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?", FORM, null)).action())
                .isEqualTo(Action.DECLINE);

        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        assertThat(elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Nested?",
                Map.of("type", "object", "properties", Map.of("x", Map.of("type", "object"))), null)).action())
                .isEqualTo(Action.DECLINE);

        // The user is told, with no form shown.
        assertThat(messages.values()).extracting(AiChatMessage::getMessageType)
                .containsOnly(AiMessageType.WARNING);
        assertThat(messages.values()).extracting(AiChatMessage::getMessage)
                .anySatisfy(text -> assertThat(text).contains("too many forms"))
                .anySatisfy(text -> assertThat(text).contains("unsupported type"));
    }

    private McpElicitations elicitations(final StroomDuration timeout, final int maxPerNode) {
        final AiConfig config = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                timeout, maxPerNode);
        return new McpElicitations(aiDao, runDao, runManager, () -> config);
    }

    private AiChatMessage waitForCard() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            final Optional<AiChatMessage> card = messages.values().stream()
                    .filter(message -> message.getMessageType() == AiMessageType.ELICITATION)
                    .findFirst();
            if (card.isPresent()) {
                return card.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No form was shown");
    }

    private static ElicitationRecord.Status status(final AiChatMessage message) {
        return JsonUtil.readValue(message.getMessage(), ElicitationRecord.class).status();
    }
}
