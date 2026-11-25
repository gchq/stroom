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
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests what happens to the output of real runs, with a real database, when their tasks are run again or are lost.
class TestProcessorTaskRerun extends AbstractCoreIntegrationTest {

    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;
    @Inject
    private ProcessorTaskAvailability processorTaskAvailability;
    @Inject
    private ProcessorTaskHeartbeat processorTaskHeartbeat;
    @Inject
    private ProcessorTaskService processorTaskService;
    @Inject
    private ProcessorTaskDao processorTaskDao;
    @Inject
    private MetaService metaService;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private Provider<ProcessorConfig> processorConfigProvider;

    @BeforeEach
    void setUp() {
        // Work is put back and then looked for straight away.
        processorConfigProvider.get().setSkipEmptyFilterFetchDuration(StroomDuration.ZERO);
    }

    @ParameterizedTest(name = "claimTasksOnWorker={0}")
    @ValueSource(booleans = {false, true})
    void runAfterANodeDiedLeavesOneSetOfOutput(final boolean claimTasksOnWorker) {
        final ProcessorTask assigned = assignEventsTask(claimTasksOnWorker);
        final ProcessorTask first = claimTasksOnWorker
                ? assigned
                : processorTaskDao.changeTaskStatus(
                        assigned, nodeInfo.getThisNodeName(), TaskStatus.PROCESSING, 1L, null).task();
        // The run wrote two streams before its node died, and had only finished one of them.
        final Meta locked = createOutput(first, first.getId(), Status.LOCKED);
        final Meta unlocked = createOutput(first, first.getId(), Status.UNLOCKED);
        processorTaskHeartbeat.deregister(first);
        assertThat(processorTaskDao.reapDeadTasks(Instant.now().plusSeconds(60))).isOne();

        processorTaskTestHelper.createAndQueueTasks();
        final ProcessorTask second = assign();
        assertThat(second.getId()).isEqualTo(first.getId());
        commonTranslationTestHelper.process(second);

        assertThat(getTask(first).getStatus()).isEqualTo(TaskStatus.COMPLETE);
        assertThat(getTask(first).getRunCount()).isEqualTo(2);
        assertThat(getStatus(locked)).isEqualTo(Status.DELETED);
        assertThat(getStatus(unlocked)).isEqualTo(Status.DELETED);
        assertThat(findOutput(first))
                .isNotEmpty()
                .allSatisfy(output -> {
                    assertThat(output.getStatus()).isEqualTo(Status.UNLOCKED);
                    assertThat(output.getId()).isGreaterThan(unlocked.getId());
                });
        assertThat(processorTaskHeartbeat.size()).isZero();
    }

    @Test
    void runThatHasLostItsTaskBeforeItClearsUpDeletesNothing() {
        final ProcessorTask first = assignEventsTask(true);
        processorTaskHeartbeat.deregister(first);
        processorTaskDao.reapDeadTasks(Instant.now().plusSeconds(60));
        final ProcessorTask second = assign();
        // It stalls, its task is reaped, and a third run writes a stream.
        processorTaskDao.reapDeadTasks(Instant.now().plusSeconds(60));
        final ProcessorTask third = assign();
        final Meta output = createOutput(third, third.getId(), Status.LOCKED);

        commonTranslationTestHelper.process(second);

        assertThat(getStatus(output)).isEqualTo(Status.LOCKED);
        assertThat(findOutput(third)).extracting(Meta::getId).containsExactly(output.getId());
        assertThat(getTask(third).getStatus()).isEqualTo(TaskStatus.PROCESSING);
        assertThat(getTask(third).getVersion()).isEqualTo(third.getVersion());
        // Only the third run is still registered.
        assertThat(processorTaskHeartbeat.size()).isOne();
        assertThat(processorTaskHeartbeat.endLostRuns()).isZero();
        processorTaskHeartbeat.deregister(third);
    }

    @Test
    void completeRunClearsTheOutputOfAFinishedTaskOfTheSamePipeline() {
        final ProcessorTask task = assignEventsTask(true);
        // There is no such task, so it must have finished.
        final Meta old = createOutput(task, task.getId() + 1_000_000, Status.UNLOCKED);

        commonTranslationTestHelper.process(task);

        assertThat(getTask(task).getStatus()).isEqualTo(TaskStatus.COMPLETE);
        assertThat(getStatus(old)).isEqualTo(Status.DELETED);
        assertThat(findOutput(task)).isNotEmpty();
    }

    @Test
    void runWhoseTaskIsAbortedClearsItsOwnOutputAndNothingElse() {
        final ProcessorTask task = assignEventsTask(true);
        // This would be out of date if the run were to complete.
        final Meta other = createOutput(task, task.getId() + 1_000_000, Status.UNLOCKED);
        processorTaskDao.changeTaskStatus(task, null, TaskStatus.ABORTED, 1L, 2L, "Expected", false);

        // The run doesn't know until it tries to set its status.
        assertThat(commonTranslationTestHelper.process(task).getWritten()).isPositive();

        assertThat(getTask(task).getStatus()).isEqualTo(TaskStatus.ABORTED);
        assertThat(getTask(task).getReason()).isEqualTo("Expected");
        assertThat(findOutput(task)).isEmpty();
        assertThat(getStatus(other)).isEqualTo(Status.UNLOCKED);
    }

    @Test
    void runThatHitsAFaultIsRunAgainAndLeavesOneSetOfOutput() {
        final ProcessorTask task = assignEventsTask(true);
        // What a run that hit a fault leaves, as it keeps its error stream.
        final Meta error = createOutput(task, task.getId(), Status.UNLOCKED);
        processorTaskHeartbeat.deregister(task);
        processorTaskDao.changeTaskStatus(
                task, nodeInfo.getThisNodeName(), TaskStatus.RETRY, 1L, 2L, "Expected", true);
        processorTaskDao.putBackRetryTasks(
                task.getProcessorFilter().getId(), Instant.now().plusSeconds(60), 10);

        commonTranslationTestHelper.process(assign());

        assertThat(getTask(task).getStatus()).isEqualTo(TaskStatus.COMPLETE);
        assertThat(getTask(task).getFaultCount()).isOne();
        assertThat(getTask(task).getReason()).isNull();
        assertThat(getStatus(error)).isEqualTo(Status.DELETED);
        assertThat(findOutput(task)).isNotEmpty();
    }

    // Processes reference data and then gets the task that uses it.
    private ProcessorTask assignEventsTask(final boolean claimTasksOnWorker) {
        processorTaskTestHelper.setClaimTasksOnWorker(claimTasksOnWorker);
        commonTranslationTestHelper.setup();
        processorTaskTestHelper.createAndQueueTasks();
        ProcessorTask task = assign();
        while (!StreamTypeNames.RAW_EVENTS.equals(metaService.getMeta(task.getMetaId()).getTypeName())) {
            commonTranslationTestHelper.process(task);
            task = assign();
        }
        return task;
    }

    private ProcessorTask assign() {
        processorTaskAvailability.clear();
        final List<ProcessorTask> tasks = processorTaskTestHelper.assignTasks(1);
        assertThat(tasks).hasSize(1);
        return tasks.getFirst();
    }

    private ProcessorTask getTask(final ProcessorTask task) {
        return processorTaskService.find(new ExpressionCriteria())
                .stream()
                .filter(other -> other.getId() == task.getId())
                .findFirst()
                .orElseThrow();
    }

    private Meta createOutput(final ProcessorTask task, final long taskId, final Status status) {
        final Meta parent = metaService.getMeta(task.getMetaId());
        final ProcessorFilter filter = task.getProcessorFilter();
        return metaService.create(MetaProperties.builder()
                .feedName(parent.getFeedName())
                .typeName(StreamTypeNames.EVENTS)
                .parent(parent)
                .processorUuid(filter.getProcessor().getUuid())
                .pipelineUuid(filter.getProcessor().getPipelineUuid())
                .processorFilterId(filter.getId())
                .processorTaskId(taskId)
                .build(), status);
    }

    private Status getStatus(final Meta meta) {
        return metaService.getMeta(meta.getId(), true).getStatus();
    }

    private List<Meta> findOutput(final ProcessorTask task) {
        return metaService.find(new FindMetaCriteria(ExpressionOperator.builder()
                        .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, task.getMetaId())
                        .addIdTerm(MetaFields.META_PROCESSOR_TASK_ID, Condition.EQUALS, task.getId())
                        .build()))
                .stream()
                .filter(output -> !Status.DELETED.equals(output.getStatus()))
                .toList();
    }
}
