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
import stroom.ai.impl.UserActionRecord.Status;
import stroom.ai.impl.dao.AiDaoModule;
import stroom.ai.impl.db.AiDbConnProvider;
import stroom.ai.impl.db.AiDbModule;
import stroom.ai.impl.extraction.ExtractionActions;
import stroom.ai.impl.vis.VisualisationActions;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiUserActionResult;
import stroom.collection.api.CollectionService;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/// What happens when things race against the database: several attempts made at once, as from two browser
/// tabs or two nodes. Exactly one must win each time.
class TestAiConcurrency {

    private static final int RACERS = 8;
    private static final List<String> TABLES = List.of(
            "ai_chat_file_chunk",
            "ai_chat_run",
            "ai_memory",
            "ai_tool_approval",
            "ai_chat_message",
            "ai_chat_attachment",
            "ai_chat");
    private static final String PIPELINE_UUID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";

    private AiDao aiDao;
    private AiChatRunDao runDao;
    private AiChat chat;
    private ExecutorService executor;

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
        aiDao = injector.getInstance(AiDao.class);
        runDao = injector.getInstance(AiChatRunDao.class);
        try (final Connection connection = injector.getInstance(AiDbConnProvider.class).getConnection()) {
            DbTestUtil.clearTables(connection, TABLES);
        }
        chat = aiDao.createChat(UserRef.builder().uuid("user1").build());
        executor = Executors.newFixedThreadPool(RACERS);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void onlyOneRunStartsWhenSeveralTryAtOnce() throws Exception {
        final List<Optional<?>> started = race(() ->
                runDao.createIfNoneActive(chat.getId(), "user1", AiChatRunStatus.RUNNING, "node1", null));

        assertThat(started).filteredOn(Optional::isPresent).hasSize(1);
        assertThat(runDao.getActive(chat.getId())).isPresent();
    }

    @Test
    void onlyOneWriterChangesAMessageFromWhatItRead() throws Exception {
        final AiChatMessage message = aiDao.storeMessage(chat.getId(), AiMessageType.ELICITATION, "waiting");

        final List<Boolean> changed = race(() ->
                aiDao.compareAndSetMessageText(message.getId(), "waiting", "answered by " + Thread.currentThread()));

        assertThat(changed).filteredOn(Boolean::booleanValue).hasSize(1);
    }

    @Test
    void aButtonClickedInSeveralTabsAtOnceIsDoneOnce() throws Exception {
        final AiChatMessage message = aiDao.storeMessage(chat.getId(), AiMessageType.TOOL_RESULT,
                JsonUtil.writeValueAsString(new ToolResultRecord("c1", "s__create_filter", false, "created", null, 7,
                        1, List.of(new UserActionRecord("enable-31", UserActions.ENABLE_PROCESSOR_FILTER,
                                "Enable processor filter 31", null, null, 31,
                                new DocRef("Pipeline", PIPELINE_UUID, "Acme-Events"), null, List.of(),
                                Long.MAX_VALUE, Status.PENDING, null))), false));
        final UserActionLookup lookup = Mockito.mock(UserActionLookup.class);
        Mockito.when(lookup.filter(31)).thenReturn(Optional.of(ProcessorFilter.builder()
                .id(31)
                .pipelineUuid(PIPELINE_UUID)
                .enabled(false)
                .build()));
        final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
        final UserActionService service = new UserActionService(Mockito.mock(AiService.class), aiDao,
                new UserActions(lookup), lookup, processorFilterService, Mockito.mock(AiAuditLog.class),
                Mockito.mock(DashboardBuilder.class), Mockito.mock(VisualisationActions.class),
                Mockito.mock(ExtractionActions.class));

        final List<AiUserActionResult> results = race(() ->
                service.doUserAction(chat.getId(), message.getId(), "enable-31"));

        assertThat(results).filteredOn(AiUserActionResult::isDone).hasSize(1);
        Mockito.verify(processorFilterService, Mockito.times(1)).setEnabled(31, true);
    }

    /// Runs the same thing in several threads, released together, and returns what each got.
    private <T> List<T> race(final Callable<T> attempt) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return attempt.call();
            }));
        }
        start.countDown();
        final List<T> results = new ArrayList<>();
        for (final Future<T> future : futures) {
            results.add(future.get());
        }
        return results;
    }
}
