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

import stroom.index.impl.IndexShardManager;
import stroom.index.impl.IndexShardManager.IndexShardAction;
import stroom.index.impl.IndexShardTaskDao;
import stroom.index.impl.IndexShardTaskState;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.processor.impl.ProcessorTaskTestHelper;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.concurrent.ThreadUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    // Translates the data, then indexes it, and returns the ids of the indexing tasks.
    private List<Long> indexTheStream() {
        commonTranslationTestHelper.setup();
        commonTranslationTestHelper.processAll();
        storeCreationTool.addIndex("Test index", INDEX_XSLT, OptionalInt.empty());

        processorTaskTestHelper.createAndQueueTasks();
        final List<Long> taskIds = new ArrayList<>();
        List<ProcessorTask> tasks = processorTaskTestHelper.assignTasks(1);
        while (!tasks.isEmpty()) {
            for (final ProcessorTask task : tasks) {
                commonTranslationTestHelper.process(task);
                taskIds.add(task.getId());
            }
            tasks = processorTaskTestHelper.assignTasks(1);
        }
        assertThat(taskIds).isNotEmpty();
        return taskIds;
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
