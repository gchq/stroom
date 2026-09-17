/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699 Phase 2. The claim itself: FIFO, atomic CREATED to PROCESSING, and the property the
 * whole decentralised design rests on - that two nodes claiming the same filter at the same moment
 * get <em>distinct</em> tasks on the first try rather than one winning and the rest retrying. See
 * PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.3.
 */
class TestProcessorTaskClaiming extends AbstractProcessorTest {

    private static final Instant SEED_TIME = Instant.ofEpochMilli(1_000);

    @Test
    void claimTakesTheOldestWaitingTasksStraightToProcessing() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final List<Long> created = createTasks(filter, 5);
        // Not claimable: wrong status, or belonging to a different filter.
        final long alreadyRunning = createProcessorTask(filter, TaskStatus.PROCESSING, NODE2, FEED);
        final ProcessorFilter otherFilter = createProcessorFilter(processor);
        final long otherFiltersTask = createProcessorTask(otherFilter, TaskStatus.CREATED, null, FEED);

        final List<ProcessorTask> claimed = processorTaskDao.claimTasks(filter.getId(), NODE1, 3);

        assertThat(claimed).extracting(ProcessorTask::getId)
                .describedAs("oldest first, so FIFO comes from the index scan rather than a sort")
                .containsExactly(created.get(0), created.get(1), created.get(2));
        claimed.forEach(task -> {
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
            assertThat(task.getNodeName()).isEqualTo(NODE1);
            assertThat(task.getStartTimeMs()).isNotNull();
            assertThat(task.getProcessorFilter())
                    .describedAs("the handler needs the filter, so it comes back with the task")
                    .isNotNull();
        });
        assertThat(getTaskStatus(created.get(3))).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskStatus(alreadyRunning)).isEqualTo(TaskStatus.PROCESSING);
        assertThat(getTaskNodeId(alreadyRunning)).isEqualTo(processorNodeCache.getOrCreate(NODE2));
        assertThat(getTaskStatus(otherFiltersTask)).isEqualTo(TaskStatus.CREATED);
    }

    @Test
    void claimBumpsTheVersionSoALostLeaseIsDetectable() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final long taskId = createProcessorTask(filter, TaskStatus.CREATED, null, FEED);
        final int versionBefore = getTaskVersion(taskId);

        processorTaskDao.claimTasks(filter.getId(), NODE1, 1);

        assertThat(getTaskVersion(taskId)).isEqualTo(versionBefore + 1);
    }

    @Test
    void claimingMoreThanExistsTakesWhatThereIs() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        createTasks(filter, 2);

        assertThat(processorTaskDao.claimTasks(filter.getId(), NODE1, 10)).hasSize(2);
        assertThat(processorTaskDao.claimTasks(filter.getId(), NODE1, 10))
                .describedAs("a filter that has run out yields nothing rather than failing")
                .isEmpty();
        assertThat(processorTaskDao.claimTasks(filter.getId(), NODE1, 0)).isEmpty();
    }

    @Test
    void concurrentClaimsGetDistinctTasks() throws Exception {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final int nodeCount = 4;
        final int perNode = 5;
        createTasks(filter, nodeCount * perNode);

        // All four "nodes" go for the head of the same filter at the same moment. Without SKIP
        // LOCKED they would serialise behind one another's row locks and, once the winner
        // committed, find the rows no longer CREATED - the wasted-round-trip behaviour the design
        // exists to avoid.
        final CyclicBarrier barrier = new CyclicBarrier(nodeCount);
        final List<Thread> threads = new ArrayList<>();
        final List<List<ProcessorTask>> results = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            final String nodeName = "node" + i;
            final List<ProcessorTask> result = new ArrayList<>();
            synchronized (results) {
                results.add(result);
            }
            final Thread thread = new Thread(() -> {
                try {
                    barrier.await();
                    result.addAll(processorTaskDao.claimTasks(filter.getId(), nodeName, perNode));
                } catch (final Exception e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (final Thread thread : threads) {
            thread.join();
        }

        assertThat(errors).isEmpty();
        final List<Long> allClaimed = results.stream()
                .flatMap(List::stream)
                .map(ProcessorTask::getId)
                .toList();
        assertThat(Set.copyOf(allClaimed))
                .describedAs("no task may be claimed twice - this is the correctness property")
                .hasSize(allClaimed.size());
        assertThat(allClaimed)
                .describedAs("and every node gets work, rather than one winning and the rest "
                             + "wasting a round trip")
                .hasSize(nodeCount * perNode);
        results.forEach(result -> assertThat(result).isNotEmpty());
    }

    @Test
    void sweepReturnsStrandedQueueResidueToCreated() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final long queued = createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED, SEED_TIME);
        final long assigned = createProcessorTask(filter, TaskStatus.ASSIGNED, NODE1, FEED, SEED_TIME);
        final long processing = createProcessorTask(filter, TaskStatus.PROCESSING, NODE1, FEED, SEED_TIME);
        final long complete = createProcessorTask(filter, TaskStatus.COMPLETE, NODE1, FEED, SEED_TIME);
        final int queuedVersion = getTaskVersion(queued);

        final long swept = processorTaskDao.sweepQueuedTasks(SEED_TIME.plusMillis(1));

        assertThat(swept).isEqualTo(2);
        assertThat(getTaskStatus(queued)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(queued)).isNull();
        assertThat(getTaskVersion(queued))
                .describedAs("the version bump is what makes an old mode assignment of this task "
                             + "abandon rather than force its write")
                .isEqualTo(queuedVersion + 1);
        assertThat(getTaskStatus(assigned)).isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskStatus(processing))
                .describedAs("live work belongs to the dead task reap, not to this sweep")
                .isEqualTo(TaskStatus.PROCESSING);
        assertThat(getTaskStatus(complete)).isEqualTo(TaskStatus.COMPLETE);
    }

    /**
     * §6 item 1: the sweep is a standing duty, so it can fire while a rolling restart still has
     * nodes running the old mode. That is only safe because a status write that loses its version
     * check is abandoned rather than forced - for <em>every</em> transition, not just completion.
     */
    @Test
    void anOldModeAssignmentOfASweptTaskIsAbandonedNotForced() {
        final Processor processor = createProcessor();
        final ProcessorFilter filter = createProcessorFilter(processor);
        final long taskId = createProcessorTask(filter, TaskStatus.QUEUED, NODE1, FEED, SEED_TIME);

        // The old master still has this task in its in-memory queue, holding the copy it read
        // before the sweep ran.
        final ProcessorTask asQueuedByTheMaster = processorTaskDao.find(new ExpressionCriteria())
                .getValues()
                .getFirst();

        assertThat(processorTaskDao.sweepQueuedTasks(SEED_TIME.plusMillis(1))).isEqualTo(1);

        // Its belated dispatch of that task must not stamp over what the sweep did.
        final ProcessorTask assigned = processorTaskDao.changeTaskStatus(
                asQueuedByTheMaster, NODE1, TaskStatus.ASSIGNED, SEED_TIME.toEpochMilli(), null);

        assertThat(assigned)
                .describedAs("worst case is a wasted assignment, the same outcome as a lost lease")
                .isNull();
        assertThat(getTaskStatus(taskId))
                .describedAs("the task stays claimable, so it gets processed rather than stranded")
                .isEqualTo(TaskStatus.CREATED);
        assertThat(getTaskNodeId(taskId)).isNull();

        // And a worker node claiming it next is entirely normal.
        assertThat(processorTaskDao.claimTasks(filter.getId(), NODE2, 1)).hasSize(1);
    }

    private List<Long> createTasks(final ProcessorFilter filter, final int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> createProcessorTask(filter, TaskStatus.CREATED, null, FEED))
                .toList();
    }
}
