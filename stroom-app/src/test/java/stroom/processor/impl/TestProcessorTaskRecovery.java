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

package stroom.processor.impl;

import stroom.data.shared.StreamTypeNames;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.processor.api.ProcessorTaskRecovery;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskExpressionUtil;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests putting a processor task back to run again, or giving up on it, on behalf of something that has
/// lost what the task wrote.
class TestProcessorTaskRecovery extends AbstractCoreIntegrationTest {

    private static final String MESSAGE = "The documents indexed from this stream were removed";

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ProcessorTaskRecovery processorTaskRecovery;
    @Inject
    private ProcessorTaskService processorTaskService;
    @Inject
    private ProcessorTaskDao processorTaskDao;
    @Inject
    private MetaService metaService;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;

    @Test
    void testRetry_completeTaskIsPutBackWithAnErrorStream() {
        final Meta source = createSource();
        final ProcessorTask task = createTask(source, TaskStatus.COMPLETE);

        assertThat(processorTaskRecovery.retry(task.getId(), MESSAGE)).isTrue();

        // Ready to be picked up by any node, as a task whose node died is.
        final ProcessorTask retried = fetch(task);
        assertThat(retried.getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(retried.getNodeName()).isNull();
        assertThat(retried.getVersion()).isGreaterThan(task.getVersion());

        // The error stream carries no task id, so the next run of the pipeline clears it away.
        final List<Meta> errorStreams = findErrorStreams(source);
        assertThat(errorStreams).hasSize(1);
        assertThat(errorStreams.getFirst().getProcessorTaskId()).isNull();
        assertThat(errorStreams.getFirst().getFeedName()).isEqualTo(source.getFeedName());
    }

    @Test
    void testRetry_whatTheTaskWroteLastTimeIsDeleted() {
        final Meta source = createSource();
        final ProcessorTask task = createTask(source, TaskStatus.COMPLETE);
        // Output of the run that is being done again, and of some other task.
        final Meta ownOutput = metaService.create(MetaProperties.builder()
                .feedName(source.getFeedName())
                .typeName(StreamTypeNames.ERROR)
                .parent(source)
                .processorTaskId(task.getId())
                .build());
        final Meta otherOutput = metaService.create(MetaProperties.builder()
                .feedName(source.getFeedName())
                .typeName(StreamTypeNames.EVENTS)
                .parent(source)
                .processorTaskId(task.getId() + 1_000_000)
                .build());

        assertThat(processorTaskRecovery.retry(task.getId(), MESSAGE)).isTrue();

        // The task runs again under the same id, so nothing else would ever clear its first output away.
        assertThat(metaService.getMeta(ownOutput.getId(), true).getStatus()).isEqualTo(Status.DELETED);
        assertThat(metaService.getMeta(otherOutput.getId(), true).getStatus()).isNotEqualTo(Status.DELETED);
        assertThat(findErrorStreams(source))
                .extracting(Meta::getProcessorTaskId)
                .containsOnlyNulls()
                .hasSize(1);
    }

    @Test
    void testRetry_runningTaskIsLeftAlone() {
        final Meta source = createSource();
        final ProcessorTask task = createTask(source, TaskStatus.PROCESSING);

        assertThat(processorTaskRecovery.retry(task.getId(), MESSAGE)).isFalse();

        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.PROCESSING);
        assertThat(findErrorStreams(source)).isEmpty();
    }

    @Test
    void testRetry_taskAlreadyWaitingToRun() {
        final Meta source = createSource();
        final ProcessorTask task = createTask(source, TaskStatus.CREATED);

        assertThat(processorTaskRecovery.retry(task.getId(), MESSAGE)).isTrue();

        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.CREATED);
        assertThat(findErrorStreams(source)).hasSize(1);
    }

    @Test
    void testFail_taskIsSetToFailedWithAnErrorStream() {
        final Meta source = createSource();
        final ProcessorTask task = createTask(source, TaskStatus.COMPLETE);

        assertThat(processorTaskRecovery.fail(task.getId(), MESSAGE)).isTrue();

        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(findErrorStreams(source)).hasSize(1);

        // A failed task can still be put back, e.g. by a later recovery.
        assertThat(processorTaskRecovery.retry(task.getId(), MESSAGE)).isTrue();
        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.CREATED);
    }

    @Test
    void testFail_runningTaskIsLeftAlone() {
        final Meta source = createSource();
        final ProcessorTask task = createTask(source, TaskStatus.PROCESSING);

        assertThat(processorTaskRecovery.fail(task.getId(), MESSAGE)).isFalse();

        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.PROCESSING);
    }

    @Test
    void testNoSuchTask() {
        assertThat(processorTaskRecovery.getStatus(Long.MAX_VALUE)).isEmpty();
        assertThat(processorTaskRecovery.retry(Long.MAX_VALUE, MESSAGE)).isTrue();
        assertThat(processorTaskRecovery.fail(Long.MAX_VALUE, MESSAGE)).isTrue();
    }

    @Test
    void testGetStatus() {
        final ProcessorTask task = createTask(createSource(), TaskStatus.COMPLETE);

        assertThat(processorTaskRecovery.getStatus(task.getId())).contains(TaskStatus.COMPLETE);
    }

    @Test
    void testChangeTaskStatus_onlyFromTheStatusesGiven() {
        final ProcessorTask task = createTask(createSource(), TaskStatus.COMPLETE);

        assertThat(processorTaskDao.changeTaskStatus(
                task.getId(), Set.of(TaskStatus.PROCESSING, TaskStatus.CREATED), TaskStatus.FAILED)).isFalse();
        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.COMPLETE);

        assertThat(processorTaskDao.changeTaskStatus(
                task.getId(), Set.of(TaskStatus.COMPLETE), TaskStatus.FAILED)).isTrue();
        assertThat(fetch(task).getStatus()).isEqualTo(TaskStatus.FAILED);
    }

    private Meta createSource() {
        final String feedName = FileSystemTestUtil.getUniqueTestString();
        final Meta source = commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
        commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);
        return source;
    }

    // Creates the task for a stream and gives it the status wanted.
    private ProcessorTask createTask(final Meta source, final TaskStatus status) {
        processorTaskTestHelper.createAndQueueTasks();
        final ProcessorTask task = processorTaskService.find(
                        new ExpressionCriteria(ProcessorTaskExpressionUtil.createWithStream(source)))
                .getFirst();
        assertThat(task).isNotNull();
        if (TaskStatus.CREATED.equals(status)) {
            processorTaskDao.changeTaskStatus(
                    task.getId(), Set.of(TaskStatus.QUEUED, TaskStatus.ASSIGNED), TaskStatus.CREATED);
        } else {
            processorTaskDao.changeTaskStatus(
                    task,
                    "node1a",
                    status,
                    System.currentTimeMillis(),
                    TaskStatus.PROCESSING.equals(status)
                            ? null
                            : System.currentTimeMillis());
        }
        final ProcessorTask changed = fetch(task);
        assertThat(changed.getStatus()).isEqualTo(status);
        return changed;
    }

    private ProcessorTask fetch(final ProcessorTask task) {
        return processorTaskService.fetch(task.getId()).orElseThrow();
    }

    private List<Meta> findErrorStreams(final Meta source) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, source.getId())
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                .build();
        return metaService.find(new FindMetaCriteria(expression))
                .getValues()
                .stream()
                .filter(meta -> !Status.DELETED.equals(meta.getStatus()))
                .toList();
    }
}
