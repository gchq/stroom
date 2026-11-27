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

package stroom.index;

import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.impl.IndexShardPurgeDao;
import stroom.index.impl.IndexShardStreamRecovery;
import stroom.index.impl.IndexShardTaskDao;
import stroom.index.impl.IndexShardTaskState;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorTaskAvailability;
import stroom.processor.impl.ProcessorTaskTestHelper;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests that a task that indexes a stream is held as `Committing` until every shard it wrote to has
/// committed, and is then completed, against a real database and real index files.
class TestIndexingTaskCompletion extends AbstractCoreIntegrationTest {

    private static final Path INDEX_XSLT = StroomPipelineTestFileUtil.getTestResourcesFile(
            "CommonIndexingTest/index.xsl");

    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;
    @Inject
    private IndexShardManager indexShardManager;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
    @Inject
    private HeldProcessorTaskService heldProcessorTaskService;
    @Inject
    private IndexShardTaskDao indexShardTaskDao;
    @Inject
    private IndexShardDao indexShardDao;
    @Inject
    private IndexShardStreamRecovery indexShardStreamRecovery;
    @Inject
    private ProcessorTaskAvailability processorTaskAvailability;
    @Inject
    private IndexShardPurgeDao indexShardPurgeDao;
    // The streams of the tasks that have been run.
    private final List<Long> streamIds = new ArrayList<>();
    @Inject
    private Provider<ProcessorConfig> processorConfigProvider;

    @BeforeEach
    void setUp() {
        // Work is put back and then looked for straight away.
        processorConfigProvider.get().setSkipEmptyFilterFetchDuration(StroomDuration.ZERO);
    }

    @Test
    void testTaskIsCommittingUntilItsShardsCommit() {
        final List<Long> taskIds = indexTheStream();

        // The run has ended, but its documents are not yet committed.
        assertThat(states(taskIds)).allMatch(state -> TaskStatus.COMMITTING.equals(state.status()));
        assertThat(taskIds).allSatisfy(taskId -> assertThat(indexShardTaskDao.findByTask(taskId))
                .isNotEmpty()
                .allMatch(row -> IndexShardTaskState.WRITTEN.equals(row.state())));

        indexShardManager.performAction(FindIndexShardCriteria.matchAll(), IndexShardAction.FLUSH);

        awaitStatus(taskIds, TaskStatus.COMPLETE);
        // Nothing is kept of a task once it is complete.
        assertThat(taskIds).allSatisfy(taskId -> assertThat(indexShardTaskDao.findByTask(taskId)).isEmpty());
    }

    @Test
    void testClosingTheShardsCompletesTheTaskToo() {
        final List<Long> taskIds = indexTheStream();
        assertThat(states(taskIds)).allMatch(state -> TaskStatus.COMMITTING.equals(state.status()));

        // As when the node shuts down, or the writer cache evicts the writers.
        indexShardWriterCache.shutdown();

        awaitStatus(taskIds, TaskStatus.COMPLETE);
        assertThat(taskIds).allSatisfy(taskId -> assertThat(indexShardTaskDao.findByTask(taskId)).isEmpty());
    }

    @Test
    void testRerun_documentsOfTheEarlierRunArePurged() {
        final List<Long> taskIds = indexTheStream();
        final long documentCount = countUncommittedDocuments();
        assertThat(documentCount).isPositive();

        // Put back while Committing, e.g. as the node lost its documents, and run again on the same shards.
        taskIds.forEach(taskId -> assertThat(heldProcessorTaskService.putBack(taskId, 1, "test")).isTrue());
        assertThat(runAgain()).containsExactlyInAnyOrderElementsOf(taskIds);
        indexShardManager.performAction(FindIndexShardCriteria.matchAll(), IndexShardAction.FLUSH);

        awaitStatus(taskIds, TaskStatus.COMPLETE);
        // The stream is in the index once.
        assertThat(countCommittedDocuments()).isEqualTo(documentCount);
        assertThat(states(taskIds)).allMatch(state -> state.runCount() == 2);
    }

    @Test
    void testDocumentsLost_taskIsPutBackAndIndexedOnce() {
        final List<Long> taskIds = indexTheStream();
        final long documentCount = countUncommittedDocuments();

        // As if the node died: its writers are gone without a clean close, and on restart the shards are reset.
        final List<IndexShardWriter> writers = openWriters();
        writers.forEach(writer -> indexShardDao.reset(writer.getIndexShardId()));
        writers.forEach(writer -> indexShardWriterCache.close(writer));
        awaitNoOpenWriters();

        indexShardStreamRecovery.exec();

        assertThat(states(taskIds)).allMatch(state -> TaskStatus.CREATED.equals(state.status()));
        assertThat(runAgain()).containsExactlyInAnyOrderElementsOf(taskIds);
        indexShardManager.performAction(FindIndexShardCriteria.matchAll(), IndexShardAction.FLUSH);

        awaitStatus(taskIds, TaskStatus.COMPLETE);
        // What the lost writer had committed when it closed has been purged, so the stream is in the index once.
        assertThat(countCommittedDocuments()).isEqualTo(documentCount);
    }

    @Test
    void testPurgeOfAShardNobodyHasOpen_isSweptByTheJob() {
        final List<Long> taskIds = indexTheStream();
        indexShardWriterCache.shutdown();
        awaitStatus(taskIds, TaskStatus.COMPLETE);
        assertThat(countCommittedDocuments()).isPositive();

        // As when a task that wrote these shards has been aborted since.
        indexShardDao.find(FindIndexShardCriteria.matchAll()).forEach(indexShard ->
                streamIds.forEach(streamId -> indexShardPurgeDao.request(indexShard.getId(), streamId)));
        indexShardStreamRecovery.exec();

        assertThat(countCommittedDocuments()).isZero();
        indexShardDao.find(FindIndexShardCriteria.matchAll()).forEach(indexShard ->
                assertThat(indexShardPurgeDao.findByShard(indexShard.getId())).isEmpty());
    }

    // Translates the data, then indexes it, and returns the ids of the indexing tasks.
    private List<Long> indexTheStream() {
        commonTranslationTestHelper.setup();
        commonTranslationTestHelper.processAll();
        storeCreationTool.addIndex("Test index", INDEX_XSLT, OptionalInt.empty());
        return runAgain();
    }

    // Runs every task that is waiting, and returns their ids.
    private List<Long> runAgain() {
        processorTaskAvailability.clear();
        processorTaskTestHelper.createAndQueueTasks();
        final List<Long> taskIds = new ArrayList<>();
        List<ProcessorTask> tasks = processorTaskTestHelper.assignTasks(1);
        while (!tasks.isEmpty()) {
            for (final ProcessorTask task : tasks) {
                commonTranslationTestHelper.process(task);
                taskIds.add(task.getId());
                streamIds.add(task.getMetaId());
            }
            tasks = processorTaskTestHelper.assignTasks(1);
        }
        assertThat(taskIds).isNotEmpty();
        return taskIds;
    }

    private List<IndexShardWriter> openWriters() {
        return indexShardDao.find(FindIndexShardCriteria.matchAll())
                .stream()
                .map(indexShard -> indexShardWriterCache.getIfPresent(indexShard.getId()))
                .flatMap(Optional::stream)
                .toList();
    }

    private void awaitNoOpenWriters() {
        for (int i = 0; i < 100 && !openWriters().isEmpty(); i++) {
            ThreadUtil.sleep(100);
        }
        // The close itself is done on another thread.
        ThreadUtil.sleep(500);
    }

    private long countUncommittedDocuments() {
        return openWriters().stream().mapToLong(IndexShardWriter::getDocumentCount).sum();
    }

    private long countCommittedDocuments() {
        return indexShardDao.find(FindIndexShardCriteria.matchAll())
                .stream()
                .filter(indexShard -> !IndexShardStatus.DELETED.equals(indexShard.getStatus()))
                .mapToLong(IndexShard::getDocumentCount)
                .sum();
    }

    private Collection<ProcessorTaskState> states(final List<Long> taskIds) {
        return heldProcessorTaskService.getStates(taskIds).values();
    }

    private void awaitStatus(final List<Long> taskIds, final TaskStatus status) {
        final Predicate<ProcessorTaskState> hasStatus = state -> status.equals(state.status());
        for (int i = 0; i < 100 && !states(taskIds).stream().allMatch(hasStatus); i++) {
            ThreadUtil.sleep(100);
        }
        assertThat(states(taskIds)).allMatch(hasStatus);
    }
}
