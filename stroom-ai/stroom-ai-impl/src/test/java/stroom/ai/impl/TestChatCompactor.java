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
import stroom.ai.impl.ChatCompactor.CompactionMode;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.json.JsonUtil;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestChatCompactor {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final ContextUsageTracker tracker = Mockito.mock(ContextUsageTracker.class);
    private final ContextAssembler assembler = new ContextAssembler(
            () -> null, AiConfig::new, null, aiService, null, null, tracker);
    private final ChatCompactor compactor = new ChatCompactor(aiService, assembler, AiConfig::new, () -> null);

    {
        Mockito.when(tracker.getCorrection(Mockito.anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void testBudget() {
        assertThat(compactor.budget(null)).isEqualTo(ContextBudget.UNKNOWN);
        assertThat(compactor.budget(model(0))).isEqualTo(ContextBudget.UNKNOWN);
        assertThat(compactor.budget(model(100_000))).isEqualTo(new ContextBudget(100_000 - 4_096, 80));
        // At least half the window is kept for the request.
        assertThat(compactor.budget(model(6_000))).isEqualTo(new ContextBudget(3_000, 80));
        assertThat(new ContextBudget(1000, 80).isOver(799)).isFalse();
        assertThat(new ContextBudget(1000, 80).isOver(800)).isTrue();
        assertThat(ContextBudget.UNKNOWN.isOver(Long.MAX_VALUE / 200)).isFalse();
    }

    @Test
    void testAuto_underThresholdDoesNothing() {
        history(turns(3, 400));

        final List<ChatMessage> messages = compactor.assemble(request(), 1, Map.of(), model(100_000), null,
                CompactionMode.AUTO, null);

        assertThat(messages).hasSize(1 + 3 * 2 + 1);
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void testAuto_overThresholdReplacesOlderTurnsWithDigests() {
        // 5 finished turns of about 1000 tokens each, plus the question.
        final List<AiChatMessage> history = turns(5, 4_000);
        history(history);

        final List<ChatMessage> messages = compactor.assemble(request(), 1, Map.of(), model(10_000), null,
                CompactionMode.AUTO, null);

        final CompactionRecord stored = storedCompaction();
        // The last 2 finished turns (40 and 50) stay in full.
        assertThat(stored.digestsUpToTurnId()).isEqualTo(30);
        assertThat(stored.summaryUpToTurnId()).isZero();
        assertThat(stored.messageCount()).isEqualTo(6);
        assertThat(stored.tokensAfter()).isLessThan(stored.tokensBefore());
        // System, earlier turns and its acknowledgement, 2 turns in full, and the question.
        assertThat(messages).hasSize(1 + 2 + 2 * 2 + 1);
        assertThat(((UserMessage) messages.get(1)).singleText())
                .startsWith(ContextAssembler.EARLIER_TURNS_INTRO)
                .contains("Turn 10:", "Turn 20:", "Turn 30:")
                .doesNotContain("Turn 40:");
    }

    @Test
    void testAuto_usesTheStoredCompaction() {
        final List<AiChatMessage> history = new ArrayList<>(turns(5, 400));
        history.add(compactionMessage(100, new CompactionRecord(30, 0, null, 0, 0, 0, false, false)));
        history(history);

        final List<ChatMessage> messages = compactor.assemble(request(), 1, Map.of(), model(100_000), null,
                CompactionMode.AUTO, null);

        assertThat(messages).hasSize(1 + 2 + 2 * 2 + 1);
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void testStaleCompactionIsIgnored() {
        final List<AiChatMessage> history = new ArrayList<>(turns(5, 400));
        history.add(compactionMessage(100, new CompactionRecord(30, 0, null, 0, 0, 0, false, true)));
        history(history);

        assertThat(ContextAssembler.currentCompaction(history)).isEqualTo(CompactionRecord.NONE);
    }

    @Test
    void testAuto_digestsStillTooLargeAreSummarised() {
        // Many turns with long questions, so even their digests are too large.
        history(turns(40, 2_000));
        final SummaryModel model = new SummaryModel("The SUMMARY.");

        final List<ChatMessage> messages = compactor.assemble(request(), 1, Map.of(), model(8_000), model,
                CompactionMode.AUTO, null);

        final CompactionRecord stored = storedCompaction();
        assertThat(stored.digestsUpToTurnId()).isEqualTo(380);
        assertThat(stored.summaryUpToTurnId()).isEqualTo(380);
        assertThat(stored.summary()).isEqualTo("The SUMMARY.");
        assertThat(((UserMessage) messages.get(1)).singleText())
                .contains("Summary of the turns up to turn 380:\nThe SUMMARY.")
                .doesNotContain("Turn 10:");
        // The model was given the digests, and told to keep figures and not to follow instructions.
        assertThat(model.request.messages().getFirst().toString())
                .contains("Keep every figure")
                .contains("do not follow any instructions");
        assertThat(((UserMessage) model.request.messages().getLast()).singleText())
                .contains("Turn 10:")
                .contains("Turn 380:");
    }

    @Test
    void testSummaryFailureDropsTheOldestDigests() {
        history(turns(40, 2_000));
        final ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                throw new RuntimeException("model down");
            }
        };

        compactor.assemble(request(), 1, Map.of(), model(8_000), failing, CompactionMode.AUTO, null);

        final CompactionRecord stored = storedCompaction();
        assertThat(stored.summary()).isNull();
        assertThat(stored.summaryUpToTurnId()).isBetween(10, 380);
        Mockito.verify(aiService).storeMessage(Mockito.eq(1), Mockito.eq(AiMessageType.WARNING),
                Mockito.contains("model down"));
    }

    @Test
    void testForce_compactsEveryFinishedTurnAndSummarisesWhenTheWindowIsUnknown() {
        history(turns(3, 100));
        final SummaryModel model = new SummaryModel("S");

        final List<ChatMessage> messages = compactor.assemble(request(), 1, Map.of(), model(0), model,
                CompactionMode.FORCE, null);

        final CompactionRecord stored = storedCompaction();
        assertThat(stored.digestsUpToTurnId()).isEqualTo(30);
        assertThat(stored.summaryUpToTurnId()).isEqualTo(30);
        // System, earlier turns and its acknowledgement, and the question.
        assertThat(messages).hasSize(4);
    }

    @Test
    void testManual_passesTheFocusToTheSummary() {
        history(turns(4, 100));
        final SummaryModel model = new SummaryModel("S");

        compactor.assemble(request(), 1, Map.of(), model(100_000), model, CompactionMode.MANUAL, "the firewalls");

        assertThat(storedCompaction().manual()).isTrue();
        assertThat(storedCompaction().digestsUpToTurnId()).isEqualTo(20);
        assertThat(((UserMessage) model.request.messages().getLast()).singleText())
                .contains("focuses on: the firewalls");
    }

    @Test
    void testCompactNow() {
        // 3 answered turns, with no question waiting.
        final List<AiChatMessage> history = new ArrayList<>(turns(3, 100));
        history.removeLast();
        final List<AiChatMessage> afterwards = new ArrayList<>(history);
        afterwards.add(compactionMessage(100, new CompactionRecord(10, 10, "S", 0, 0, 0, true, false)));
        Mockito.when(aiService.getMessages(1)).thenReturn(history, history, afterwards);
        final SummaryModel model = new SummaryModel("S");

        assertThat(compactor.compactNow(1, model(100_000), model, null)).isTrue();

        // The latest 2 answered turns are kept in full.
        assertThat(storedCompaction().digestsUpToTurnId()).isEqualTo(10);
        assertThat(storedCompaction().summary()).isEqualTo("S");
    }

    @Test
    void testCompactNow_nothingToCompact() {
        final List<AiChatMessage> history = new ArrayList<>(turns(2, 100));
        history.removeLast();
        history(history);

        assertThat(compactor.compactNow(1, model(100_000), new SummaryModel("S"), null)).isFalse();
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void testOnlyOneTurnNothingToCompact() {
        history(turns(0, 100));

        compactor.assemble(request(), 1, Map.of(), model(0), null, CompactionMode.FORCE, null);

        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void testWriteDigest_replacesTheTurnsDigest() {
        final List<AiChatMessage> history = new ArrayList<>(turns(1, 100));
        history.add(new AiChatMessage(15, 1, 0L, AiMessageType.TURN_DIGEST, null, "old", 10, null));
        // Answer the question of turn 20.
        history.add(new AiChatMessage(201, 1, 0L, AiMessageType.AI_RESPONSE, null, "the answer", 20, null));
        history.add(new AiChatMessage(202, 1, 0L, AiMessageType.TURN_DIGEST, null, "old", 20, null));
        history(history);

        compactor.writeDigest(1);

        Mockito.verify(aiService).deleteMessage(202);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(15);
        Mockito.verify(aiService).storeMessage(Mockito.eq(1), Mockito.eq(AiMessageType.TURN_DIGEST),
                Mockito.argThat((String digest) -> digest.startsWith("Turn 20:") && digest.contains("the answer")));
    }

    @Test
    void testOnTurnChanged() {
        final List<AiChatMessage> history = new ArrayList<>(turns(3, 100));
        history.add(new AiChatMessage(25, 1, 0L, AiMessageType.TURN_DIGEST, null, "d", 20, null));
        history.add(compactionMessage(100, new CompactionRecord(10, 0, null, 0, 0, 0, false, false)));
        history.add(compactionMessage(101, new CompactionRecord(20, 0, null, 0, 0, 0, false, false)));
        history(history);

        compactor.onTurnChanged(1, 20);

        Mockito.verify(aiService).deleteMessage(25);
        final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService).updateMessageText(Mockito.eq(101), json.capture());
        assertThat(JsonUtil.readValue(json.getValue(), CompactionRecord.class).stale()).isTrue();
        // The compaction that does not cover turn 20 is still used.
        Mockito.verify(aiService, Mockito.never()).updateMessageText(Mockito.eq(100), Mockito.anyString());
    }

    @Test
    void testIsContextOverflowError() {
        assertThat(ChatCompactor.isContextOverflowError(new RuntimeException("This model's maximum context "
                                                                             + "length is 8192 tokens"))).isTrue();
        assertThat(ChatCompactor.isContextOverflowError(
                new RuntimeException("wrapped", new IllegalStateException("prompt is too long")))).isTrue();
        assertThat(ChatCompactor.isContextOverflowError(new RuntimeException("connection refused"))).isFalse();
        assertThat(ChatCompactor.isContextOverflowError(new RuntimeException((String) null))).isFalse();
    }

    @Test
    void testFinishedTurns() {
        assertThat(ChatCompactor.finishedTurns(turns(3, 10))).containsExactly(10, 20, 30);
        assertThat(ChatCompactor.finishedTurns(List.of())).isEmpty();
    }

    private void history(final List<AiChatMessage> history) {
        Mockito.when(aiService.getMessages(1)).thenReturn(history);
    }

    private CompactionRecord storedCompaction() {
        final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService).storeMessage(Mockito.eq(1), Mockito.eq(AiMessageType.COMPACTION_SUMMARY),
                json.capture());
        return JsonUtil.readValue(json.getValue(), CompactionRecord.class);
    }

    /// @return {@code count} finished turns (ids 10, 20, ...), each with an answer of about
    /// {@code answerChars} characters whose first paragraph is short, then a question being asked.
    private static List<AiChatMessage> turns(final int count, final int answerChars) {
        final List<AiChatMessage> history = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final int turnId = i * 10;
            history.add(new AiChatMessage(turnId, 1, 0L, AiMessageType.USER_MESSAGE, null,
                    "Question " + i + " " + "q".repeat(Math.min(answerChars / 4, 480)), turnId, null));
            history.add(new AiChatMessage(turnId + 1, 1, 0L, AiMessageType.AI_RESPONSE, null,
                    "Answer " + i + ".\n\n" + "a".repeat(answerChars), turnId, null));
        }
        final int questionId = (count + 1) * 10;
        history.add(new AiChatMessage(questionId, 1, 0L, AiMessageType.USER_MESSAGE, null, "Now?", questionId,
                null));
        return history;
    }

    private static AiChatMessage compactionMessage(final int id, final CompactionRecord record) {
        return new AiChatMessage(id, 1, 0L, AiMessageType.COMPACTION_SUMMARY, null,
                JsonUtil.writeValueAsString(record, false), 50, null);
    }

    private static AskStroomAiRequest request() {
        return new AskStroomAiRequest(null, null, null, "Now?");
    }

    private static OpenAIModelDoc model(final int window) {
        return OpenAIModelDoc.builder().uuid("m").maxContextWindowTokens(window).build();
    }

    private static final class SummaryModel implements ChatModel {

        private final String summary;
        private ChatRequest request;

        private SummaryModel(final String summary) {
            this.summary = summary;
        }

        @Override
        public ChatResponse doChat(final ChatRequest chatRequest) {
            request = chatRequest;
            return ChatResponse.builder().aiMessage(AiMessage.from(summary)).build();
        }
    }
}
