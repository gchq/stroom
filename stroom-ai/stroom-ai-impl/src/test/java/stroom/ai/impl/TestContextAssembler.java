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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.workspace.WorkspaceStore;
import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiSteppingContext;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextAssembler {

    @Test
    void testToolLog() {
        final List<AiChatMessage> toolMessages = List.of(
                message(1, AiMessageType.TOOL_CALL,
                        new ToolCallRecord("c1", "stroom__find_documents", "{\"name\":\"fw\"}", "looking")),
                message(2, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c1", "stroom__find_documents", false, "Found 2 feeds", null,
                                13, 5, null)),
                message(3, AiMessageType.TOOL_CALL,
                        new ToolCallRecord("c2", "run_stroomql", "{\"query\":\"from x\"}", null)),
                message(4, AiMessageType.TOOL_RESULT,
                        new ToolResultRecord("c2", "run_stroomql", false, "stub", "/outputs/turn-1/call-3.txt",
                                90_000, 50, null)));

        assertThat(ContextAssembler.toolLog(toolMessages)).isEqualTo("""
                Tools you used to answer this:
                - stroom__find_documents({"name":"fw"}) -> Found 2 feeds
                - run_stroomql({"query":"from x"}) -> result saved to /outputs/turn-1/call-3.txt""");
    }

    @Test
    void testToolLog_none() {
        assertThat(ContextAssembler.toolLog(null)).isNull();
        assertThat(ContextAssembler.toolLog(List.of())).isNull();
    }

    @Test
    void testToolLog_longArgumentsAndResultsAreShortened() {
        final String longText = "y".repeat(500);
        final List<AiChatMessage> toolMessages = List.of(
                message(1, AiMessageType.TOOL_CALL, new ToolCallRecord("c1", "t", longText, null)),
                message(2, AiMessageType.TOOL_RESULT, new ToolResultRecord("c1", "t", false, longText, null,
                        500, 1, null)));

        assertThat(ContextAssembler.toolLog(toolMessages))
                .contains("y".repeat(ContextAssembler.TOOL_LOG_ARGS_LIMIT - 1) + "…")
                .hasSizeLessThan(500);
    }

    @Test
    void testEstimateUsage() {
        final stroom.ai.api.AiService aiService = Mockito.mock(stroom.ai.api.AiService.class);
        final AiDao aiDao = Mockito.mock(AiDao.class);
        final stroom.ai.impl.AiWorkspaceDao workspaceDao = Mockito.mock(stroom.ai.impl.AiWorkspaceDao.class);
        final ContextUsageTracker tracker = Mockito.mock(ContextUsageTracker.class);
        final ContextAssembler assembler = new ContextAssembler(
                () -> null, AiConfig::new, null, aiService, aiDao, workspaceDao, tracker, null, null);
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "table", 1, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 1, 100),
                new AiChatMessage(3, 1, 0L, AiMessageType.AI_RESPONSE, null, "a", 1, 200),
                // Not sent to the model.
                new AiChatMessage(4, 1, 0L, AiMessageType.ERROR, null, "x".repeat(4_000), 1, null),
                new AiChatMessage(5, 1, 0L, AiMessageType.USER_MESSAGE, null, "q2", 2, 50)));
        Mockito.when(workspaceDao.getSize(50)).thenReturn(4_000L);
        Mockito.when(tracker.getToolDefinitionTokens(1)).thenReturn(300);
        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.empty());
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage("m", 10_000, null, 0, 0));

        final stroom.ai.shared.AiContextUsage usage = assembler.estimateUsage(1);

        assertThat(usage.getParts())
                .containsEntry(ContextAssembler.PART_CONVERSATION, 350L)
                .containsEntry(ContextAssembler.PART_ATTACHMENTS, 1_000L)
                .containsEntry(ContextAssembler.PART_TOOL_DEFINITIONS, 300L)
                .containsEntry(ContextAssembler.PART_TOOL_RESULTS, 0L)
                .containsEntry(ContextAssembler.PART_EARLIER_TURNS, 0L);
        assertThat(usage.isEstimated()).isTrue();
        assertThat(usage.getContextWindowTokens()).isEqualTo(10_000);
        final long sum = usage.getParts().values().stream().mapToLong(Long::longValue).sum();
        assertThat(usage.getUsedTokens()).isEqualTo(sum);
        assertThat(usage.getPercentUsed()).isEqualTo((int) (sum * 100 / 10_000));

        // Once a call has been measured, the estimate is corrected.
        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.of(2.0));
        final stroom.ai.shared.AiContextUsage corrected = assembler.estimateUsage(1);
        assertThat(corrected.isEstimated()).isFalse();
        assertThat(corrected.getUsedTokens()).isEqualTo(sum * 2);
    }

    @Test
    void testTheEstimateForCompactingErrsLargeUntilACallIsMeasured() {
        final ContextUsageTracker tracker = Mockito.mock(ContextUsageTracker.class);
        final ContextAssembler assembler = new ContextAssembler(
                () -> null, AiConfig::new, null, Mockito.mock(stroom.ai.api.AiService.class),
                Mockito.mock(AiDao.class), Mockito.mock(AiWorkspaceDao.class), tracker, null, null);
        final List<ChatMessage> messages = List.of(UserMessage.from("x".repeat(4_000)));
        Mockito.when(tracker.getToolDefinitionTokens(1)).thenReturn(100);
        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.empty());

        final long unmeasured = assembler.estimateTokens(1, messages);

        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.of(1.0));
        final long measured = assembler.estimateTokens(1, messages);
        // As the agent loop estimates, so the chat is compacted before the run has to shorten its own steps.
        assertThat(unmeasured).isEqualTo(Math.round(measured * ContextUsageTracker.UNMEASURED_SAFETY_FACTOR));
        Mockito.when(tracker.getCorrection(1)).thenReturn(java.util.Optional.of(2.0));
        assertThat(assembler.estimateTokens(1, messages)).isEqualTo(measured * 2);
    }

    @Test
    void testUsageWhileAnsweringIsWhatTheModelWasLastSentAndWhatHasBeenAddedSince() {
        final stroom.ai.api.AiService aiService = Mockito.mock(stroom.ai.api.AiService.class);
        final AiDao aiDao = Mockito.mock(AiDao.class);
        final ContextUsageTracker tracker = Mockito.mock(ContextUsageTracker.class);
        final ContextAssembler assembler = new ContextAssembler(
                () -> null, AiConfig::new, null, aiService, aiDao, Mockito.mock(AiWorkspaceDao.class), tracker,
                null, null);
        final List<AiChatMessage> answering = List.of(
                new AiChatMessage(10, 1, 1_000L, AiMessageType.USER_MESSAGE, null, "onboard it", 10, 5),
                // Stored at full size, though the run has shortened it in what it sends.
                new AiChatMessage(11, 1, 1_500L, AiMessageType.TOOL_RESULT, null, "{}", 10, 90_000),
                // Since the last call, so the next call adds it.
                new AiChatMessage(12, 1, 2_500L, AiMessageType.TOOL_RESULT, null, "{}", 10, 300));
        Mockito.when(aiService.getMessages(1)).thenReturn(answering);
        Mockito.when(tracker.getLastCall(1)).thenReturn(java.util.Optional.of(
                new ContextUsageTracker.MeasuredCall(5_000, 2_000L)));
        Mockito.when(tracker.getShortenedParts(1)).thenReturn(7);
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage("m", 10_000, null, 0, 0));

        final stroom.ai.shared.AiContextUsage usage = assembler.estimateUsage(1);

        assertThat(usage.getUsedTokens()).isEqualTo(5_300L);
        assertThat(usage.isEstimated()).isFalse();
        assertThat(usage.getShortenedParts()).isEqualTo(7);
        // The parts are as stored, for the breakdown.
        assertThat(usage.getParts()).containsEntry(ContextAssembler.PART_TOOL_RESULTS, 90_300L);

        // Never more than the window the model accepted the call within.
        Mockito.when(aiDao.getUsage(1)).thenReturn(new AiDao.ChatUsage("m", 5_100, null, 0, 0));
        assertThat(assembler.estimateUsage(1).getPercentUsed()).isEqualTo(100);

        // A call measured before the question, or a question answered, is the stored estimate.
        Mockito.when(tracker.getLastCall(1)).thenReturn(java.util.Optional.of(
                new ContextUsageTracker.MeasuredCall(5_000, 500L)));
        assertThat(assembler.estimateUsage(1).getShortenedParts()).isZero();
        Mockito.when(tracker.getLastCall(1)).thenReturn(java.util.Optional.of(
                new ContextUsageTracker.MeasuredCall(5_000, 2_000L)));
        final List<AiChatMessage> answered = new java.util.ArrayList<>(answering);
        answered.add(new AiChatMessage(13, 1, 3_000L, AiMessageType.AI_RESPONSE, null, "done", 10, 2));
        Mockito.when(aiService.getMessages(1)).thenReturn(answered);
        assertThat(assembler.estimateUsage(1).getShortenedParts()).isZero();
    }

    @Test
    void testUnavailableAttachmentIsSaidToBeGone() {
        final stroom.ai.api.AiService aiService = Mockito.mock(stroom.ai.api.AiService.class);
        final AiWorkspaceDao workspaceDao = Mockito.mock(AiWorkspaceDao.class);
        final ContextUsageTracker tracker = Mockito.mock(ContextUsageTracker.class);
        final ContextAssembler assembler = new ContextAssembler(
                () -> null, AiConfig::new, null, aiService, null, workspaceDao, tracker, null, null);
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "Firewall events", 0, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 2, null));
        Mockito.when(workspaceDao.getFileInfo(50)).thenReturn(java.util.Optional.of(new AiWorkspaceDao.FileInfo(
                50, 1, "/attachments/50.md", AiAttachmentStatus.UNAVAILABLE, null, null,
                "it reached its retention age", 0L)));

        final List<ChatMessage> messages = assembler.assemble(new AskStroomAiRequest(null, null, null, "q"), 1,
                java.util.Map.of(), history, CompactionRecord.NONE);

        assertThat(((UserMessage) messages.get(1)).singleText())
                .contains("'Firewall events' is no longer available (it reached its retention age)")
                .contains("ask the user to attach it again");
    }

    @Test
    void testTheAttachedTabIsNotedForTheLatestQuestionOnly() {
        final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
        final AiWorkspaceDao workspaceDao = Mockito.mock(AiWorkspaceDao.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, workspaceDao,
                Mockito.mock(ContextUsageTracker.class), null, null, tabSnapshots);
        final TabSnapshots.StoredTabSnapshot earlier = tab(7, new DocRef("XSLT", "x", "Event translation"));
        final TabSnapshots.StoredTabSnapshot latest = tab(8, new DocRef("XSLT", "x", "Event translation"));
        Mockito.when(tabSnapshots.isEnabled()).thenReturn(true);
        Mockito.when(tabSnapshots.read(71)).thenReturn(java.util.Optional.of(earlier));
        Mockito.when(tabSnapshots.read(81)).thenReturn(java.util.Optional.of(latest));
        Mockito.when(tabSnapshots.note(1, latest)).thenReturn("[Attached tab: XSLT 'Event translation'.]");
        Mockito.when(workspaceDao.getFileInfo(71)).thenReturn(java.util.Optional.of(new AiWorkspaceDao.FileInfo(
                71, 1, "/tabs/7/snapshot.json", AiAttachmentStatus.READY, "x", null, null, 10L)));
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, 71, "first", 1, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.AI_RESPONSE, null, "a1", 1, null),
                new AiChatMessage(3, 1, 0L, AiMessageType.USER_MESSAGE, 81, "second", 3, null));

        final List<ChatMessage> messages = assembler.assemble(new AskStroomAiRequest(null, null, null, "second"),
                1, java.util.Map.of(), history, CompactionRecord.NONE, true);

        assertThat(((UserMessage) messages.get(1)).singleText())
                .isEqualTo("[Asked with a tab attached; its snapshot is in /tabs/7/]\nfirst");
        assertThat(((UserMessage) messages.getLast()).singleText())
                .isEqualTo("second\n\n[Attached tab: XSLT 'Event translation'.]");
        Mockito.verify(tabSnapshots, Mockito.never()).note(1, earlier);
        // Not a query, so no query guidance.
        assertThat(((SystemMessage) messages.getFirst()).text()).doesNotContain(ContextAssembler.QUERY_GUIDANCE);
    }

    @Test
    void testQueryGuidanceIsGivenOnlyWhileAQueryTabIsActive() {
        final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null, null, tabSnapshots);
        final TabSnapshots.StoredTabSnapshot query = tab(2, new DocRef("Query", "q", "Logons"));
        Mockito.when(tabSnapshots.isEnabled()).thenReturn(true);
        Mockito.when(tabSnapshots.read(21)).thenReturn(java.util.Optional.of(query));
        Mockito.when(tabSnapshots.note(Mockito.anyInt(), Mockito.any())).thenReturn("[Attached tab]");
        final AskStroomAiRequest request = new AskStroomAiRequest(null, null, null, "q");

        final String withQuery = ((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), List.of(
                        new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, 21, "q", 1, null)),
                CompactionRecord.NONE, true).getFirst()).text();
        final String withoutTab = ((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), List.of(
                        new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 1, null)),
                CompactionRecord.NONE, true).getFirst()).text();

        assertThat(withQuery).contains(ContextAssembler.QUERY_GUIDANCE).contains("validate_stroomql");
        assertThat(withoutTab).doesNotContain(ContextAssembler.QUERY_GUIDANCE);
        // The tools' guidance says when to offer, rather than run, a refresh of the tab's results.
        assertThat(withoutTab).contains("Never pass refresh unless the user has said yes");
    }

    @Test
    void testSteppingGuidanceIsGivenOnlyWhileTheSteppingTabIsActive() {
        final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null, null, tabSnapshots);
        final DocRef pipeline = new DocRef("Pipeline", "p", "Acme-Events");
        final TabSnapshots.StoredTabSnapshot stepping = new TabSnapshots.StoredTabSnapshot(31, 3, pipeline, "/",
                "Acme-Events", "Stepping", false, null, "h", 0L, List.of(), List.of(),
                new AiSteppingContext("ACME", 12L, "Raw Events", null, 1L, 1L, null, List.of(), List.of()));
        // The same pipeline, showing its Structure tab rather than stepping.
        final TabSnapshots.StoredTabSnapshot structure = tab(4, pipeline);
        Mockito.when(tabSnapshots.isEnabled()).thenReturn(true);
        Mockito.when(tabSnapshots.read(31)).thenReturn(java.util.Optional.of(stepping));
        Mockito.when(tabSnapshots.read(41)).thenReturn(java.util.Optional.of(structure));
        Mockito.when(tabSnapshots.note(Mockito.anyInt(), Mockito.any())).thenReturn("[Attached tab]");
        final AskStroomAiRequest request = new AskStroomAiRequest(null, null, null, "q");

        final String whileStepping = systemText(assembler, request, 31);
        final String notStepping = systemText(assembler, request, 41);
        final String withoutTab = systemText(assembler, request, null);

        assertThat(whileStepping).contains(ContextAssembler.STEPPING_GUIDANCE)
                .contains("propose_code_change")
                .doesNotContain(ContextAssembler.QUERY_GUIDANCE);
        assertThat(notStepping).doesNotContain(ContextAssembler.STEPPING_GUIDANCE);
        assertThat(withoutTab).doesNotContain(ContextAssembler.STEPPING_GUIDANCE);
    }

    @Test
    void testDashboardGuidanceIsGivenOnlyWhileADashboardTabIsActive() {
        final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null, null, tabSnapshots);
        Mockito.when(tabSnapshots.isEnabled()).thenReturn(true);
        Mockito.when(tabSnapshots.read(31)).thenReturn(java.util.Optional.of(tab(3,
                new DocRef("Dashboard", "d", "Logons"))));
        Mockito.when(tabSnapshots.read(41)).thenReturn(java.util.Optional.of(tab(4,
                new DocRef("XSLT", "x", "Event translation"))));
        Mockito.when(tabSnapshots.note(Mockito.anyInt(), Mockito.any())).thenReturn("[Attached tab]");
        final AskStroomAiRequest request = new AskStroomAiRequest(null, null, null, "q");

        final String withDashboard = systemText(assembler, request, 31);
        final String withXslt = systemText(assembler, request, 41);
        final String withoutTab = systemText(assembler, request, null);

        assertThat(withDashboard).contains(ContextAssembler.DASHBOARD_GUIDANCE)
                .contains("propose_dashboard_change")
                .contains("withEventIds")
                .doesNotContain(ContextAssembler.QUERY_GUIDANCE)
                .doesNotContain(ContextAssembler.STEPPING_GUIDANCE);
        assertThat(withXslt).doesNotContain(ContextAssembler.DASHBOARD_GUIDANCE);
        assertThat(withoutTab).doesNotContain(ContextAssembler.DASHBOARD_GUIDANCE);
    }

    private static String systemText(final ContextAssembler assembler,
                                     final AskStroomAiRequest request,
                                     final Integer snapshotId) {
        return ((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), List.of(
                        new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, snapshotId, "q", 1, null)),
                CompactionRecord.NONE, true).getFirst()).text();
    }

    private static TabSnapshots.StoredTabSnapshot tab(final int number, final DocRef docRef) {
        return new TabSnapshots.StoredTabSnapshot(number * 10 + 1, number, docRef, "/", docRef.getName(), null,
                false, null, "h", 0L, List.of(), List.of(), null);
    }

    @Test
    void testOnlyModelsWithToolsAreToldAboutThem() {
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null, null);
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 1, null));
        final AskStroomAiRequest request = new AskStroomAiRequest(null, null, null, "q");

        final String withTools = ((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), history,
                CompactionRecord.NONE, true).getFirst()).text();
        final String withoutTools = ((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), history,
                CompactionRecord.NONE, false).getFirst()).text();

        assertThat(withTools)
                .startsWith(AskStroomAiConfig.DEFAULT_CHAT_SYSTEM_PROMPT)
                .contains("## Tools")
                .contains("read_turn and search_history");
        assertThat(withoutTools)
                .isEqualTo(AskStroomAiConfig.DEFAULT_CHAT_SYSTEM_PROMPT);
    }

    @Test
    void testConversationContextIsTheChatAsCompacted() {
        final stroom.ai.api.AiService aiService = Mockito.mock(stroom.ai.api.AiService.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null, aiService, null,
                Mockito.mock(AiWorkspaceDao.class), Mockito.mock(ContextUsageTracker.class), null, null);
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, null, "first question", 1, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.AI_RESPONSE, null, "a long first answer", 1, null),
                new AiChatMessage(3, 1, 0L, AiMessageType.COMPACTION_SUMMARY, null,
                        "{\"digestsUpToTurnId\":1,\"summaryUpToTurnId\":1,\"summary\":\"They asked about "
                        + "firewalls.\"}", 1, null),
                new AiChatMessage(4, 1, 0L, AiMessageType.USER_MESSAGE, null, "second question", 4, null),
                new AiChatMessage(5, 1, 0L, AiMessageType.AI_RESPONSE, null, "second answer", 4, null),
                new AiChatMessage(6, 1, 0L, AiMessageType.USER_MESSAGE, null, "the current question", 6, null)));

        final String context = assembler.conversationContext(1, 1_000);

        // Earlier turns as the model sees them, then later ones in full, without the question being answered.
        assertThat(context)
                .contains("They asked about firewalls.")
                .doesNotContain("a long first answer")
                .endsWith("User: second question\nAI: second answer")
                .doesNotContain("the current question");

        // The most recent turns are kept when there is little room.
        assertThat(assembler.conversationContext(1, 5)).isEqualTo("AI: second answer");
        assertThat(assembler.conversationContext(1, 0)).isEmpty();
    }

    @Test
    void testAskAndPlanModesAreExplainedToTheModel() {
        final AiDao aiDao = Mockito.mock(AiDao.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), aiDao, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null, null);
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 1, null));
        final AskStroomAiRequest request = new AskStroomAiRequest(null, null, null, "q");

        Mockito.when(aiDao.getAgentMode(1)).thenReturn(AiAgentMode.AGENT);
        // So that after a switch from Plan mode, the model knows it can now do what it planned.
        assertThat(((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), history,
                CompactionRecord.NONE, true).getFirst()).text()).contains(ContextAssembler.AGENT_GUIDANCE);
        // A model without tools has nothing to change things with.
        assertThat(((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), history,
                CompactionRecord.NONE, false).getFirst()).text()).doesNotContain("## Mode");

        Mockito.when(aiDao.getAgentMode(1)).thenReturn(AiAgentMode.ASK);
        assertThat(((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), history,
                CompactionRecord.NONE, true).getFirst()).text()).contains(ContextAssembler.ASK_GUIDANCE);

        Mockito.when(aiDao.getAgentMode(1)).thenReturn(AiAgentMode.PLAN);
        assertThat(((SystemMessage) assembler.assemble(request, 1, java.util.Map.of(), history,
                CompactionRecord.NONE, true).getFirst()).text()).contains(ContextAssembler.PLAN_GUIDANCE);
    }

    @Test
    void testTablesAreGivenAsProfilesToModelsWithTools(@TempDir final Path tempDir) throws IOException {
        final AiWorkspaceDao workspaceDao = Mockito.mock(AiWorkspaceDao.class);
        final WorkspaceStore workspaceStore = Mockito.mock(WorkspaceStore.class);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, workspaceStore,
                Mockito.mock(stroom.ai.api.AiService.class), null, workspaceDao,
                Mockito.mock(ContextUsageTracker.class), null, null);
        final AiChatAttachment attachment = new AiChatAttachment(50, 1, 0L, 0L, AiAttachmentStatus.READY,
                AiAttachmentType.DASHBOARD, "Firewall events", 2, false, null);
        final Path table = tempDir.resolve("50.md");
        Files.writeString(table, "| host |\n| --- |\n| fw1 |\n| fw2 |\n");
        final Path profile = tempDir.resolve("51.md");
        Files.writeString(profile, "Profile of Firewall events: 2 rows, 1 columns.");
        Mockito.when(workspaceStore.localFile(50)).thenReturn(table);
        Mockito.when(workspaceStore.localFile(51)).thenReturn(profile);
        Mockito.when(workspaceDao.findFile(1, "/attachments/50.profile.md")).thenReturn(java.util.Optional.of(51));
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "Firewall events", 0, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 2, null));
        final AskStroomAiRequest request = new AskStroomAiRequest(null, null, null, "q");

        final String withTools = ((UserMessage) assembler.assemble(request, 1, java.util.Map.of(50, attachment),
                history, CompactionRecord.NONE, true).get(1)).singleText();
        final String withoutTools = ((UserMessage) assembler.assemble(request, 1, java.util.Map.of(50, attachment),
                history, CompactionRecord.NONE, false).get(1)).singleText();

        assertThat(withTools)
                .contains("The whole table is the workspace file /attachments/50.md")
                .contains("Profile of Firewall events")
                .doesNotContain("| fw2 |");
        assertThat(withoutTools).contains("| fw2 |").doesNotContain("Profile of");
    }

    @Test
    void testModelsWithoutToolsAreGivenOnlySoManyRows(@TempDir final Path tempDir) throws IOException {
        final WorkspaceStore workspaceStore = Mockito.mock(WorkspaceStore.class);
        final stroom.ai.shared.AskStroomAiConfig config = stroom.ai.shared.AskStroomAiConfig.builder()
                .tableAnalysisConfig(stroom.ai.shared.TableAnalysisConfig.builder().maxRowsWithoutTools(2).build())
                .build();
        final ContextAssembler assembler = new ContextAssembler(() -> config, AiConfig::new, workspaceStore,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null, null);
        final AiChatAttachment attachment = new AiChatAttachment(50, 1, 0L, 0L, AiAttachmentStatus.READY,
                AiAttachmentType.DASHBOARD, "Firewall events", 3, false, null);
        final Path table = tempDir.resolve("50.md");
        Files.writeString(table, "| host |\n| --- |\n| fw1 |\n| fw2 |\n| fw3 |\n");
        Mockito.when(workspaceStore.localFile(50)).thenReturn(table);
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "Firewall events", 0, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 2, null));

        final String text = ((UserMessage) assembler.assemble(new AskStroomAiRequest(null, null, null, "q"), 1,
                java.util.Map.of(50, attachment), history, CompactionRecord.NONE, false).get(1)).singleText();

        assertThat(text)
                .contains("| host |\n| --- |\n| fw1 |\n| fw2 |\n")
                .doesNotContain("fw3")
                .contains("Only the first 2 rows are given here");
    }

    @Test
    void testCurrentTurnIsReplayed() {
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.USER_MESSAGE, "earlier"),
                message(2, AiMessageType.AI_RESPONSE, "earlier answer"),
                message(10, AiMessageType.USER_MESSAGE, "q"),
                message(11, AiMessageType.TOOL_CALL, new ToolCallRecord("a", "t", "{}", null)),
                message(12, AiMessageType.TOOL_RESULT, new ToolResultRecord("a", "t", false, "r1", null, 2, 1, null)),
                message(13, AiMessageType.STEER_MESSAGE, "also b"),
                // Waiting for approval: left to the agent loop.
                message(14, AiMessageType.TOOL_CALL, new ToolCallRecord("b", "t", "{}", null)));

        final List<ChatMessage> replay = ContextAssembler.currentTurn(history);

        assertThat(replay).hasSize(3);
        assertThat(((AiMessage) replay.get(0)).toolExecutionRequests().getFirst().id()).isEqualTo("a");
        assertThat(((ToolExecutionResultMessage) replay.get(1)).text()).isEqualTo("r1");
        assertThat(((UserMessage) replay.get(2)).singleText()).isEqualTo(McpAgentChat.STEER_PREFIX + "also b");
        assertThat(ContextAssembler.currentTurn(List.of())).isEmpty();
    }

    @Test
    void testCurrentTurnIsReplayedAsTheModelsStepsWithWhatItSaid() {
        final List<AiChatMessage> history = List.of(
                message(10, AiMessageType.USER_MESSAGE, "q"),
                // Two calls made together, then one on its own.
                message(11, AiMessageType.TOOL_CALL, new ToolCallRecord("a", "t", "{}", "Checking both feeds.")),
                message(12, AiMessageType.TOOL_CALL, new ToolCallRecord("b", "t", "{}", "Checking both feeds.")),
                message(13, AiMessageType.TOOL_RESULT, new ToolResultRecord("a", "t", false, "r1", null, 2, 1, null)),
                message(14, AiMessageType.TOOL_RESULT, new ToolResultRecord("b", "t", false, "r2", null, 2, 1, null)),
                message(15, AiMessageType.TOOL_CALL, new ToolCallRecord("c", "t", "{}", "Now the pipeline.")),
                message(16, AiMessageType.TOOL_RESULT, new ToolResultRecord("c", "t", false, "r3", null, 2, 1, null)));

        final List<ChatMessage> replay = ContextAssembler.currentTurn(history);

        assertThat(replay).hasSize(5);
        assertThat(((AiMessage) replay.get(0)).text()).isEqualTo("Checking both feeds.");
        assertThat(((AiMessage) replay.get(0)).toolExecutionRequests())
                .extracting(ToolExecutionRequest::id)
                .containsExactly("a", "b");
        assertThat(((ToolExecutionResultMessage) replay.get(1)).text()).isEqualTo("r1");
        assertThat(((ToolExecutionResultMessage) replay.get(2)).text()).isEqualTo("r2");
        assertThat(((AiMessage) replay.get(3)).text()).isEqualTo("Now the pipeline.");
        assertThat(((ToolExecutionResultMessage) replay.get(4)).text()).isEqualTo("r3");
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, id < 10
                ? 1
                : 10, null);
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final Object record) {
        return new AiChatMessage(id, 1, 0L, type, null, JsonUtil.writeValueAsString(record, false), 1, null);
    }

    @Test
    void attachedTablesWithEventIdsMentionTheEventTools() {
        assertThat(ContextAssembler.eventsNote("| Column | Type |\n| User | text |\n| StreamId | number |\n"
                                               + "| EventId | number |"))
                .contains("fetch_events")
                .contains("search_events");
        // A grouped table has no event ids.
        assertThat(ContextAssembler.eventsNote("| Column | Type |\n| User | text |\n| Count | number |")).isEmpty();
    }


    @Test
    void testAReferencedDocumentsNoteSurvivesCompaction() {
        final DocRef dictionary = new DocRef("Dictionary", "dict-uuid", "Malicious IPs");
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        Mockito.when(documentAccess.canView(dictionary)).thenReturn(true);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null,
                new DocumentReferences(Mockito.mock(stroom.ai.api.AiService.class), documentAccess));
        final AiChatAttachment reference = new AiChatAttachment(50, 1, 0L, 0L, AiAttachmentStatus.READY,
                AiAttachmentType.DOCUMENT, "Dictionary 'Malicious IPs'", null, false, null, dictionary,
                "/Security/Lookups");
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "Dictionary 'Malicious IPs'", 1, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q1", 1, null),
                new AiChatMessage(3, 1, 0L, AiMessageType.AI_RESPONSE, null, "a1", 1, null),
                new AiChatMessage(4, 1, 0L, AiMessageType.USER_MESSAGE, null, "q2", 2, null));

        // The turn it was attached in is compacted.
        final List<ChatMessage> messages = assembler.assemble(new AskStroomAiRequest(null, null, null, "q2"), 1,
                java.util.Map.of(50, reference), history,
                new CompactionRecord(1, 1, "Summary", 0, 0, 0, false, false), true);

        assertThat(messages).filteredOn(UserMessage.class::isInstance)
                .extracting(message -> ((UserMessage) message).singleText())
                .contains("[Referenced document: Dictionary 'Malicious IPs' (uuid dict-uuid, /Security/Lookups). "
                          + "Use document_info, read_document or document_documentation with its uuid to see it.]")
                .noneMatch(text -> text.equals("q1"));
    }

    @Test
    void testAReferencedDocumentTheUserCanNoLongerViewIsSaidToBeGone() {
        final DocRef dictionary = new DocRef("Dictionary", "dict-uuid", "Malicious IPs");
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        Mockito.when(documentAccess.canView(dictionary)).thenReturn(false);
        final ContextAssembler assembler = new ContextAssembler(() -> null, AiConfig::new, null,
                Mockito.mock(stroom.ai.api.AiService.class), null, Mockito.mock(AiWorkspaceDao.class),
                Mockito.mock(ContextUsageTracker.class), null,
                new DocumentReferences(Mockito.mock(stroom.ai.api.AiService.class), documentAccess));
        final AiChatAttachment reference = new AiChatAttachment(50, 1, 0L, 0L, AiAttachmentStatus.READY,
                AiAttachmentType.DOCUMENT, "Dictionary 'Malicious IPs'", null, false, null, dictionary,
                "/Security/Lookups");
        final List<AiChatMessage> history = List.of(
                new AiChatMessage(1, 1, 0L, AiMessageType.ATTACHMENT, 50, "Dictionary 'Malicious IPs'", 1, null),
                new AiChatMessage(2, 1, 0L, AiMessageType.USER_MESSAGE, null, "q", 1, null));

        final List<ChatMessage> messages = assembler.assemble(new AskStroomAiRequest(null, null, null, "q"), 1,
                java.util.Map.of(50, reference), history, CompactionRecord.NONE, true);

        assertThat(((UserMessage) messages.get(1)).singleText())
                .contains("The referenced document Dictionary 'Malicious IPs' is no longer available")
                .doesNotContain("dict-uuid")
                .doesNotContain("/Security/Lookups");
    }

    @Test
    void theModelIsToldToAskWhichDataSourceAndWhetherToRunOrCreateAQuery() {
        assertThat(ContextAssembler.TOOL_GUIDANCE)
                .contains("Never choose a data source for the user")
                .contains("ask them which to use with ask_user, one option for each")
                .contains("run a search and give them the results (run_stroomql), or to set up a Query document "
                          + "they can run themselves (propose_query)")
                .contains("in the same ask_user call");
    }
}
