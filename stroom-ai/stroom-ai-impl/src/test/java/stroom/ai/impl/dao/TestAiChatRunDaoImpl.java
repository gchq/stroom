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
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docstore.api.DocFinder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
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
    void testDeletingChatDeletesRuns() {
        runDao.create(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null);

        aiDao.deleteChat(chat.getId());

        assertThat(runDao.getLatest(chat.getId())).isEmpty();
    }
}
