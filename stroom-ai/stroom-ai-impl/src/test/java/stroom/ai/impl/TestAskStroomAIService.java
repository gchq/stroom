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
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
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
import stroom.ai.shared.AiTabSnapshot;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.SuggestAiPromptsRequest;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.SearchRequestSource;
import stroom.query.impl.QueryService;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAskStroomAIService {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final AiChatRunDao runDao = Mockito.mock(AiChatRunDao.class);
    private final ChatTitler chatTitler = Mockito.mock(ChatTitler.class);
    private final stroom.ai.impl.memory.AiMemoryService memoryService =
            Mockito.mock(stroom.ai.impl.memory.AiMemoryService.class);
    private final AiChat chat = new AiChat(1, 0L, 0L, "user1", "chat");

    private final ChatCompactor compactor = Mockito.mock(ChatCompactor.class);
    private final ToolApprovalService toolApprovalService = Mockito.mock(ToolApprovalService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final ContextAssembler contextAssembler = Mockito.mock(ContextAssembler.class);

    private final AiBackgroundTasks backgroundTasks = Mockito.mock(AiBackgroundTasks.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);

    private AskStroomAIService service() {
        return new AskStroomAIService(aiService, null, null, null, null, null, null, () -> null, null, null,
                null, null, null, null, null, runManager, runDao, null, null, contextAssembler, null, compactor, null,
                toolApprovalService, Mockito.mock(AiAuditLog.class), aiDao, AiConfig::new, securityContext,
                () -> mcpElicitations, chatTitler, null,
                backgroundTasks, null, null, tabSnapshots, new HttpServletRequestHolder());
    }

    private final McpElicitations mcpElicitations = Mockito.mock(McpElicitations.class);
    private final AiAttachmentFileStore attachmentFileStore = Mockito.mock(AiAttachmentFileStore.class);
    private final McpClientCache mcpClientCache = Mockito.mock(McpClientCache.class);
    private final ContextUsageTracker usageTracker = Mockito.mock(ContextUsageTracker.class);

    // With what deleting a chat needs.
    private AskStroomAIService serviceForDelete() {
        return new AskStroomAIService(aiService, attachmentFileStore, null, null, null, null, null, () -> null,
                null, null, null, null, null, mcpClientCache, null, runManager, runDao, null, null,
                contextAssembler, usageTracker, compactor, memoryService, toolApprovalService,
                Mockito.mock(AiAuditLog.class), aiDao, AiConfig::new, securityContext, () -> null, chatTitler, null,
                backgroundTasks, null, null, tabSnapshots, new HttpServletRequestHolder());
    }

    private static final DocRef CHOSEN_MODEL = new DocRef(OpenAIModelDoc.TYPE, "chosen-uuid", "Chosen");
    private static final DocRef DEFAULT_MODEL = new DocRef(OpenAIModelDoc.TYPE, "default-uuid", "Default");

    // With a model store and a default config whose model is DEFAULT_MODEL, or none.
    private AskStroomAIService serviceForModels(final OpenAIModelStore store, final DocRef defaultModel) {
        final AskStroomAiConfig defaultConfig = AskStroomAiConfig.builder().modelRef(defaultModel).build();
        return new AskStroomAIService(aiService, null, null, null, store, null, null, () -> defaultConfig,
                null, null, null, null, null, null, null, runManager, runDao, null, null, contextAssembler, null,
                compactor, null, toolApprovalService, Mockito.mock(AiAuditLog.class), aiDao, AiConfig::new,
                securityContext, () -> null, chatTitler, null,
                backgroundTasks, null, null, tabSnapshots, new HttpServletRequestHolder());
    }

    @Test
    void stoppingAChatStopsItsRunAndItsBackgroundTasks() {
        Mockito.when(runManager.stop(1, AiChatRunStopReason.USER)).thenReturn(true);
        Mockito.when(backgroundTasks.terminate(1)).thenReturn(2);

        assertThat(service().cancelProcessing(1)).isTrue();

        final InOrder inOrder = Mockito.inOrder(aiService, runManager, backgroundTasks);
        inOrder.verify(aiService).verifyOwnership(1);
        inOrder.verify(runManager).stop(1, AiChatRunStopReason.USER);
        inOrder.verify(backgroundTasks).terminate(1);
    }

    @Test
    void stoppingAChatWithOnlyBackgroundTasksStopsThem() {
        // E.g. search results still downloading to attach, with no question asked yet.
        Mockito.when(runManager.stop(1, AiChatRunStopReason.USER)).thenReturn(false);
        Mockito.when(backgroundTasks.terminate(1)).thenReturn(1);
        assertThat(service().cancelProcessing(1)).isTrue();

        Mockito.when(backgroundTasks.terminate(1)).thenReturn(0);
        assertThat(service().cancelProcessing(1)).isFalse();
    }

    @Test
    void testGetModelDoc_usesTheChosenModel() {
        final OpenAIModelStore store = Mockito.mock(OpenAIModelStore.class);
        final OpenAIModelDoc chosen = OpenAIModelDoc.builder().uuid("chosen-uuid").name("Chosen").build();
        Mockito.when(store.readDocument(CHOSEN_MODEL)).thenReturn(chosen);

        assertThat(serviceForModels(store, DEFAULT_MODEL)
                .getModelDoc(AskStroomAiConfig.builder().modelRef(CHOSEN_MODEL).build()))
                .isSameAs(chosen);
    }

    @Test
    void testGetModelDoc_noModelOrAMissingOneFallsBackToTheDefault() {
        // Users whose settings were saved before a model was set up have none, and a model can be deleted.
        final OpenAIModelStore store = Mockito.mock(OpenAIModelStore.class);
        final OpenAIModelDoc defaultDoc = OpenAIModelDoc.builder().uuid("default-uuid").name("Default").build();
        Mockito.when(store.readDocument(DEFAULT_MODEL)).thenReturn(defaultDoc);
        Mockito.when(store.readDocument(CHOSEN_MODEL)).thenReturn(null);
        final AskStroomAIService service = serviceForModels(store, DEFAULT_MODEL);

        assertThat(service.getModelDoc(AskStroomAiConfig.builder().build())).isSameAs(defaultDoc);
        assertThat(service.getModelDoc(null)).isSameAs(defaultDoc);
        assertThat(service.getModelDoc(AskStroomAiConfig.builder().modelRef(CHOSEN_MODEL).build()))
                .isSameAs(defaultDoc);
    }

    @Test
    void testGetModelDoc_noModelAndNoDefaultSaysWhatToDo() {
        final OpenAIModelStore store = Mockito.mock(OpenAIModelStore.class);
        final AskStroomAIService service = serviceForModels(store, null);

        assertThatThrownBy(() -> service.getModelDoc(AskStroomAiConfig.builder().build()))
                .hasMessageContaining("No AI model is set")
                .hasMessageContaining("Settings");
        assertThatThrownBy(() -> service.getModelDoc(AskStroomAiConfig.builder().modelRef(CHOSEN_MODEL).build()))
                .hasMessageContaining("'Chosen' cannot be found");
    }

    @Test
    void testDeleteChat_stopsAWorkingRunBeforeDeleting() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        // Active until it has been asked to stop.
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running), Optional.of(running), Optional.empty());
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of());

        serviceForDelete().deleteChat(1);

        final InOrder inOrder = Mockito.inOrder(aiService, runManager);
        inOrder.verify(aiService).verifyOwnership(1);
        inOrder.verify(runManager).stop(1, AiChatRunStopReason.USER);
        inOrder.verify(aiService).deleteChat(1);
        Mockito.verify(mcpClientCache).evictChat(1);
    }

    @Test
    void testDeleteChat_aRunThatWillNotStopLeavesTheChat() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.STOPPING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        final AskStroomAIService service = serviceForDelete();
        service.setStopWait(java.time.Duration.ofMillis(300));

        assertThatThrownBy(() -> service.deleteChat(1))
                .isInstanceOf(AiChatRunManager.AiChatBusyException.class);
        Mockito.verify(aiService, Mockito.never()).deleteChat(Mockito.anyInt());
    }

    @Test
    void testDeleteAllChats_deletesEachOfTheUsersChatsAndLeavesBusyOnes() {
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("user1").build());
        Mockito.when(aiDao.getChatIds("user1")).thenReturn(List.of(1, 2, 3));
        final AiChatRun stuck = new AiChatRun(5, 2, AiChatRunStatus.STOPPING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(Mockito.anyInt())).thenReturn(Optional.empty());
        Mockito.when(runDao.getActive(2)).thenReturn(Optional.of(stuck));
        final AskStroomAIService service = serviceForDelete();
        service.setStopWait(java.time.Duration.ofMillis(200));

        assertThat(service.deleteAllChats()).isEqualTo(2);

        Mockito.verify(aiService).deleteChat(1);
        Mockito.verify(aiService, Mockito.never()).deleteChat(2);
        Mockito.verify(aiService).deleteChat(3);
        // Each is checked as deleting one chat is.
        Mockito.verify(aiService).verifyOwnership(1);
        Mockito.verify(aiService).verifyOwnership(3);
    }

    @Test
    void testClearChat_startsTheChatAfreshButKeepsIt() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of(new AiChatAttachment(50, 1, 0L, 0L,
                AiAttachmentStatus.READY, AiAttachmentType.WORKSPACE_FILE, "f", null, false, null)));

        serviceForDelete().clearChat(1);

        final InOrder inOrder = Mockito.inOrder(aiService, attachmentFileStore, memoryService);
        inOrder.verify(aiService).verifyOwnership(1);
        inOrder.verify(aiService).deleteAllChatMessagesAndAttachments(1);
        inOrder.verify(attachmentFileStore).deleteAttachmentFiles(List.of(50));
        // This chat's memory goes; the user's own is kept.
        inOrder.verify(memoryService).deleteByUser(1, "/memories/session/", null);
        Mockito.verify(memoryService, Mockito.never()).deleteByUser(1, "/memories/", null);
        Mockito.verify(runDao).deleteByChat(1);
        Mockito.verify(usageTracker).forget(1);
        // Named afresh by its next question.
        Mockito.verify(aiDao).resetChatTitle(1);
        // The chat itself stays.
        Mockito.verify(aiService, Mockito.never()).deleteChat(Mockito.anyInt());
    }

    @Test
    void testClearChat_aRunThatWillNotStopLeavesTheChatAsItIs() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(
                new AiChatRun(5, 1, AiChatRunStatus.STOPPING, null, 0L, null, false, null)));
        final AskStroomAIService service = serviceForDelete();
        service.setStopWait(java.time.Duration.ofMillis(200));

        assertThatThrownBy(() -> service.clearChat(1)).isInstanceOf(AiChatRunManager.AiChatBusyException.class);
        Mockito.verify(aiService, Mockito.never()).deleteAllChatMessagesAndAttachments(Mockito.anyInt());
        Mockito.verifyNoInteractions(memoryService);
    }

    @Test
    void testDeleteChat_anotherUsersChatIsNotTouched() {
        Mockito.doThrow(new RuntimeException("not yours")).when(aiService).verifyOwnership(1);

        assertThatThrownBy(() -> serviceForDelete().deleteChat(1)).hasMessage("not yours");
        Mockito.verifyNoInteractions(runManager);
        Mockito.verify(aiService, Mockito.never()).deleteChat(Mockito.anyInt());
    }

    @Test
    void testAskStroomAi_theModeIsKeptWithTheChat() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "plan it", AiAgentMode.PLAN));
        // A request without a mode leaves the chat's mode as it is.
        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "and again"));

        Mockito.verify(aiDao, Mockito.times(1)).setAgentMode(1, AiAgentMode.PLAN);
        Mockito.verify(aiDao, Mockito.times(1)).setAgentMode(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAskStroomAi_theActiveTabIsKeptAndRecordedOnTheQuestion() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        runStraightAway();
        final AiTabSnapshot tab = new AiTabSnapshot(new DocRef("XSLT", "x-uuid", "Event translation"),
                "Event translation", "XSLT", true, List.of(), List.of(), null, "hash1");
        Mockito.when(tabSnapshots.store(1, tab)).thenReturn(new TabSnapshots.Stored(42, false));

        final AskStroomAiResponse response = service().askStroomAi(new AskStroomAiRequest(chat, null, null,
                "why?", null, tab));

        assertThat(response.isTabSnapshotNeeded()).isFalse();
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, 42, "why?");
    }

    @Test
    void testAskStroomAi_aTabSentAsItsHashOnlyThatIsNotHeldIsAskedForInFull() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        runStraightAway();
        final AiTabSnapshot tab = new AiTabSnapshot(new DocRef("XSLT", "x-uuid", "Event translation"),
                "Event translation", null, false, null, null, null, "hash1");
        Mockito.when(tabSnapshots.store(1, tab)).thenReturn(new TabSnapshots.Stored(null, true));

        final AskStroomAiResponse response = service().askStroomAi(new AskStroomAiRequest(chat, null, null,
                "why?", null, tab));

        assertThat(response.isTabSnapshotNeeded()).isTrue();
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, null, "why?");
    }

    @Test
    void testAskStroomAi_aTabThatCantBeKeptLeavesTheQuestionWithoutIt() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        runStraightAway();
        final AiTabSnapshot tab = new AiTabSnapshot(new DocRef("XSLT", "x-uuid", "Event translation"),
                "Event translation", null, false, List.of(), List.of(), null, "hash1");
        Mockito.when(tabSnapshots.store(1, tab)).thenThrow(new RuntimeException("The workspace is full"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "why?", null, tab));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, null, "why?");
    }

    @Test
    void testEditMessage_anEditedQuestionIsAskedWithTheTabAsItIsNow() {
        // Asked with one tab, then the user switched to the one they meant and edited the question.
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, 42, "q1", 1, null)));
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        runStraightAway();
        final AiTabSnapshot tab = new AiTabSnapshot(new DocRef("Pipeline", "p1", "Twitter-Events"),
                "Twitter-Events", null, false, List.of(), List.of(), null, "hash2");
        Mockito.when(tabSnapshots.store(1, tab)).thenReturn(new TabSnapshots.Stored(43, false));

        service().editMessage(new EditAiMessageRequest(chat, null, 1, "q1 edited", tab));

        // The old snapshot may be shared with other questions, so is kept; only the question goes.
        Mockito.verify(aiService).deleteMessage(1);
        Mockito.verify(aiService, Mockito.never()).deleteAttachment(42);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, 43, "q1 edited");
    }

    @Test
    void testEditMessage_aQuestionAskedAgainWithNoTabSharedHasNone() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, 42, "q1", 1, null)));
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        runStraightAway();

        service().editMessage(new EditAiMessageRequest(chat, null, 1, null, null));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, null, "q1");
    }

    @Test
    void testIsAttachmentMessage() {
        assertThat(AskStroomAIService.isAttachmentMessage(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 5, "table", 0, null))).isTrue();
        assertThat(AskStroomAIService.isAttachmentMessage(
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, 5, "q", 2, null))).isFalse();
        assertThat(AskStroomAIService.isAttachmentMessage(message(3, AiMessageType.AI_RESPONSE, "a"))).isFalse();
        // A message steering a run refers to the snapshot of the tab sent with it, which isn't its own.
        assertThat(AskStroomAIService.isAttachmentMessage(
                new AiChatMessage(4, 1, 0L, AiMessageType.STEER_MESSAGE, 5, "s", 2, null))).isFalse();
        assertThat(AskStroomAIService.isAttachmentMessage(
                new AiChatMessage(5, 1, 0L, AiMessageType.QUEUED_STEER_MESSAGE, 5, "s", 2, null))).isFalse();
    }

    // Runs a new run's work straight away, ignoring the failure to answer with mocks.
    private void runStraightAway() {
        Mockito.when(runManager.start(Mockito.eq(1), Mockito.any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<AiChatRun> work = invocation.getArgument(1);
            try {
                work.accept(null);
            } catch (final RuntimeException e) {
                // The answer cannot be worked out with mocks, which is fine here.
            }
            return null;
        });
    }

    @Test
    void testAskStroomAi_startsARunWhenNoneIsActive() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "question"));

        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
        Mockito.verify(aiService, Mockito.never())
                .storeMessage(Mockito.eq(1), Mockito.eq(AiMessageType.STEER_MESSAGE), Mockito.any(),
                        Mockito.anyString());
    }

    @Test
    void testAskStroomAi_aMessageWhileAToolWaitsForAFormPutsTheFormAside() {
        // Seen: a message sent while a form waited did nothing until the form was answered or expired.
        final AiChatRun waitingForForm = new AiChatRun(5, 1, AiChatRunStatus.WAITING_FOR_USER,
                AiChatRunStopReason.NEEDS_INPUT, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(waitingForForm));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "use ACME"));

        // Kept for the run to read, and the form put aside, so the tool that asked carries on (unanswered) and the
        // run reads the message next.
        final InOrder inOrder = Mockito.inOrder(aiService, mcpElicitations);
        inOrder.verify(aiService).storeMessage(1, AiMessageType.STEER_MESSAGE, null, "use ACME");
        inOrder.verify(mcpElicitations).supersedePending(1);
        // Nothing is denied, and the run is not carried on, as the tool's thread is still working on it.
        Mockito.verifyNoInteractions(toolApprovalService);
        Mockito.verify(runManager, Mockito.never()).continueRun(Mockito.anyInt(), Mockito.any());
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAskStroomAi_steersAnActiveRun() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        Mockito.when(aiService.storeMessage(1, AiMessageType.QUEUED_STEER_MESSAGE, null, "also check yesterday"))
                .thenReturn(message(9, AiMessageType.QUEUED_STEER_MESSAGE, "also check yesterday"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "also check yesterday"));

        // It waits for the run's current step to finish.
        Mockito.verify(aiService).storeMessage(1, AiMessageType.QUEUED_STEER_MESSAGE, null, "also check yesterday");
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAskStroomAi_theTabSentWithASteerIsKeptAndRecordedOnIt() {
        // Regression: the tab may have changed since the question (e.g. by applying a change the run proposed), so a
        // later change must be proposed against the tab as it is when the user steers the run.
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        final AiTabSnapshot tab = new AiTabSnapshot(new DocRef("XSLT", "x-uuid", "Event translation"),
                "Event translation", "XSLT", true, List.of(), List.of(), null, "hash2");
        Mockito.when(tabSnapshots.store(1, tab)).thenReturn(new TabSnapshots.Stored(43, false));
        Mockito.when(aiService.storeMessage(1, AiMessageType.QUEUED_STEER_MESSAGE, 43, "now map the domain"))
                .thenReturn(message(9, AiMessageType.QUEUED_STEER_MESSAGE, "now map the domain"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "now map the domain", null, tab));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.QUEUED_STEER_MESSAGE, 43, "now map the domain");
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAskStroomAi_runFinishedAsTheSteerArrived() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        // Active when checked, but gone once the message has been stored.
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running), Optional.empty());
        Mockito.when(aiService.storeMessage(1, AiMessageType.QUEUED_STEER_MESSAGE, null, "late"))
                .thenReturn(message(9, AiMessageType.QUEUED_STEER_MESSAGE, "late"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "late"));

        // The steer is taken back and the message is asked as a new question.
        Mockito.verify(aiService).deleteMessage(9);
        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testSendSteerNow_onlyAQueuedMessageOfTheChatIsSentNow() {
        Mockito.when(aiService.changeMessageType(1, 9, Set.of(AiMessageType.QUEUED_STEER_MESSAGE),
                AiMessageType.SEND_NOW_STEER_MESSAGE)).thenReturn(true);

        assertThat(service().sendSteerNow(1, 9)).isTrue();
        // One the run has already picked up, or not in the chat, isn't changed.
        assertThat(service().sendSteerNow(1, 10)).isFalse();
        Mockito.verify(aiService, Mockito.times(2)).verifyOwnership(1);
    }

    @Test
    void testSuggestPrompts_needsTheChatsOwnerAndAChat() {
        Mockito.doThrow(new PermissionException(null, "Not yours")).when(aiService).verifyOwnership(1);

        assertThatThrownBy(() -> service().suggestPrompts(new SuggestAiPromptsRequest(chat, null, List.of(7),
                null))).isInstanceOf(PermissionException.class);
        assertThat(service().suggestPrompts(new SuggestAiPromptsRequest(null, null, List.of(7), null))).isEmpty();
    }

    @Test
    void testSendSteerNow_needsTheChatsOwner() {
        Mockito.doThrow(new PermissionException(null, "Not yours")).when(aiService).verifyOwnership(1);

        assertThatThrownBy(() -> service().sendSteerNow(1, 9)).isInstanceOf(PermissionException.class);
        Mockito.verify(aiService, Mockito.never()).changeMessageType(Mockito.anyInt(), Mockito.anyInt(),
                Mockito.any(), Mockito.any());
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

        assertThatThrownBy(() -> service().editMessage(new EditAiMessageRequest(chat, null, 2, "x", null)))
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

        service().editMessage(new EditAiMessageRequest(chat, null, 3, "q2 edited", null));

        Mockito.verify(runManager).stop(1, AiChatRunStopReason.EDITED);
        // The edited question and its answer are removed, and the earlier turn is kept.
        Mockito.verify(aiService).deleteMessage(3);
        Mockito.verify(aiService).deleteMessage(4);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(1);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(2);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, null, "q2 edited");
    }

    @Test
    void testEditMessage_keepsWhatWasAttachedSinceTheLastMessageForTheEditedQuestion() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 10, "sent with q1", 0, null),
                message(2, AiMessageType.USER_MESSAGE, "q1"),
                message(3, AiMessageType.AI_RESPONSE, "a1"),
                message(4, AiMessageType.USER_MESSAGE, "q2"),
                message(5, AiMessageType.AI_RESPONSE, "a2"),
                // Attached while the user was editing, so not sent yet.
                new AiChatMessage(6, 1, 0L, AiMessageType.ATTACHMENT, 60, "attached since", 0, null)));
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

        service().editMessage(new EditAiMessageRequest(chat, null, 4, "q2 edited", null));

        Mockito.verify(aiService).deleteMessage(4);
        Mockito.verify(aiService).deleteMessage(5);
        // Neither attachment goes: one belongs to an earlier question, the other to the edited one.
        Mockito.verify(aiService, Mockito.never()).deleteAttachment(Mockito.anyInt());
        Mockito.verify(aiService, Mockito.never()).deleteMessage(6);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, null, "q2 edited");
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

        service().editMessage(new EditAiMessageRequest(chat, null, 1, null, null));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, null, "q1");
    }

    @Test
    void testEditMessage_runThatWillNotStop() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(message(1, AiMessageType.USER_MESSAGE, "q1")));
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        final AskStroomAIService service = service();
        service.setStopWait(java.time.Duration.ofMillis(300));

        assertThatThrownBy(() -> service.editMessage(new EditAiMessageRequest(chat, null, 1, "x", null)))
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

        final AiContextUsage usage = new AiContextUsage(1000, 600L, true, Map.of(), 80, 0, 0L, Map.of());
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
        Mockito.when(runManager.stopFlag(1)).thenReturn(new java.util.concurrent.atomic.AtomicBoolean());
        Mockito.when(compactor.compactNow(Mockito.eq(1), Mockito.any(), Mockito.any(), Mockito.eq("focus"),
                Mockito.any()))
                .thenReturn(false);

        service.compactChat(new CompactAiChatRequest(chat, null, "focus"));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.WARNING, AskStroomAIService.NOTHING_TO_COMPACT);
    }

    @Test
    void testSourceDocUuid() {
        final DocRef dataSource = new DocRef("Index", "index-uuid");
        final DocRef queryDoc = new DocRef("Query", "query-uuid");

        final QueryService queryService = Mockito.mock(QueryService.class);

        assertThat(AskStroomAIService.sourceDocUuid(new DashboardTableContext("d", null,
                DashboardSearchRequest.builder()
                        .search(Search.builder().dataSourceRef(dataSource).build())
                        .build()), queryService)).isEqualTo("index-uuid");
        // A query whose data source can't be found records its Query document instead.
        assertThat(AskStroomAIService.sourceDocUuid(new QueryTableContext("q", null,
                QuerySearchRequest.builder()
                        .searchRequestSource(SearchRequestSource.builder().ownerDocRef(queryDoc).build())
                        .build()), queryService)).isEqualTo("query-uuid");
        assertThat(AskStroomAIService.sourceDocUuid(new DashboardTableContext("d", null, null), queryService))
                .isNull();
        assertThat(AskStroomAIService.sourceDocUuid(new GeneralTableContext("g", List.of(), List.of()),
                queryService)).isNull();
    }

    @Test
    void testSourceDocUuid_aQueryTableRecordsTheDataSourceItReads() {
        // Regression test: a query table recorded its Query document, so losing Use on the index it read did not
        // make the table unavailable.
        final QueryService queryService = Mockito.mock(QueryService.class);
        final String query = "from \"Logons\" select UserId";
        Mockito.when(queryService.getReferencedDataSource(query))
                .thenReturn(Optional.of(new DocRef("Index", "index-uuid", "Logons")));
        final QueryTableContext context = new QueryTableContext("q", null, QuerySearchRequest.builder()
                .searchRequestSource(SearchRequestSource.builder()
                        .ownerDocRef(new DocRef("Query", "query-uuid"))
                        .build())
                .query(query)
                .build());

        assertThat(AskStroomAIService.sourceDocUuid(context, queryService)).isEqualTo("index-uuid");

        // A query that can't be parsed falls back to its Query document.
        Mockito.when(queryService.getReferencedDataSource(query)).thenThrow(new RuntimeException("bad query"));
        assertThat(AskStroomAIService.sourceDocUuid(context, queryService)).isEqualTo("query-uuid");
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
    void testAnsweringQuestionsTheRunStoppedForCarriesItOn() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(10, AiMessageType.USER_MESSAGE, "find the failed logons")));
        Mockito.when(aiService.getChat(1)).thenReturn(chat);
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage(null, null, null, 0, 0));
        Mockito.when(mcpElicitations.answer(1, 20, "ACCEPT", Map.of("q1", "Production"))).thenReturn(true);

        assertThat(service().answerElicitation(new AnswerAiElicitationRequest(chat, 20, "ACCEPT",
                Map.of("q1", "Production")))).isTrue();

        Mockito.verify(runManager).continueRun(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testAnsweringAFormAToolWaitsForDoesNotCarryOnTheRun() {
        // The tool's call is still working, and carries on with the answer.
        Mockito.when(mcpElicitations.answer(1, 20, "DECLINE", null)).thenReturn(false);

        assertThat(service().answerElicitation(new AnswerAiElicitationRequest(chat, 20, "DECLINE", null)))
                .isTrue();

        Mockito.verify(runManager, Mockito.never()).continueRun(Mockito.anyInt(), Mockito.any());
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAMessageSentInsteadOfAnsweringQuestionsPutsThemAside() {
        final AiChatRun waiting = new AiChatRun(5, 1, AiChatRunStatus.WAITING_FOR_USER,
                AiChatRunStopReason.NEEDS_ANSWERS, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(waiting));
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(10, AiMessageType.USER_MESSAGE, "find the failed logons")));
        Mockito.when(aiService.getChat(1)).thenReturn(chat);
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage(null, null, null, 0, 0));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "just production"));

        // The run carries on: the call that asked is told they weren't answered, and the model reads the message.
        final InOrder inOrder = Mockito.inOrder(aiService, mcpElicitations, runManager);
        inOrder.verify(aiService).storeMessage(1, AiMessageType.STEER_MESSAGE, null, "just production");
        inOrder.verify(mcpElicitations).supersedePending(1);
        inOrder.verify(runManager).continueRun(Mockito.eq(1), Mockito.any());
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

        Mockito.verify(aiService).storeMessage(1, AiMessageType.STEER_MESSAGE, null, "no, use the other name");
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
                new AiChatStatus(1, "chat", 0L, AiChatRunStatus.WAITING_FOR_USER, true, false, 0L, null, 0)));

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

        final AiChatPollResponse response = service().pollMessages(1, new AiChatPollRequest(2, false, null));

        assertThat(response.getNewMessages()).extracting(AiChatMessage::getId).containsExactly(3);
        assertThat(response.getStreamingMessages()).containsExactly(streamed);
    }

    @Test
    void testPollMessages_aRunSeenWaitingForTheUserComesWithWhatItAsked() {
        // Seen: a form asked while a poll was being answered was never shown. The poll read the messages before
        // the form was stored, and the run once it was waiting for the user, so the client stopped polling.
        // Here the run asks between the poll's two reads, whichever comes first.
        final AiChatMessage form = message(8, AiMessageType.ELICITATION, "{\"status\":\"PENDING\"}");
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        final AiChatRun waiting = new AiChatRun(5, 1, AiChatRunStatus.WAITING_FOR_USER,
                AiChatRunStopReason.NEEDS_INPUT, 0L, null, false, null);
        final AtomicInteger reads = new AtomicInteger();
        Mockito.when(runDao.getLatest(1)).thenAnswer(invocation -> Optional.of(reads.getAndIncrement() == 0
                ? running
                : waiting));
        Mockito.when(runDao.interruptStale(Mockito.anyLong())).thenReturn(List.of());
        Mockito.when(aiService.getMessagesSince(1, 2)).thenAnswer(invocation -> reads.getAndIncrement() == 0
                ? List.of()
                : List.of(form));
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of());
        Mockito.when(aiService.getWorkingMessage(1)).thenReturn(Optional.empty());

        final AiChatPollResponse response = service().pollMessages(1, new AiChatPollRequest(2, false, null));

        // Either the run is seen still running, so the client polls again, or the form comes with it.
        if (response.getRun().getStatus() == AiChatRunStatus.WAITING_FOR_USER) {
            assertThat(response.getNewMessages()).contains(form);
        } else {
            assertThat(response.isComplete()).isFalse();
        }
    }

    @Test
    void testPollMessages_nothingIsStreamedWithoutAnActiveRun() {
        Mockito.when(runDao.getLatest(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of());
        Mockito.when(aiService.getWorkingMessage(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getMessagesSince(1, 2)).thenReturn(List.of());

        final AiChatPollResponse response = service().pollMessages(1, new AiChatPollRequest(2, false, null));

        assertThat(response.getStreamingMessages()).isEmpty();
        Mockito.verify(aiService, Mockito.never()).getStreamingMessages(1);
    }

    @Test
    void testPollMessages_watchedFormsAreSentAsTheyNowStand() {
        Mockito.when(runDao.getLatest(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of());
        Mockito.when(aiService.getWorkingMessage(1)).thenReturn(Optional.empty());
        Mockito.when(aiService.getMessagesSince(1, 9)).thenReturn(List.of());
        // A form cancelled as the run stopped, a steering message the run has picked up, and a message that is
        // neither.
        final AiChatMessage cancelled = message(4, AiMessageType.ELICITATION, "{\"status\":\"CANCELLED\"}");
        final AiChatMessage sent = message(5, AiMessageType.STEER_MESSAGE, "also fw2");
        Mockito.when(aiDao.getMessagesSince(1, 3)).thenReturn(List.of(
                cancelled,
                sent,
                message(6, AiMessageType.TOOL_RESULT, "secret")));

        final AiChatPollResponse response = service().pollMessages(1,
                new AiChatPollRequest(9, false, List.of(4, 5, 6)));

        assertThat(response.getWatchedMessages()).containsExactly(cancelled, sent);
        // Nothing watched, nothing looked up.
        assertThat(service().pollMessages(1, new AiChatPollRequest(9, false, List.of())).getWatchedMessages())
                .isNull();
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, 0, null);
    }

    @Test
    void testSnippetsShowTheMatchInContext() {
        final String question = "word ".repeat(40) + "the FIREWALL dropped packets\nfrom fw1 " + "tail ".repeat(80);

        final String snippet = AskStroomAIService.snippet(question, "firewall");

        assertThat(snippet)
                .startsWith("…")
                .endsWith("…")
                .contains("the FIREWALL dropped packets from fw1")
                .doesNotContain("\n");
        assertThat(snippet.length()).isLessThanOrEqualTo(AskStroomAIService.SNIPPET_CHARS + 2);
        // A short question is shown whole.
        assertThat(AskStroomAIService.snippet("Is fw1 up?", "fw1")).isEqualTo("Is fw1 up?");
        assertThat(AskStroomAIService.snippet(null, "fw1")).isNull();
    }

    @Test
    void testSearchingWithTooLittleTextFindsNothing() {
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("user1").build());

        assertThat(serviceForDelete().searchChats(" a ")).isEmpty();
        Mockito.verify(aiDao, Mockito.never()).searchChats(Mockito.any(), Mockito.any(), Mockito.anyInt());

        serviceForDelete().searchChats(" fw ");
        Mockito.verify(aiDao).searchChats("user1", "fw", AskStroomAIService.MAX_SEARCH_HITS);
    }


    @Test
    void testDebugDetailFollowsTheUsersOwnSetting() {
        final AskStroomAiConfig on = AskStroomAiConfig.builder().enableDebugDetail(true).build();
        final AskStroomAiConfig off = AskStroomAiConfig.builder().enableDebugDetail(false).build();

        // The user's setting wins over the default, either way.
        assertThat(AskStroomAIService.isDebugDetailEnabled(off, on)).isFalse();
        assertThat(AskStroomAIService.isDebugDetailEnabled(on, off)).isTrue();
        // Without the user's settings, the default applies.
        assertThat(AskStroomAIService.isDebugDetailEnabled(null, off)).isFalse();
        assertThat(AskStroomAIService.isDebugDetailEnabled(null, null))
                .isEqualTo(AskStroomAiConfig.DEFAULT_ENABLE_DEBUG_DETAIL);
    }
}
