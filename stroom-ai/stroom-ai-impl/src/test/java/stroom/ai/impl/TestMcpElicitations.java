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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.time.StroomDuration;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    // The stop flag of the run that made the tool call.
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);
    private final Map<Integer, AiChatMessage> messages = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(100);
    private final AiChatRun run = new AiChatRun(9, CHAT_ID, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
    private final AiChatRun waitingRun = new AiChatRun(9, CHAT_ID, AiChatRunStatus.WAITING_FOR_USER,
            AiChatRunStopReason.NEEDS_INPUT, 0L, null, false, null);

    {
        Mockito.when(runManager.stopFlag(CHAT_ID)).thenReturn(stopFlag);
        Mockito.when(aiDao.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString())).thenAnswer(inv -> {
            final AiChatMessage message = new AiChatMessage(nextId.getAndIncrement(), inv.getArgument(0), 0L,
                    inv.getArgument(1), null, inv.getArgument(2), 1, null);
            messages.put(message.getId(), message);
            return message;
        });
        Mockito.when(aiDao.getMessages(CHAT_ID)).thenAnswer(inv -> messages.values().stream()
                .sorted(Comparator.comparingInt(AiChatMessage::getId))
                .toList());
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
    void anAnswerGivenAsSoonAsTheFormIsShownIsTaken() {
        // Seen as a flaky test: the form was shown before its run waited for the user, so an answer given at once
        // was refused as if the tool had stopped waiting. Here the user answers the moment the form is stored.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final AtomicReference<Throwable> answerError = new AtomicReference<>();
        Mockito.when(aiDao.storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString())).thenAnswer(inv -> {
            final AiChatMessage card = new AiChatMessage(nextId.getAndIncrement(), inv.getArgument(0), 0L,
                    inv.getArgument(1), null, inv.getArgument(2), 1, null);
            messages.put(card.getId(), card);
            try {
                elicitations.answer(CHAT_ID, card.getId(), "ACCEPT", Map.of("feed", "ACME"));
            } catch (final RuntimeException e) {
                answerError.set(e);
            }
            return card;
        });

        final ElicitResult result = elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?",
                FORM, null));

        assertThat(answerError.get()).isNull();
        assertThat(result.action()).isEqualTo(Action.ACCEPT);
        assertThat(result.content()).isEqualTo(Map.of("feed", "ACME"));
    }

    @Test
    void aFormsDocumentLinksAreCheckedAndNoNamesGuessed() throws Exception {
        // Asked for by the user: the pipeline a form names opens from it, linked by its tool; no name is linked by
        // guessing (seen: the index a form was to create linked to the feed of its name).
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final AnswerLinks links = Mockito.mock(AnswerLinks.class);
        Mockito.when(links.checkLinks(CHAT_ID, "Reprocess with 'Twitter-Events-V1.1'?"))
                .thenReturn("Reprocess with '[Twitter-Events-V1.1](?action=open-doc&docType=Pipeline&docUuid=p)'?");
        elicitations.setAnswerLinks(links);

        final CompletableFuture<ElicitResult> result = CompletableFuture.supplyAsync(() ->
                elicitations.handle(CHAT_ID, "Stroom AI", new ElicitFormRequest(
                        "Reprocess with 'Twitter-Events-V1.1'?", FORM, null)));
        final AiChatMessage card = waitForCard();

        assertThat(JsonUtil.readValue(card.getMessage(), ElicitationRecord.class).message())
                .isEqualTo("Reprocess with '[Twitter-Events-V1.1](?action=open-doc&docType=Pipeline&docUuid=p)'?");
        Mockito.verify(links, Mockito.never()).link(Mockito.anyInt(), Mockito.anyString());
        elicitations.answer(CHAT_ID, card.getId(), "DECLINE", null);
        assertThat(result.get(10, TimeUnit.SECONDS).action()).isEqualTo(Action.DECLINE);
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
    void aMessageSentInsteadPutsTheFormAsideAndTheToolCarriesOn() throws Exception {
        Mockito.when(aiDao.getMessages(CHAT_ID)).thenAnswer(inv -> messages.values().stream()
                .sorted(Comparator.comparingInt(AiChatMessage::getId))
                .toList());
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final CompletableFuture<ElicitResult> result = CompletableFuture.supplyAsync(() ->
                elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?", FORM, null)));
        final AiChatMessage card = waitForCard();

        assertThat(elicitations.supersedePending(CHAT_ID)).isEqualTo(1);

        // Not answered: the server is told so (a cancel, as the protocol has it, and nothing more), and the card says
        // why.
        final ElicitResult told = result.get(5, TimeUnit.SECONDS);
        assertThat(told.action()).isEqualTo(Action.CANCEL);
        assertThat(McpElicitations.sentInstead(told)).as("what an MCP server is told").isEmpty();
        assertThat(messages.get(card.getId()).getMessage()).contains("\"status\":\"SUPERSEDED\"");
        // Only a form still waiting is put aside.
        assertThat(elicitations.supersedePending(CHAT_ID)).isZero();
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
        stopFlag.set(true);
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);

        final ElicitResult result = elicitations.handle(CHAT_ID, "fastmcp",
                new ElicitFormRequest("Which feed?", FORM, null));

        assertThat(result.action()).isEqualTo(Action.CANCEL);
        assertThat(status(messages.values().iterator().next())).isEqualTo(ElicitationRecord.Status.CANCELLED);
    }

    @Test
    void aFormIsCancelledWhenItsRunStopsAndEndsWhileItWaits() {
        // Seen: Stop while a form waited gave up on the tool call, so the run ended, and the chat no longer had a
        // run to say it had been stopped: the form waited on until it expired, shown as needing the user.
        Mockito.when(runManager.isStopRequested(CHAT_ID)).thenReturn(false);
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final Thread stopper = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(300);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stopFlag.set(true);
        });

        final ElicitResult result = elicitations.handle(CHAT_ID, "fastmcp",
                new ElicitFormRequest("Which feed?", FORM, null));

        assertThat(result.action()).isEqualTo(Action.CANCEL);
        assertThat(status(messages.values().iterator().next())).isEqualTo(ElicitationRecord.Status.CANCELLED);
        assertThat(stopper.isAlive()).isFalse();
    }

    @Test
    void formsPastTheLimitAreDeclined() {
        final McpElicitations full = elicitations(StroomDuration.ofMinutes(1), 0);
        assertThat(full.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Which feed?", FORM, null)).action())
                .isEqualTo(Action.DECLINE);

        assertThat(messages.values()).extracting(AiChatMessage::getMessage)
                .singleElement()
                .satisfies(text -> assertThat(text).contains("too many forms").contains("maxElicitationsPerNode")
                        .containsIgnoringCase("didn't decline"));
    }

    @Test
    void aServersFormThatCanNeverBeShownIsAnsweredWithAnErrorNotADecline() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);

        // Too long to show, or unsupported: the server's problem, so it is told of an error, not that the user
        // declined, which they didn't.
        assertThatThrownBy(() -> elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest(
                "x".repeat(ElicitationSchema.MAX_MESSAGE_CHARS + 1), FORM, null)))
                .isInstanceOfSatisfying(McpError.class, e -> {
                    assertThat(e.getJsonRpcError().code()).isEqualTo(ErrorCodes.INVALID_PARAMS);
                    assertThat(e.getMessage()).contains("message is too long").contains("has not answered");
                });
        assertThatThrownBy(() -> elicitations.handle(CHAT_ID, "fastmcp", new ElicitFormRequest("Nested?",
                Map.of("type", "object", "properties", Map.of("x", Map.of("type", "object"))), null)))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("unsupported type");

        // The user is told, with no form shown, and what they can do.
        assertThat(messages.values()).extracting(AiChatMessage::getMessageType)
                .containsOnly(AiMessageType.WARNING);
        assertThat(messages.values()).extracting(AiChatMessage::getMessage)
                .hasSize(2)
                .allSatisfy(text -> assertThat(text).contains("a problem with the MCP server")
                        .contains("rather than that you declined").contains("administrator"));
    }

    @Test
    void aFormThatCanNeverBeShownAskedWithNoCallIdIsNotSaidToBeDeclined() {
        // A model that gives its tool calls no id: the form was refused as declined, though the user never saw it.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);

        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", null,
                "x".repeat(ElicitationSchema.MAX_MESSAGE_CHARS + 1), FORM))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("didn't decline")
                .hasMessageContaining("message is too long");
    }

    @Test
    void anMcpServersFormThatCanNeverBeShownIsRefusedSayingEveryLimit() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);

        assertThatThrownBy(() -> elicitations.handle(CHAT_ID, "server",
                new ElicitFormRequest("x".repeat(ElicitationSchema.MAX_MESSAGE_CHARS + 1), FORM, null)))
                .hasMessageContaining(ElicitationSchema.MAX_SCHEMA_CHARS + " characters of fields");
    }

    @Test
    void aToolsFormThatCanNeverBeShownFailsTheCallRatherThanSayingTheUserDeclined() {
        // Seen: a form too large to show was declined, the tool told the agent "The user made no choice", and the
        // agent called it again and again, the user asked the same questions each time.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 1; i <= ElicitationSchema.MAX_FIELDS + 1; i++) {
            properties.put("q" + i, Map.of("type", "string", "enum", List.of("Leave out", "Keep as written")));
        }
        final Map<String, Object> tooMany = Map.of("type", "object", "properties", properties);

        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "build_translation_xslt", "c1", "Which?", tooMany))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("couldn't show this tool's form")
                .hasMessageContaining("more than " + ElicitationSchema.MAX_FIELDS + " fields")
                .hasMessageContaining("didn't decline")
                .hasMessageContaining("don't repeat the call");

        // No form is shown; the user is told.
        assertThat(messages.values()).extracting(AiChatMessage::getMessageType).containsOnly(AiMessageType.WARNING);
        assertThat(messages.values()).extracting(AiChatMessage::getMessage).singleElement().asString()
                .contains("couldn't be shown");
    }

    @Test
    void askUsersQuestionsStopTheRunUntilTheyAreAnsweredAndTheCallIsGivenTheAnswerWhenMadeAgain() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 1);
        question();

        // No thread waits: the run stops, however long the user takes.
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOfSatisfying(AiChatRunManager.WaitingForUserException.class, e ->
                        assertThat(e.getStopReason()).isEqualTo(AiChatRunStopReason.NEEDS_ANSWERS));
        assertThat(elicitations.waitingCount()).isZero();
        final AiChatMessage card = cardFor("c1");
        assertThat(JsonUtil.readValue(card.getMessage(), ElicitationRecord.class).expiresMs())
                .isEqualTo(Long.MAX_VALUE);
        // Made again before it is answered (e.g. the run was resumed), it stops again, without asking twice.
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        assertThat(messages.values()).filteredOn(m -> m.getMessageType() == AiMessageType.ELICITATION).hasSize(1);

        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.of(new AiChatRun(9, CHAT_ID,
                AiChatRunStatus.WAITING_FOR_USER, AiChatRunStopReason.NEEDS_ANSWERS, 0L, null, false, null)));
        // The run is to be carried on.
        assertThat(elicitations.answer(CHAT_ID, card.getId(), "ACCEPT", Map.of("feed", "ACME"))).isTrue();

        final ElicitResult result = elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM);
        assertThat(result.action()).isEqualTo(Action.ACCEPT);
        assertThat(result.content()).isEqualTo(Map.of("feed", "ACME"));
    }

    @Test
    void askUsersQuestionsCanBeAnsweredAfterTheirRunHasEnded() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        // E.g. it was stopped, or the user's session ended.
        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.empty());

        assertThat(elicitations.answer(CHAT_ID, cardFor("c1").getId(), "DECLINE", null)).isTrue();
        assertThat(elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM).action())
                .isEqualTo(Action.DECLINE);
    }

    @Test
    void askUsersQuestionsCannotBeAnsweredOnceTheUserHasAskedSomethingElse() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        final AiChatMessage card = cardFor("c1");
        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.empty());
        question();

        assertThatThrownBy(() -> elicitations.answer(CHAT_ID, card.getId(), "ACCEPT", Map.of("feed", "ACME")))
                .hasMessageContaining("no longer waiting");
        assertThat(JsonUtil.readValue(messages.get(card.getId()).getMessage(), ElicitationRecord.class).status())
                .isEqualTo(ElicitationRecord.Status.CANCELLED);
    }

    @Test
    void askUsersQuestionsCannotBeAnsweredWhileAnotherRunIsWorking() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        elicitations.setRunStopWait(Duration.ofMillis(300));
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.of(run));

        // Running past the wait: another's work, not the run that asked about to stop.
        assertThatThrownBy(() -> elicitations.answer(CHAT_ID, cardFor("c1").getId(), "ACCEPT",
                Map.of("feed", "ACME")))
                .hasMessageContaining("no longer waiting");
    }

    @Test
    void askUsersQuestionsAnsweredBeforeTheirRunHasStoppedForThemAreTaken() throws Exception {
        // Seen in TestAskStroomAiWideSampleForms, now and then: the form is stored as the tool call throws, and the
        // user (the test) answered it before the run had unwound and stopped for it. The answer was refused as "no
        // longer waiting" and the form cancelled, so the run waited for an answer it could never be given.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        final AtomicReference<AiChatRun> active = new AtomicReference<>(run);
        Mockito.when(runDao.getActive(CHAT_ID)).thenAnswer(inv -> Optional.of(active.get()));
        final CompletableFuture<Void> stops = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(300);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            active.set(new AiChatRun(9, CHAT_ID, AiChatRunStatus.WAITING_FOR_USER,
                    AiChatRunStopReason.NEEDS_ANSWERS, 0L, null, false, null));
        });

        assertThat(elicitations.answer(CHAT_ID, cardFor("c1").getId(), "ACCEPT", Map.of("feed", "ACME"))).isTrue();

        stops.get(5, TimeUnit.SECONDS);
        assertThat(JsonUtil.readValue(cardFor("c1").getMessage(), ElicitationRecord.class).status())
                .isEqualTo(ElicitationRecord.Status.ACCEPTED);
        assertThat(elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM).content())
                .isEqualTo(Map.of("feed", "ACME"));
    }

    @Test
    void askUsersQuestionsPutAsideByAMessageAreNotAnswered() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);

        assertThat(elicitations.supersedePending(CHAT_ID)).isEqualTo(1);

        // Not answered (a cancel, to a tool that knows no better), but put aside by a message, not closed or run out.
        final ElicitResult result = elicitations.askUser(CHAT_ID, "ask_user", "c1", "Which feed?", FORM);
        assertThat(result.action()).isEqualTo(Action.CANCEL);
        assertThat(McpElicitations.sentInstead(result)).as("no message stored here").contains("");
    }

    @Test
    void askUsersQuestionsPutAsideByAMessageTellTheToolTheUserSentOneAndWhatItSaid() {
        // Seen in testing: the user typed a question in the chat instead of answering survey_feed's form; the
        // re-made call was told only "cancel", as for a form closed or run out, so the agent was told to call the
        // tool again with the same arguments, the form was shown again, and the question was never answered.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "survey_feed", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        // As AskStroomAIService steers the run: the message is stored, then the form put aside.
        final String typed = "What's the difference between event.app and event.logger_name?";
        aiDao.storeMessage(CHAT_ID, AiMessageType.STEER_MESSAGE, typed);
        assertThat(elicitations.supersedePending(CHAT_ID)).isEqualTo(1);

        // A later message, sent once the run had carried on, isn't the one that put the form aside.
        aiDao.storeMessage(CHAT_ID, AiMessageType.STEER_MESSAGE, "And another thing");

        final ElicitResult result = elicitations.askUser(CHAT_ID, "survey_feed", "c1", "Which feed?", FORM);

        // Not the same as a form that ran out: the tool (and so the agent) can tell the user sent a message, and
        // what it said, as a request for approval answered with a message is told (AskStroomAIService.steer).
        assertThat(McpElicitations.sentInstead(result)).as("what the re-made call is given").contains(typed);
        assertThat(McpElicitations.sentInsteadText("Which feed?", "survey_feed", typed, null))
                .as("what the agent is told").containsIgnoringCase("sent a message instead").contains(typed)
                .contains("Reply to their message first").doesNotContain("again with the same arguments");
    }

    @Test
    void stroomsOwnFormAskedOutsideACallPutAsideByAMessageTellsTheToolWhatItSaid() throws Exception {
        // A call with no id waits for the answer, as for an MCP server, but is Stroom's own: told what was sent.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final CompletableFuture<ElicitResult> result = CompletableFuture.supplyAsync(() ->
                elicitations.askUser(CHAT_ID, "survey_feed", null, "Which feed?", FORM));
        waitForCard();
        aiDao.storeMessage(CHAT_ID, AiMessageType.STEER_MESSAGE, "Why ask?");

        assertThat(elicitations.supersedePending(CHAT_ID)).isEqualTo(1);

        assertThat(McpElicitations.sentInstead(result.get(5, TimeUnit.SECONDS))).contains("Why ask?");
    }

    @Test
    void aFormThatRanOutIsNotTakenForOnePutAside() {
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        question();
        assertThatThrownBy(() -> elicitations.askUser(CHAT_ID, "survey_feed", "c1", "Which feed?", FORM))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        final AiChatMessage card = cardFor("c1");
        messages.put(card.getId(), new AiChatMessage(card.getId(), CHAT_ID, 0L, AiMessageType.ELICITATION, null,
                JsonUtil.writeValueAsString(JsonUtil.readValue(card.getMessage(), ElicitationRecord.class)
                        .answered(ElicitationRecord.Status.EXPIRED, null), false), 1, null));
        aiDao.storeMessage(CHAT_ID, AiMessageType.STEER_MESSAGE, "A message sent later");

        final ElicitResult result = elicitations.askUser(CHAT_ID, "survey_feed", "c1", "Which feed?", FORM);

        assertThat(result.action()).isEqualTo(Action.CANCEL);
        assertThat(McpElicitations.sentInstead(result)).isEmpty();
    }

    @Test
    void anMcpServersFormIsNotAnsweredAsIfItsRunHadStopped() {
        // Its tool call waits for it, so it is never carried on.
        final McpElicitations elicitations = elicitations(StroomDuration.ofMinutes(1), 5);
        final ElicitationRecord record = new ElicitationRecord(ElicitationRecord.KIND,
                ElicitationRecord.Status.PENDING, "fastmcp", null, "Which feed?", FORM, Long.MAX_VALUE, null, null);
        final AiChatMessage card = aiDao.storeMessage(CHAT_ID, AiMessageType.ELICITATION,
                JsonUtil.writeValueAsString(record, false));
        Mockito.when(runDao.getActive(CHAT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> elicitations.answer(CHAT_ID, card.getId(), "DECLINE", null))
                .hasMessageContaining("no longer waiting");
    }

    // Adds a question from the user to the chat.
    private void question() {
        aiDao.storeMessage(CHAT_ID, AiMessageType.USER_MESSAGE, "Onboard this feed");
    }

    private AiChatMessage cardFor(final String callId) {
        return messages.values().stream()
                .filter(m -> m.getMessageType() == AiMessageType.ELICITATION)
                .filter(m -> callId.equals(JsonUtil.readValue(m.getMessage(), ElicitationRecord.class).callId()))
                .findFirst()
                .orElseThrow();
    }

    private McpElicitations elicitations(final StroomDuration timeout, final int maxPerNode) {
        final AiConfig config = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                timeout, maxPerNode, null, null, null, null);
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
