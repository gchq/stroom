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
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResponse;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// A chat left idle close to its model's context window is compacted by the 'AI Chat Idle Compaction' job, with
/// the chat's model, as its owner, through the real run manager, database and model client.
class TestAskStroomAiIdleCompaction extends AbstractAiIntegrationTest {

    private static final int WINDOW_TOKENS = 8_000;
    private static final int LARGE_WINDOW_TOKENS = 1_000_000;
    // The node an idle compaction works on when the question waiting for it, and the Stop, are on this one.
    private static final String OTHER_NODE = "other-node";

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

    @Test
    void oneStopWhileAQuestionWaitsForAnIdleCompactionOnAnotherNodeStopsBothAndTheQuestionIsNotSent() {
        final DocRef modelRef = asUser(user, () -> fakeModel(LARGE_WINDOW_TOKENS));
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final AiChatRun idle = runDao.createIdleCompactionIfNoneActive(chat.getId(), user.getUuid(), OTHER_NODE)
                .orElseThrow();
        final Question question = ask(chat, modelRef, "Which feeds are there?");
        question.awaitWaiting();

        // One Stop, on this node, as the user's client sends it.
        final boolean stopped = asUser(user, () -> askStroomAIService.cancelProcessing(chat.getId()));

        assertThat(stopped).isTrue();
        final AskStroomAiResponse response = question.response();
        assertThat(response.isStoppedBeforeSending()).isTrue();
        // The compaction is asked to stop, and no run was started for the question, nor was it stored.
        final AiChatRun idleNow = runDao.get(idle.getId()).orElseThrow();
        assertThat(idleNow.getStatus()).isEqualTo(AiChatRunStatus.STOPPING);
        assertThat(idleNow.getStopReason()).isEqualTo(AiChatRunStopReason.USER);
        assertThat(runDao.getLatest(chat.getId()).map(AiChatRun::getId)).contains(idle.getId());
        assertThat(aiService.getMessages(chat.getId())).isEmpty();
        assertThat(model.agentRequests()).isEmpty();
        stopOnTheOtherNode(idle);
    }

    @Test
    void aQuestionSentAfterAStopWithNoQuestionWaitingIsAskedOnceTheCompactionHasStopped() {
        final DocRef modelRef = asUser(user, () -> fakeModel(LARGE_WINDOW_TOKENS));
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final AiChatRun idle = runDao.createIdleCompactionIfNoneActive(chat.getId(), user.getUuid(), OTHER_NODE)
                .orElseThrow();
        // A Stop with no question waiting stops the compaction alone, as before.
        assertThat(asUser(user, () -> askStroomAIService.cancelProcessing(chat.getId()))).isTrue();
        assertThat(runDao.get(idle.getId()).map(AiChatRun::getStatus)).contains(AiChatRunStatus.STOPPING);
        assertThat(aiService.getMessages(chat.getId())).isEmpty();

        // A question sent while it winds down waits for it, then is asked.
        final Question question = ask(chat, modelRef, "Which feeds are there?");
        question.awaitWaiting();
        stopOnTheOtherNode(idle);

        assertThat(question.response().isStoppedBeforeSending()).isFalse();
        final List<AiChatMessage> messages = answered(chat.getId());
        assertThat(messages).extracting(AiChatMessage::getMessageType)
                .contains(AiMessageType.USER_MESSAGE, AiMessageType.AI_RESPONSE);
        assertThat(runDao.getLatest(chat.getId()).map(AiChatRun::getId)).isNotEqualTo(Optional.of(idle.getId()));
    }

    @Test
    void aQuestionWaitingForAnIdleCompactionThatFinishesIsAskedAsBefore() {
        final DocRef modelRef = asUser(user, () -> fakeModel(LARGE_WINDOW_TOKENS));
        final AiChat chat = asUser(user, () -> askStroomAIService.createChat());
        final AiChatRun idle = runDao.createIdleCompactionIfNoneActive(chat.getId(), user.getUuid(), OTHER_NODE)
                .orElseThrow();
        final Question question = ask(chat, modelRef, "Which feeds are there?");
        question.awaitWaiting();

        runDao.compareAndSetStatus(idle.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.DONE, null, null);

        assertThat(question.response().isStoppedBeforeSending()).isFalse();
        assertThat(answered(chat.getId())).extracting(AiChatMessage::getMessageType)
                .contains(AiMessageType.USER_MESSAGE, AiMessageType.AI_RESPONSE);
    }

    // Asks a question in the background, as the user's client does, as it may wait for an idle compaction.
    private Question ask(final AiChat chat, final DocRef modelRef, final String text) {
        final AtomicReference<AskStroomAiResponse> response = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread thread = new Thread(() -> {
            try {
                response.set(asUser(user, () -> askStroomAIService.askStroomAi(new AskStroomAiRequest(chat,
                        AskStroomAiConfig.builder().modelRef(modelRef).build(), null, text))));
            } catch (final Throwable e) {
                failure.set(e);
            }
        }, "question");
        thread.start();
        return new Question(thread, response, failure);
    }

    // The idle compaction's node stops it, keeping the reason it was asked to stop for.
    private void stopOnTheOtherNode(final AiChatRun idle) {
        runDao.compareAndSetStatus(idle.getId(), Set.of(AiChatRunStatus.RUNNING, AiChatRunStatus.STOPPING),
                AiChatRunStatus.STOPPED, AiChatRunStopReason.USER, null);
    }

    // The chat's messages once its question has been answered and its run has ended.
    private List<AiChatMessage> answered(final int chatId) {
        final Instant until = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(until)) {
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            if (messages.stream().anyMatch(message -> message.getMessageType() == AiMessageType.AI_RESPONSE)
                && !runManager.isRunningHere(chatId)) {
                return messages;
            }
            sleep(100);
        }
        throw new AssertionError("Not answered within 2 minutes");
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted", e);
        }
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

    /// A question asked in the background.
    private record Question(Thread thread,
                            AtomicReference<AskStroomAiResponse> responseRef,
                            AtomicReference<Throwable> failure) {

        /// Waits until the question is waiting for the idle compaction: it polls the run, sleeping between looks.
        void awaitWaiting() {
            final Instant until = Instant.now().plus(Duration.ofSeconds(30));
            // Long enough for its first look at the run, and more.
            sleep(1_000);
            while (thread.getState() != Thread.State.TIMED_WAITING) {
                if (!thread.isAlive() || Instant.now().isAfter(until)) {
                    throw new AssertionError("The question did not wait for the idle compaction", failure.get());
                }
                sleep(20);
            }
        }

        /// @return The response to the question, once it has returned.
        AskStroomAiResponse response() {
            try {
                thread.join(Duration.ofSeconds(30));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted", e);
            }
            assertThat(thread.isAlive()).as("The question has not returned").isFalse();
            assertThat(failure.get()).as("The question failed").isNull();
            return responseRef.get();
        }
    }
}
