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
import stroom.node.api.NodeInfo;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.test.common.util.test.FileSystemTestUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699. End to end cover for worker node task selection
 * ({@code stroom.processor.claimTasksOnWorker}) against a real database.
 * <p>
 * The mode is <b>experimental and off by default</b>, so every other integration test now runs the
 * master queue path and nothing would otherwise notice claiming breaking. This test turns the mode
 * on explicitly and drives the same route the running system does - the filters this node is
 * eligible for, the availability summary, the {@code SKIP LOCKED} claim, and the task states left
 * behind - so that the feature cannot rot silently while it is switched off.
 * <p>
 * The unit level mechanics live in {@code stroom.processor.impl.dao.TestProcessorTaskClaimer} and
 * friends; the switch between the two modes is covered by
 * {@code stroom.processor.impl.TestDataProcessorTaskFactory}. What is only testable here is the
 * two modes doing the same job over the same real data.
 */
class TestWorkerTaskClaiming extends AbstractCoreIntegrationTest {

    private static final int FILES_PER_FEED = 2;
    private static final int FEED_COUNT = 2;
    private static final int EXPECTED_TASKS = FEED_COUNT * FILES_PER_FEED;

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ProcessorTaskTestHelper processorTaskTestHelper;
    @Inject
    private ProcessorTaskService processorTaskService;
    @Inject
    private ProcessorTaskClaimer processorTaskClaimer;
    @Inject
    private ProcessorTaskAvailability processorTaskAvailability;
    @Inject
    private NodeInfo nodeInfo;
    @Inject
    private Provider<ProcessorConfig> processorConfigProvider;

    @BeforeEach
    void setUp() {
        // A test creates work and immediately looks for it, so it cannot afford to wait out the
        // backoff that stops an idle cluster querying for every filter on every pass. The other
        // interval, the availability summary's, has no setter, so where a test needs a fresh
        // summary it drops the cached one with processorTaskAvailability.clear().
        processorConfigProvider.get().setSkipEmptyFilterFetchDuration(StroomDuration.ZERO);
    }

    /**
     * Whichever mode is in use, the node ends up processing every task that was created, exactly
     * once. This is the property that matters about the two modes and the one a change to either
     * of them could quietly break.
     */
    @ParameterizedTest(name = "claimTasksOnWorker={0}")
    @ValueSource(booleans = {false, true})
    void bothModesHandOutEveryTaskExactlyOnce(final boolean claimTasksOnWorker) {
        processorTaskTestHelper.setClaimTasksOnWorker(claimTasksOnWorker);
        createData();

        processorTaskTestHelper.createAndQueueTasks();
        assertThat(allTasks()).hasSize(EXPECTED_TASKS);

        final List<ProcessorTask> handedOut = processorTaskTestHelper.assignTasks(100);

        assertThat(handedOut).hasSize(EXPECTED_TASKS);
        assertThat(handedOut)
                .extracting(ProcessorTask::getId)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(taskIds());
        // Everything has been handed out, so a second ask gets nothing rather than the same tasks
        // over again.
        processorTaskAvailability.clear();
        assertThat(processorTaskTestHelper.assignTasks(100)).isEmpty();
    }

    /**
     * A claimed task is owned and PROCESSING from the moment it is claimed, not from the moment
     * processing starts - that is what lets the reaper treat a missing heartbeat as a dead node.
     * It must never be left in the master queue's states, which nothing in this mode would clear.
     */
    @Test
    void claimedTasksAreOwnedAndProcessingImmediately() {
        processorTaskTestHelper.setClaimTasksOnWorker(true);
        createData();
        processorTaskTestHelper.createAndQueueTasks();

        final List<ProcessorTask> claimed = processorTaskTestHelper.assignTasks(100);
        assertThat(claimed).hasSize(EXPECTED_TASKS);

        assertThat(allTasks())
                .allSatisfy(task -> {
                    assertThat(task.getStatus()).isEqualTo(TaskStatus.PROCESSING);
                    assertThat(task.getNodeName()).isEqualTo(nodeInfo.getThisNodeName());
                    assertThat(task.getStartTimeMs()).isNotNull();
                });
    }

    /**
     * Nothing else puts a claimed task back, so a node that is asked to give tasks up has to
     * release them itself. Without this they would sit in PROCESSING until the lease expired.
     */
    @Test
    void abandonedTasksGoBackToCreatedAndCanBeClaimedAgain() {
        processorTaskTestHelper.setClaimTasksOnWorker(true);
        createData();
        processorTaskTestHelper.createAndQueueTasks();

        final List<ProcessorTask> claimed = processorTaskTestHelper.assignTasks(100);
        assertThat(claimed).hasSize(EXPECTED_TASKS);

        assertThat(processorTaskClaimer.abandonTasks(claimed)).isEqualTo(EXPECTED_TASKS);

        assertThat(allTasks())
                .extracting(ProcessorTask::getStatus)
                .containsOnly(TaskStatus.CREATED);

        processorTaskAvailability.clear();
        assertThat(processorTaskTestHelper.assignTasks(100))
                .extracting(ProcessorTask::getId)
                .containsExactlyInAnyOrderElementsOf(taskIds());
    }

    /**
     * A node only ever takes as much as it asked for. Over-claiming would hold work that the node
     * has no free slot for and that no other node can then take.
     */
    @Test
    void nodeClaimsNoMoreThanItAsksFor() {
        processorTaskTestHelper.setClaimTasksOnWorker(true);
        createData();
        processorTaskTestHelper.createAndQueueTasks();

        assertThat(processorTaskTestHelper.assignTasks(1)).hasSize(1);

        assertThat(allTasks())
                .filteredOn(task -> TaskStatus.PROCESSING.equals(task.getStatus()))
                .hasSize(1);
    }

    // --------------------------------------------------------------------------------

    private void createData() {
        for (int feed = 0; feed < FEED_COUNT; feed++) {
            final String feedName = FileSystemTestUtil.getUniqueTestString();
            for (int file = 0; file < FILES_PER_FEED; file++) {
                commonTestScenarioCreator.createSample2LineRawFile(feedName, StreamTypeNames.RAW_EVENTS);
            }
            commonTestScenarioCreator.createBasicTranslateStreamProcessor(feedName);
        }
    }

    private List<ProcessorTask> allTasks() {
        return processorTaskService.find(new ExpressionCriteria()).getValues();
    }

    private Set<Long> taskIds() {
        return allTasks()
                .stream()
                .map(ProcessorTask::getId)
                .collect(Collectors.toSet());
    }
}
