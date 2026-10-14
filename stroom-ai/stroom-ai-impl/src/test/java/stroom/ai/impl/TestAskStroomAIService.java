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

import stroom.ai.api.AiService;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiChatStatus;
import stroom.ai.shared.AiChatStatusResponse;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.query.api.SearchRequestSource;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAskStroomAIService {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final AiChatRunDao runDao = Mockito.mock(AiChatRunDao.class);
    private final AiChat chat = new AiChat(1, 0L, 0L, "user1", "chat");

    private final ChatCompactor compactor = Mockito.mock(ChatCompactor.class);
    private final ToolApprovalService toolApprovalService = Mockito.mock(ToolApprovalService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);

    private AskStroomAIService service() {
        return new AskStroomAIService(aiService, null, null, null, null, null, null, () -> null, null, null,
                null, null, null, null, null, runManager, runDao, null, null, contextAssembler, null, compactor, null,
                toolApprovalService, Mockito.mock(AiAuditLog.class), aiDao, AiConfig::new, securityContext);
    }

    @Test
    void testAskStroomAi_startsARunWhenNoneIsActive() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "question"));

        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
        Mockito.verify(aiService, Mockito.never())
                .storeMessage(Mockito.eq(1), Mockito.eq(AiMessageType.STEER_MESSAGE), Mockito.anyString());
    }

    @Test
    void testAskStroomAi_steersAnActiveRun() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        Mockito.when(aiService.storeMessage(1, AiMessageType.STEER_MESSAGE, "also check yesterday"))
                .thenReturn(message(9, AiMessageType.STEER_MESSAGE, "also check yesterday"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "also check yesterday"));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.STEER_MESSAGE, "also check yesterday");
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAskStroomAi_runFinishedAsTheSteerArrived() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        // Active when checked, but gone once the message has been stored.
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running), Optional.empty());
        Mockito.when(aiService.storeMessage(1, AiMessageType.STEER_MESSAGE, "late"))
                .thenReturn(message(9, AiMessageType.STEER_MESSAGE, "late"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "late"));

        // The steer is taken back and the message is asked as a new question.
        Mockito.verify(aiService).deleteMessage(9);
        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testAskStroomAi_stoppingRunIsNotSteered() {
        final AiChatRun stopping = new AiChatRun(5, 1, AiChatRunStatus.STOPPING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(stopping));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "question"));

        // It is started, which fails as busy, rather than steering a run that is about to stop.
        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testEditMessage_onlyQuestionsCanBeEdited() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(1, AiMessageType.USER_MESSAGE, "q"),
                message(2, AiMessageType.AI_RESPONSE, "a")));

        assertThatThrownBy(() -> service().editMessage(new EditAiMessageRequest(chat, null, 2, "x")))
                .hasMessageContaining("Only your own questions");
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testEditMessage_stopsTheRunThenReplacesTheQuestionAndWhatFollows() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(1, AiMessageType.USER_MESSAGE, "q1"),
                message(2, AiMessageType.AI_RESPONSE, "a1"),
                message(3, AiMessageType.USER_MESSAGE, "q2"),
                message(4, AiMessageType.AI_RESPONSE, "a2")));
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        // Active until it has been asked to stop.
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running), Optional.of(running), Optional.empty());
        // Run the new run's work straight away.
        Mockito.when(runManager.start(Mockito.eq(1), Mockito.any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<AiChatRun> work = invocation.getArgument(1);
            try {
                work.accept(running);
            } catch (final RuntimeException e) {
                // The answer cannot be worked out with mocks, which is fine here.
            }
            return running;
        });

        service().editMessage(new EditAiMessageRequest(chat, null, 3, "q2 edited"));

        Mockito.verify(runManager).stop(1, AiChatRunStopReason.EDITED);
        // The edited question and its answer are removed, and the earlier turn is kept.
        Mockito.verify(aiService).deleteMessage(3);
        Mockito.verify(aiService).deleteMessage(4);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(1);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(2);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, "q2 edited");
    }

    @Test
    void testEditMessage_retryKeepsTheText() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(message(1, AiMessageType.USER_MESSAGE, "q1")));
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        Mockito.when(runManager.start(Mockito.eq(1), Mockito.any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<AiChatRun> work = invocation.getArgument(1);
            try {
                work.accept(null);
            } catch (final RuntimeException e) {
                // The answer cannot be worked out with mocks, which is fine here.
            }
            return null;
        });

        service().editMessage(new EditAiMessageRequest(chat, null, 1, null));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, "q1");
    }

    @Test
    void testEditMessage_runThatWillNotStop() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(message(1, AiMessageType.USER_MESSAGE, "q1")));
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        final AskStroomAIService service = service();
        service.setStopWait(java.time.Duration.ofMillis(300));

        assertThatThrownBy(() -> service.editMessage(new EditAiMessageRequest(chat, null, 1, "x")))
                .isInstanceOf(AiChatRunManager.AiChatBusyException.class);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(Mockito.anyInt());
    }

    @Test
    void testSelectHistoryForModel_leavesOutErrorsAndUiOnlyMessages() {
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.ATTACHMENT, "table"),
                message(2, AiMessageType.USER_MESSAGE, "question"),
                message(3, AiMessageType.THINKING, "thinking"),
                message(4, AiMessageType.ERROR, "The AI model took too long to respond"),
                message(41, AiMessageType.STEER_MESSAGE, "steer"),
                message(5, AiMessageType.WORKING, "Working..."),
                message(6, AiMessageType.DEBUG_DETAIL, "detail"),
                message(7, AiMessageType.AI_RESPONSE, "answer"),
                message(8, AiMessageType.USER_MESSAGE, "question 2"));

        final List<AiChatMessage> selected = AskStroomAIService.selectHistoryForModel(history);

        // ERROR rows must not be replayed, as the model would read them as its own replies.
        assertThat(selected)
                .extracting(AiChatMessage::getId)
                .containsExactly(1, 2, 41, 7, 8);
    }

    @Test
    void testSelectHistoryForModel_endsAtTheLastQuestion() {
        // A partial answer from an attempt that was stopped is left out, so the question can be answered again.
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.USER_MESSAGE, "question 1"),
                message(2, AiMessageType.AI_RESPONSE, "answer 1"),
                message(3, AiMessageType.USER_MESSAGE, "question 2"),
                message(4, AiMessageType.AI_RESPONSE, "partial answer 2"));

        assertThat(AskStroomAIService.selectHistoryForModel(history))
                .extracting(AiChatMessage::getId)
                .containsExactly(1, 2, 3);
    }

    @Test
    void testResumeNote() {
        final AiChatRun interrupted = new AiChatRun(1, 1, AiChatRunStatus.INTERRUPTED,
                AiChatRunStopReason.NODE_LOST, 0L, 1L, false, null);
        final AiChatRun failed = new AiChatRun(2, 1, AiChatRunStatus.FAILED, null, 0L, 1L, false, "boom");

        assertThat(AskStroomAIService.resumeNote(interrupted))
                .contains("did not finish: interrupted because the stroom node running it stopped")
                .contains("check the current state")
                .endsWith("\n\n");
        assertThat(AskStroomAIService.resumeNote(failed)).contains("did not finish: failed");
    }

    @Test
    void testSelectHistoryForModel_empty() {
        assertThat(AskStroomAIService.selectHistoryForModel(List.of())).isEmpty();
    }

    @Test
    void testUndoCompaction_onlyTheLatest() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(1, AiMessageType.USER_MESSAGE, "q"),
                message(2, AiMessageType.COMPACTION_SUMMARY, "{\"digestsUpToTurnId\":1}"),
                message(3, AiMessageType.COMPACTION_SUMMARY, "{\"digestsUpToTurnId\":2}"),
                message(4, AiMessageType.COMPACTION_SUMMARY, "{\"digestsUpToTurnId\":3,\"stale\":true}")));

        assertThatThrownBy(() -> service().undoCompaction(new UndoAiCompactionRequest(chat, 2)))
                .hasMessageContaining("Only the latest compaction");
        assertThatThrownBy(() -> service().undoCompaction(new UndoAiCompactionRequest(chat, 4)))
                .hasMessageContaining("Only the latest compaction");

        final AiContextUsage usage = new AiContextUsage(1000, 600L, true, Map.of(), 80);
        Mockito.when(contextAssembler.estimateUsage(1)).thenReturn(usage);
        // The new usage is returned, as undoing makes the model see more.
        assertThat(service().undoCompaction(new UndoAiCompactionRequest(chat, 3))).isEqualTo(usage);
        Mockito.verify(aiService).deleteMessage(3);
    }

    @Test
    void testUndoCompaction_notWhileBusy() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service().undoCompaction(new UndoAiCompactionRequest(chat, 3)))
                .isInstanceOf(AiChatRunManager.AiChatBusyException.class);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(Mockito.anyInt());
    }

    @Test
    void testCompactNow_saysWhenThereIsNothingToCompact() {
        final AskStroomAIService service = Mockito.spy(service());
        Mockito.doReturn(null).when(service).getModelDocForCompaction(Mockito.any());
        Mockito.when(runManager.start(Mockito.eq(1), Mockito.any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<AiChatRun> work = invocation.getArgument(1);
            work.accept(null);
            return null;
        });
        Mockito.when(compactor.compactNow(Mockito.eq(1), Mockito.any(), Mockito.any(), Mockito.eq("focus")))
                .thenReturn(false);

        service.compactChat(new CompactAiChatRequest(chat, null, "focus"));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.WARNING, AskStroomAIService.NOTHING_TO_COMPACT);
    }

    @Test
    void testSourceDocUuid() {
        final DocRef dataSource = new DocRef("Index", "index-uuid");
        final DocRef queryDoc = new DocRef("Query", "query-uuid");

        assertThat(AskStroomAIService.sourceDocUuid(new DashboardTableContext("d", null,
                DashboardSearchRequest.builder()
                        .search(Search.builder().dataSourceRef(dataSource).build())
                        .build()))).isEqualTo("index-uuid");
        assertThat(AskStroomAIService.sourceDocUuid(new QueryTableContext("q", null,
                QuerySearchRequest.builder()
                        .searchRequestSource(SearchRequestSource.builder().ownerDocRef(queryDoc).build())
                        .build()))).isEqualTo("query-uuid");
        assertThat(AskStroomAIService.sourceDocUuid(new DashboardTableContext("d", null, null))).isNull();
        assertThat(AskStroomAIService.sourceDocUuid(new GeneralTableContext("g", List.of(), List.of()))).isNull();
    }

    @Test
    void testAnsweringToolCallsCarriesOnTheRun() {
        final AiChatMessage request = toolApprovalMessage(20);
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(10, AiMessageType.USER_MESSAGE, "make the feed"), request));
        Mockito.when(aiService.getChat(1)).thenReturn(chat);
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage(null, null, null, 0, 0));
        final AnswerAiApprovalRequest answer = new AnswerAiApprovalRequest(chat, 20, true);
        Mockito.when(toolApprovalService.answer(1, request, answer)).thenReturn(ToolApprovalRequest.Status.ANSWERED);

        assertThat(service().answerApproval(answer)).isTrue();

        Mockito.verify(runManager).continueRun(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testPartlyAnsweredToolCallsKeepWaiting() {
        final AiChatMessage request = toolApprovalMessage(20);
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(request));
        final AnswerAiApprovalRequest answer = new AnswerAiApprovalRequest(chat, 20, true, null, null,
                List.of("a"));
        Mockito.when(toolApprovalService.answer(1, request, answer)).thenReturn(ToolApprovalRequest.Status.PENDING);

        assertThat(service().answerApproval(answer)).isTrue();

        Mockito.verify(runManager, Mockito.never()).continueRun(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAMessageSentInsteadOfAnsweringDeniesTheCalls() {
        final AiChatRun waiting = new AiChatRun(5, 1, AiChatRunStatus.WAITING_FOR_USER,
                AiChatRunStopReason.NEEDS_APPROVAL, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(waiting));
        final AiChatMessage request = toolApprovalMessage(20);
        Mockito.when(toolApprovalService.pending(1)).thenReturn(Optional.of(request));
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(10, AiMessageType.USER_MESSAGE, "make the feed"), request));
        Mockito.when(aiService.getChat(1)).thenReturn(chat);
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage(null, null, null, 0, 0));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "no, use the other name"));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.STEER_MESSAGE, "no, use the other name");
        Mockito.verify(toolApprovalService).denyAll(request, "The user sent a message instead: no, use the other "
                                                             + "name");
        Mockito.verify(runManager).continueRun(Mockito.eq(1), Mockito.any());
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testApproveEverythingNeedsAnAdministrator() {
        assertThatThrownBy(() -> service().setApproveEverything(new SetAiApproveEverythingRequest(chat, true)))
                .hasMessageContaining("An administrator has not allowed");
        Mockito.verify(aiDao, Mockito.never()).setApproveEverything(Mockito.anyInt(), Mockito.anyBoolean());

        // It can always be turned off.
        assertThat(service().setApproveEverything(new SetAiApproveEverythingRequest(chat, false))).isTrue();
        Mockito.verify(aiDao).setApproveEverything(1, false);
    }

    private static AiChatMessage toolApprovalMessage(final int id) {
        final ToolApprovalRequest.PendingCall call = new ToolApprovalRequest.PendingCall("a", "s__t", "s", "t",
                "{}", List.of(), "h", "sh", null, null);
        return new AiChatMessage(id, 1, System.currentTimeMillis(), AiMessageType.APPROVAL_REQUEST, null,
                JsonUtil.writeValueAsString(new ToolApprovalRequest(ToolApprovalRequest.KIND,
                        ToolApprovalRequest.Status.PENDING, "r", List.of(call), true, false), false),
                10, null);
    }

    @Test
    void testChatStatusesAreOnlySentWhenChanged() {
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("u").build());
        Mockito.when(aiDao.getStatusVersion("u")).thenReturn(42L);
        Mockito.when(aiDao.getChatStatuses("u", AskStroomAIService.MAX_CHAT_STATUSES)).thenReturn(List.of(
                new AiChatStatus(1, "chat", 0L, AiChatRunStatus.WAITING_FOR_USER, true, false, 0L)));

        final AiChatStatusResponse first = service().getChatStatuses(0);
        final AiChatStatusResponse unchanged = service().getChatStatuses(42);

        assertThat(first.getVersion()).isEqualTo(42);
        assertThat(first.getStatuses()).singleElement().satisfies(status -> {
            assertThat(status.isNeedsYou()).isTrue();
            assertThat(status.isUnread()).isTrue();
            assertThat(status.isBusy()).isFalse();
        });
        assertThat(unchanged.getStatuses()).isNull();
    }

    @Test
    void testPollMessages_streamedTextIsReportedAsItStandsNotAsNewMessages() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getLatest(1)).thenReturn(Optional.of(running));
        Mockito.when(runDao.interruptStale(Mockito.anyLong())).thenReturn(List.of());
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of());
        Mockito.when(aiService.getWorkingMessage(1)).thenReturn(Optional.empty());
        final AiChatMessage streamed = message(7, AiMessageType.STREAMING_ANSWER, "The answer so");
        Mockito.when(aiService.getMessagesSince(1, 2)).thenReturn(List.of(
                message(3, AiMessageType.TOOL_CALL, "{}"),
                streamed));
        Mockito.when(aiService.getStreamingMessages(1)).thenReturn(List.of(streamed));

        final AiChatPollResponse response = service().pollMessages(1, new AiChatPollRequest(2, false));

        assertThat(response.getNewMessages()).extracting(AiChatMessage::getId).containsExactly(3);
        assertThat(response.getStreamingMessages()).containsExactly(streamed);
    }

    @Test
    void testPollMessages_nothingIsStreamedWithoutAnActiveRun() {
        Mockito.when(runDao.getLatest(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of());
        Mockito.when(aiService.getWorkingMessage(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getMessagesSince(1, 2)).thenReturn(List.of());

        final AiChatPollResponse response = service().pollMessages(1, new AiChatPollRequest(2, false));

        assertThat(response.getStreamingMessages()).isEmpty();
        Mockito.verify(aiService, Mockito.never()).getStreamingMessages(1);
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, 0, null);
    }
}
