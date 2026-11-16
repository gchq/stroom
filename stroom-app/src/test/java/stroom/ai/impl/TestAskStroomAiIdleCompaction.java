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
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.test.AbstractAiIntegrationTest;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// A chat left idle close to its model's context window is compacted by the 'AI Chat Idle Compaction' job, with
/// the chat's model, as its owner, through the real run manager, database and model client.
class TestAskStroomAiIdleCompaction extends AbstractAiIntegrationTest {

    private static final int WINDOW_TOKENS = 8_000;
    private static final int LARGE_WINDOW_TOKENS = 1_000_000;

    @Inject
    private AiIdleCompaction idleCompaction;
    @Inject
    private AskStroomAIService askStroomAIService;
    @Inject
    private AiService aiService;
    @Inject
    private AiDao aiDao;
    @Inject
    private AiChatRunDao runDao;
    @Inject
    private AiChatRunManager runManager;
    @Inject
    private OpenAIModelStore modelStore;
    @Inject
    private SecurityContext securityContext;

    private FakeModelServer model;
    private UserRef user;

    @BeforeEach
    void setUp() throws IOException {
        model = FakeModelServer.start();
        user = administrator();
    }

    @AfterEach
    void tearDown() {
        model.close();
    }

    @Test
    void aChatLeftIdleOverTheIdleThresholdIsCompactedOnceWithItsModel() {
        final DocRef modelRef = asUser(user, () -> fakeModel(WINDOW_TOKENS));
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        asUser(user, () -> {
            for (int i = 1; i <= 40; i++) {
                aiService.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "Question " + i + " "
                                                                                 + "q".repeat(480));
                aiService.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "Answer " + i + ".\n\n"
                                                                                + "a".repeat(2_000));
            }
        });
        // The chat was last answered with the model.
        aiDao.recordModel(chat.getId(), modelRef.getUuid(), WINDOW_TOKENS);
        final long idleNowMs = System.currentTimeMillis() + Duration.ofMinutes(11).toMillis();

        final int started = securityContext.asProcessingUserResult(() -> idleCompaction.exec(idleNowMs));

        assertThat(started).isOne();
        final List<AiChatMessage> messages = compacted(chat.getId());
        final CompactionRecord record = ContextAssembler.currentCompaction(messages);
        assertThat(record.idle()).isTrue();
        assertThat(record.manual()).isFalse();
        // The digests alone were too large for so small a window, so the chat's model wrote a summary.
        assertThat(record.summary()).isEqualTo(FakeModelServer.SIDE_ANSWER);
        assertThat(model.sideRequests()).isNotEmpty();
        assertThat(runDao.getLatest(chat.getId()).map(run -> run.getStatus())).contains(AiChatRunStatus.DONE);

        // Compacted since its last message: not looked at again.
        final int startedAgain = securityContext.asProcessingUserResult(() ->
                idleCompaction.exec(idleNowMs + Duration.ofMinutes(1).toMillis()));
        assertThat(startedAgain).isZero();
        assertThat(aiService.getMessages(chat.getId()).stream()
                .filter(message -> message.getMessageType() == AiMessageType.COMPACTION_SUMMARY))
                .hasSize(1);
    }

    @Test
    void aChatLeftIdleUnderTheIdleThresholdIsLeftAlone() {
        final DocRef modelRef = asUser(user, () -> fakeModel(LARGE_WINDOW_TOKENS));
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        asUser(user, () -> {
            aiService.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "Which feeds are there?");
            aiService.storeMessage(chat.getId(), AiMessageType.AI_RESPONSE, "FW-A and FW-B.");
        });
        aiDao.recordModel(chat.getId(), modelRef.getUuid(), LARGE_WINDOW_TOKENS);
        final long idleNowMs = System.currentTimeMillis() + Duration.ofMinutes(11).toMillis();

        final int started = securityContext.asProcessingUserResult(() -> idleCompaction.exec(idleNowMs));

        assertThat(started).isZero();
        assertThat(runDao.getLatest(chat.getId())).isEmpty();
        assertThat(model.sideRequests()).isEmpty();
    }

    private DocRef fakeModel(final int windowTokens) {
        final DocRef ref = modelStore.createDocument("Fake model");
        final OpenAIModelDoc doc = modelStore.readDocument(ref).copy()
                .baseUrl(model.baseUrl())
                .apiKey("key")
                .modelId("fake")
                .toolsEnabled(true)
                .maxContextWindowTokens(windowTokens)
                .build();
        modelStore.writeDocument(doc);
        return ref;
    }

    // The chat's messages once it has been compacted and its run has ended.
    private List<AiChatMessage> compacted(final int chatId) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            final Optional<AiChatMessage> compaction = ChatCompactor.currentCompactionMessage(messages);
            if (compaction.isPresent() && !runManager.isRunningHere(chatId)) {
                return messages;
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted", e);
            }
        }
        throw new AssertionError("Not compacted within 2 minutes");
    }
}
