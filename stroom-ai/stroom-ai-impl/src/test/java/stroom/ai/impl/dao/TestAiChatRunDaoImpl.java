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

package stroom.ai.impl.dao;

import stroom.ai.impl.AiChatRunDao;
import stroom.ai.impl.AiDao;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiChatRunDaoImpl {

    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");

    @Inject
    private AiDao aiDao;
    @Inject
    private AiChatRunDao runDao;
    @Inject
    private AiDbConnProvider aiDbConnProvider;

    private AiChat chat;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new AiDbModule(),
                new AiDaoModule(),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(WordListProvider.class).toInstance(Mockito.mock(WordListProvider.class));
                        bind(CollectionService.class).toInstance(Mockito.mock(CollectionService.class));
                        bind(DocFinder.class).toInstance(Mockito.mock(DocFinder.class));
                    }
                });
        injector.injectMembers(this);

        try (final Connection connection = aiDbConnProvider.getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
    }

    @Test
    void testCreateAndGet() {
        final AiChatRun run = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);

        assertThat(run.getChatId()).isEqualTo(chat.getId());
        assertThat(run.getStatus()).isEqualTo(AiChatRunStatus.RUNNING);
        assertThat(run.getStartTimeMs()).isNotNull();
        assertThat(run.getEndTimeMs()).isNull();
        assertThat(runDao.get(run.getId())).contains(run);
        assertThat(runDao.getLatest(chat.getId())).contains(run);
        assertThat(runDao.getActive(chat.getId())).contains(run);
    }

    @Test
    void testCreateIfNoneActive_onlyOneActiveRunPerChat() {
        final AiChatRun first = runDao.createIfNoneActive(
                chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null).orElseThrow();

        assertThat(runDao.createIfNoneActive(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null))
                .isEmpty();

        // Once the first run has ended, another can start.
        runDao.compareAndSetStatus(first.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.DONE, null, null);
        assertThat(runDao.createIfNoneActive(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null))
                .isPresent();
    }

    @Test
    void testCompareAndSetStatus() {
        final AiChatRun run = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);

        // Not changed, as it is not in the expected state.
        assertThat(runDao.compareAndSetStatus(run.getId(), Set.of(AiChatRunStatus.STOPPING),
                AiChatRunStatus.STOPPED, AiChatRunStopReason.USER, null)).isFalse();
        assertThat(runDao.get(run.getId()).orElseThrow().getStatus()).isEqualTo(AiChatRunStatus.RUNNING);

        assertThat(runDao.compareAndSetStatus(run.getId(), Set.of(AiChatRunStatus.RUNNING),
                AiChatRunStatus.FAILED, null, "x".repeat(2000))).isTrue();
        final AiChatRun failed = runDao.get(run.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AiChatRunStatus.FAILED);
        assertThat(failed.getEndTimeMs()).isNotNull();
        assertThat(failed.getErrorMessage()).hasSize(1024);
        assertThat(runDao.getActive(chat.getId())).isEmpty();
        assertThat(runDao.getLatest(chat.getId())).contains(failed);

        // A second, racing change from the same state loses.
        assertThat(runDao.compareAndSetStatus(run.getId(), Set.of(AiChatRunStatus.RUNNING),
                AiChatRunStatus.DONE, null, null)).isFalse();
    }

    @Test
    void testHeartbeatAndInterruptStale() throws InterruptedException {
        final AiChatRun alive = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);
        final AiChat otherChat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChatRun lost = runDao.create(otherChat.getId(), "user1", AiChatRunStatus.RUNNING, "node2", null);

        Thread.sleep(5);
        final long cutOff = System.currentTimeMillis();
        Thread.sleep(5);
        runDao.heartbeat(List.of(alive.getId()));

        final List<AiChatRun> interrupted = runDao.interruptStale(cutOff);

        assertThat(interrupted).extracting(AiChatRun::getId).containsExactly(lost.getId());
        final AiChatRun lostNow = runDao.get(lost.getId()).orElseThrow();
        assertThat(lostNow.getStatus()).isEqualTo(AiChatRunStatus.INTERRUPTED);
        assertThat(lostNow.getStopReason()).isEqualTo(AiChatRunStopReason.NODE_LOST);
        assertThat(runDao.getStatuses(List.of(alive.getId(), lost.getId(), 999_999)))
                .containsEntry(alive.getId(), AiChatRunStatus.RUNNING)
                .containsEntry(lost.getId(), AiChatRunStatus.INTERRUPTED)
                .hasSize(2);
        // Nothing is stale now.
        assertThat(runDao.interruptStale(cutOff)).isEmpty();
    }

    @Test
    void testInterruptStale_aToolWaitingForAFormStillNeedsHeartbeats() throws InterruptedException {
        final AiChatRun formWait = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "n", null);
        runDao.compareAndSetStatus(formWait.getId(), Set.of(AiChatRunStatus.RUNNING),
                AiChatRunStatus.WAITING_FOR_USER, AiChatRunStopReason.NEEDS_INPUT, null);
        final AiChat otherChat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        final AiChatRun approvalWait = runDao.create(otherChat.getId(), "user1", AiChatRunStatus.RUNNING, "n", null);
        runDao.compareAndSetStatus(approvalWait.getId(), Set.of(AiChatRunStatus.RUNNING),
                AiChatRunStatus.WAITING_FOR_USER, AiChatRunStopReason.NEEDS_APPROVAL, null);
        Thread.sleep(5);

        // A form wait keeps its thread, so if its node has gone it is interrupted; an approval wait has no thread.
        assertThat(runDao.interruptStale(System.currentTimeMillis()))
                .extracting(AiChatRun::getId)
                .containsExactly(formWait.getId());
    }

    @Test
    void testInterruptStale_aRunCarriedOnAfterWaitingIsNotStale() throws InterruptedException {
        // Waiting for answers, with no thread, so sending no heartbeats, for longer than a run takes to go stale.
        final AiChatRun run = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "n", null);
        runDao.compareAndSetStatus(run.getId(), Set.of(AiChatRunStatus.RUNNING),
                AiChatRunStatus.WAITING_FOR_USER, AiChatRunStopReason.NEEDS_ANSWERS, null);
        Thread.sleep(5);
        final long cutOff = System.currentTimeMillis();
        Thread.sleep(5);

        // The user sends a message instead, so the run carries on, before its node's next heartbeat.
        runDao.compareAndSetStatus(run.getId(), Set.of(AiChatRunStatus.WAITING_FOR_USER), AiChatRunStatus.RUNNING,
                null, null);

        assertThat(runDao.interruptStale(cutOff)).isEmpty();
        assertThat(runDao.get(run.getId()).orElseThrow().getStatus()).isEqualTo(AiChatRunStatus.RUNNING);
    }

    @Test
    void testSessionRuns() {
        final AiChatRun fromSession = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "n", "hash1");
        final AiChat otherChat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        runDao.create(otherChat.getId(), "user1", AiChatRunStatus.RUNNING, "n", "hash2");

        assertThat(runDao.getActiveBySession("hash1"))
                .extracting(AiChatRun::getId)
                .containsExactly(fromSession.getId());

        // Terminated when its session ended, then corrected once it is known the user signed out.
        runDao.compareAndSetStatus(fromSession.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.STOPPED,
                AiChatRunStopReason.TERMINATED, null);
        runDao.correctStopReason("hash1", AiChatRunStopReason.TERMINATED, AiChatRunStopReason.SIGN_OUT,
                System.currentTimeMillis() - 60_000);

        assertThat(runDao.get(fromSession.getId()).orElseThrow().getStopReason())
                .isEqualTo(AiChatRunStopReason.SIGN_OUT);
        assertThat(runDao.getActiveBySession("hash1")).isEmpty();
    }

    @Test
    void testSetUnattended() {
        final AiChatRun run = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "n", null);

        runDao.setUnattended(run.getId(), true);
        assertThat(runDao.get(run.getId()).orElseThrow().isUnattended()).isTrue();
        runDao.setUnattended(run.getId(), false);
        assertThat(runDao.get(run.getId()).orElseThrow().isUnattended()).isFalse();
    }

    @Test
    void testPresence() {
        assertThat(aiDao.getLastPollMs("user1")).isNull();

        final long before = System.currentTimeMillis();
        aiDao.recordPoll(chat.getId(), 0);
        final Long first = aiDao.getLastPollMs("user1");
        assertThat(first).isGreaterThanOrEqualTo(before);

        // Not written again within the interval.
        aiDao.recordPoll(chat.getId(), 60_000);
        assertThat(aiDao.getLastPollMs("user1")).isEqualTo(first);
        assertThat(aiDao.getLastPollMs("user2")).isNull();
    }

    @Test
    void testCountWorking() {
        runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "n", null);
        final AiChat chat2 = aiDao.createChat(UserRef.builder().uuid("user1").build());
        runDao.create(chat2.getId(), "user1", AiChatRunStatus.STOPPING, "n", null);
        final AiChat chat3 = aiDao.createChat(UserRef.builder().uuid("user1").build());
        // Queued and waiting runs are not being worked on.
        runDao.create(chat3.getId(), "user1", AiChatRunStatus.QUEUED, "n", null);
        final AiChat chat4 = aiDao.createChat(UserRef.builder().uuid("user1").build());
        runDao.create(chat4.getId(), "user1", AiChatRunStatus.WAITING_FOR_USER, "n", null);
        final AiChat otherUsersChat = aiDao.createChat(UserRef.builder().uuid("user2").build());
        runDao.create(otherUsersChat.getId(), "user2", AiChatRunStatus.RUNNING, "n", null);

        assertThat(runDao.countWorking("user1")).isEqualTo(2);
        assertThat(runDao.countWorking("user2")).isEqualTo(1);
        assertThat(runDao.countWorking("user3")).isZero();
    }

    @Test
    void testDeletingChatDeletesRuns() {
        runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);

        aiDao.deleteChat(chat.getId());

        assertThat(runDao.getLatest(chat.getId())).isEmpty();
    }

    @Test
    void testIdleCompactionRun_isMarkedAndOnlyStartsInAChatWithNoActiveRun() {
        final AiChatRun idle = runDao.createIdleCompactionIfNoneActive(chat.getId(), "user1", "node1")
                .orElseThrow();

        assertThat(idle.getStatus()).isEqualTo(AiChatRunStatus.RUNNING);
        assertThat(runDao.getActiveIdleCompaction(chat.getId())).contains(idle);
        assertThat(runDao.createIdleCompactionIfNoneActive(chat.getId(), "user1", "node1")).isEmpty();
        assertThat(runDao.createIfNoneActive(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null))
                .isEmpty();

        // A question's run isn't an idle compaction.
        runDao.compareAndSetStatus(idle.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.DONE, null, null);
        final AiChatRun question = runDao.createIfNoneActive(
                chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null).orElseThrow();
        assertThat(runDao.getActiveIdleCompaction(chat.getId())).isEmpty();
        assertThat(runDao.createIdleCompactionIfNoneActive(chat.getId(), "user1", "node1")).isEmpty();
        assertThat(runDao.getActive(chat.getId())).contains(question);
    }

    @Test
    void testGetLatestShown_leavesOutAnIdleCompactionThatHasEnded() {
        final AiChatRun question = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);
        runDao.compareAndSetStatus(question.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.DONE, null,
                null);
        final AiChatRun idle = runDao.createIdleCompactionIfNoneActive(chat.getId(), "user1", "node1")
                .orElseThrow();

        // Shown while it works, so the chat shows as busy and it can be stopped.
        assertThat(runDao.getLatestShown(chat.getId())).contains(idle);

        // Stopped, it answered no question, so there is nothing of it to resume.
        runDao.compareAndSetStatus(idle.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.STOPPED,
                AiChatRunStopReason.USER, null);
        assertThat(runDao.getLatestShown(chat.getId()).map(AiChatRun::getId)).contains(question.getId());
        assertThat(runDao.getLatest(chat.getId()).map(AiChatRun::getId)).contains(idle.getId());
    }

    @Test
    void testFindIdleChats_aChatWhoseLastMessageIsInTheWindowIsFound() {
        final AiChatMessage last = answered(chat.getId());

        assertThat(findIdle()).containsExactly(
                new AiDao.IdleChat(chat.getId(), "user1", last.getId(), last.getCreateTimeMs()));
    }

    @Test
    void testFindIdleChats_aChatNotIdleLongEnoughIsNotFound() {
        final AiChatMessage last = answered(chat.getId());

        // Its last message is no older than the idle time.
        assertThat(aiDao.findIdleChats(last.getCreateTimeMs(), last.getCreateTimeMs() - 60_000, List.of(), 10))
                .isEmpty();
        // Nor is one idle for longer than the window looks back.
        assertThat(aiDao.findIdleChats(last.getCreateTimeMs() + 60_000, last.getCreateTimeMs() + 1, List.of(), 10))
                .isEmpty();
    }

    @Test
    void testFindIdleChats_aChatCompactedSinceItsLastMessageIsNotFound() {
        answered(chat.getId());
        aiDao.storeMessage(chat.getId(), AiMessageType.COMPACTION_SUMMARY, "{}");

        assertThat(findIdle()).isEmpty();

        // A message after the compaction makes it a candidate again.
        aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "and now?");
        assertThat(findIdle()).hasSize(1);
    }

    @Test
    void testFindIdleChats_aChatWithAnActiveRunIsNotFound() {
        answered(chat.getId());
        final AiChatRun waiting = runDao.create(chat.getId(), "user1", AiChatRunStatus.WAITING_FOR_USER, "node1",
                null);

        assertThat(findIdle()).isEmpty();

        runDao.compareAndSetStatus(waiting.getId(), Set.of(AiChatRunStatus.WAITING_FOR_USER),
                AiChatRunStatus.DONE, null, null);
        assertThat(findIdle()).hasSize(1);
    }

    @Test
    void testFindIdleChats_aRunActiveWithinTheIdleTimeIsActivityThoughItHasEnded() {
        // A run whose last tool call (or thinking) took longer than the idle time, then ended without a message:
        // its last message is old, but the chat was not idle.
        final AiChatMessage last = answered(chat.getId());
        final AiChatRun run = runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);
        sleepPastTheMillisecond();
        final long afterLastMessage = now();
        runDao.heartbeat(List.of(run.getId()));
        runDao.compareAndSetStatus(run.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.FAILED, null,
                "The tool call failed");

        // Idle since before the run's end: not found.
        assertThat(aiDao.findIdleChats(afterLastMessage, last.getCreateTimeMs() - 60_000, List.of(), 10))
                .isEmpty();
        // Idle since after it: found.
        assertThat(aiDao.findIdleChats(now() + 60_000, last.getCreateTimeMs() - 60_000, List.of(), 10))
                .extracting(AiDao.IdleChat::chatId)
                .containsExactly(chat.getId());
    }

    @Test
    void testFindIdleChats_aChatWithAnIdleCompactionSinceItsLastMessageIsNotFound() {
        answered(chat.getId());
        final AiChatRun idle = runDao.createIdleCompactionIfNoneActive(chat.getId(), "user1", "node1")
                .orElseThrow();
        // Stopped by the user, say: it is not tried again until a new message arrives.
        runDao.compareAndSetStatus(idle.getId(), Set.of(AiChatRunStatus.RUNNING), AiChatRunStatus.STOPPED,
                AiChatRunStopReason.USER, null);

        assertThat(findIdle()).isEmpty();

        sleepPastTheMillisecond();
        aiDao.storeMessage(chat.getId(), AiMessageType.USER_MESSAGE, "and now?");
        assertThat(findIdle()).hasSize(1);
    }

    @Test
    void testFindIdleChats_leavesOutExcludedMessagesSessionChatsAndKeepsTheLimit() {
        final AiChatMessage first = answered(chat.getId());
        final AiChat second = aiDao.createChat(UserRef.builder().uuid("user2").build());
        final AiChatMessage secondLast = answered(second.getId());
        final AiChat session = aiDao.createSessionChat(UserRef.builder().uuid("user1").build(), "session-1");
        answered(session.getId());

        assertThat(findIdle()).extracting(AiDao.IdleChat::chatId).containsExactly(chat.getId(), second.getId());
        assertThat(aiDao.findIdleChats(now() + 60_000, now() - 60_000, List.of(first.getId()), 10))
                .extracting(AiDao.IdleChat::lastMessageId)
                .containsExactly(secondLast.getId());
        assertThat(aiDao.findIdleChats(now() + 60_000, now() - 60_000, List.of(), 1))
                .extracting(AiDao.IdleChat::chatId)
                .containsExactly(chat.getId());
    }

    // A question and its answer, returning the answer.
    private AiChatMessage answered(final int chatId) {
        aiDao.storeMessage(chatId, AiMessageType.USER_MESSAGE, "q");
        final AiChatMessage answer = aiDao.storeMessage(chatId, AiMessageType.AI_RESPONSE, "a");
        sleepPastTheMillisecond();
        return answer;
    }

    // As if the idle time had passed: messages stored up to a minute ago count as idle.
    private List<AiDao.IdleChat> findIdle() {
        return aiDao.findIdleChats(now() + 60_000, now() - 60_000, List.of(), 10);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // So that what is stored next is stored later than what was stored before.
    private static void sleepPastTheMillisecond() {
        ThreadUtil.sleepIgnoringInterrupts(5);
    }
}
