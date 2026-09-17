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

import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.job.api.DistributedTask;
import stroom.node.api.NodeInfo;
import stroom.processor.shared.AssignTasksRequest;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskList;
import stroom.processor.shared.ProcessorTaskResource;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.TaskId;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * gh-5699. The switch between the two task selection modes, which is the one piece of worker
 * claiming that lives on the ordinary processing path rather than inside the claiming classes.
 * <p>
 * {@code stroom.processor.claimTasksOnWorker} is <b>off by default</b>, so nothing here exercises
 * the claiming branch in a normal test run - hence this test, which drives both branches
 * explicitly. Without it the claiming wiring could be broken by an unrelated change and nothing
 * would say so until someone turned the mode on in anger.
 */
class TestDataProcessorTaskFactory {

    private static final String THIS_NODE = "node1";
    private static final String MASTER_NODE = "master";

    private final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
    private final ProcessorTaskResource processorTaskResource = Mockito.mock(ProcessorTaskResource.class);
    private final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
    private final ProcessorTaskClaimer processorTaskClaimer = Mockito.mock(ProcessorTaskClaimer.class);
    private final ProcessorConfig processorConfig = new ProcessorConfig();

    /**
     * Records what {@code alreadyClaimed} each task was handed to the handler with, which is what
     * tells the handler whether it still has to move the task to PROCESSING itself.
     */
    private final List<Boolean> alreadyClaimedFlags = new ArrayList<>();

    @Test
    void defaultIsTheMasterQueue() {
        // Worker claiming is experimental. If this ever fails because the default was flipped, the
        // decision needs making deliberately rather than by a passing test.
        assertThat(new ProcessorConfig().isClaimTasksOnWorker())
                .isFalse();
    }

    @Test
    void claimingModeClaimsLocallyAndTellsTheHandlerSo() {
        processorConfig.setClaimTasksOnWorker(true);
        Mockito.when(processorTaskClaimer.claimTasks(3))
                .thenReturn(List.of(task(1), task(2)));

        final List<DistributedTask> tasks = factory().fetch(THIS_NODE, 3);

        assertThat(tasks).hasSize(2);
        assertThat(alreadyClaimedFlags).containsExactly(true, true);
        // No master node was needed, and none was asked for. This is the whole point of the mode.
        Mockito.verifyNoInteractions(targetNodeSetFactory, processorTaskResource);
    }

    @Test
    void queueModeAsksTheMasterAndTellsTheHandlerSo() throws Exception {
        processorConfig.setClaimTasksOnWorker(false);
        givenAMasterNodeAssigning(task(1), task(2), task(3));

        final List<DistributedTask> tasks = factory().fetch(THIS_NODE, 3);

        assertThat(tasks).hasSize(3);
        assertThat(alreadyClaimedFlags).containsExactly(false, false, false);
        Mockito.verifyNoInteractions(processorTaskClaimer);
    }

    @Test
    void failedClaimYieldsNoTasksRatherThanBlowingUpTheFetcher() {
        processorConfig.setClaimTasksOnWorker(true);
        Mockito.when(processorTaskClaimer.claimTasks(Mockito.anyInt()))
                .thenThrow(new RuntimeException("Database is down"));

        assertThat(factory().fetch(THIS_NODE, 3)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void claimingModeAbandonsLocallyRatherThanViaTheMaster() throws Exception {
        processorConfig.setClaimTasksOnWorker(true);
        final DataProcessorTaskFactory factory = factory();
        final List<DistributedTask> tasks = distributedTasks(factory, task(1), task(2));

        assertThat(factory.abandon(THIS_NODE, tasks)).isTrue();

        final ArgumentCaptor<List<ProcessorTask>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(processorTaskClaimer).abandonTasks(captor.capture());
        assertThat(captor.getValue())
                .extracting(ProcessorTask::getId)
                .containsExactly(1L, 2L);
        // Nothing is asked of a master that may not exist.
        Mockito.verifyNoInteractions(targetNodeSetFactory, processorTaskResource);
    }

    @Test
    void queueModeAbandonsViaTheMaster() throws Exception {
        processorConfig.setClaimTasksOnWorker(false);
        Mockito.when(targetNodeSetFactory.isClusterStateInitialised()).thenReturn(true);
        Mockito.when(targetNodeSetFactory.getMasterNode()).thenReturn(MASTER_NODE);
        Mockito.when(processorTaskResource.abandonTasks(Mockito.eq(MASTER_NODE), Mockito.any()))
                .thenReturn(true);

        final DataProcessorTaskFactory factory = factory();
        final List<DistributedTask> tasks = distributedTasks(factory, task(1));

        assertThat(factory.abandon(THIS_NODE, tasks)).isTrue();

        final ArgumentCaptor<ProcessorTaskList> captor = ArgumentCaptor.forClass(ProcessorTaskList.class);
        Mockito.verify(processorTaskResource).abandonTasks(Mockito.eq(MASTER_NODE), captor.capture());
        assertThat(captor.getValue().getList())
                .extracting(ProcessorTask::getId)
                .containsExactly(1L);
        Mockito.verifyNoInteractions(processorTaskClaimer);
    }

    /**
     * An abandon that fails has to report failure rather than pretend the tasks were released, or
     * they sit in PROCESSING until their lease expires with nobody expecting it.
     */
    @Test
    void failedLocalAbandonIsReportedAsFailure() throws Exception {
        processorConfig.setClaimTasksOnWorker(true);
        Mockito.doThrow(new RuntimeException("Database is down"))
                .when(processorTaskClaimer).abandonTasks(Mockito.anyList());

        final DataProcessorTaskFactory factory = factory();
        assertThat(factory.abandon(THIS_NODE, distributedTasks(factory, task(1)))).isFalse();
    }

    // --------------------------------------------------------------------------------

    private void givenAMasterNodeAssigning(final ProcessorTask... tasks) throws Exception {
        Mockito.when(targetNodeSetFactory.isClusterStateInitialised()).thenReturn(true);
        Mockito.when(targetNodeSetFactory.getMasterNode()).thenReturn(MASTER_NODE);
        Mockito.when(processorTaskResource.assignTasks(
                        Mockito.eq(MASTER_NODE), Mockito.any(AssignTasksRequest.class)))
                .thenReturn(new ProcessorTaskList(THIS_NODE, List.of(tasks)));
    }

    private List<DistributedTask> distributedTasks(final DataProcessorTaskFactory factory,
                                                   final ProcessorTask... tasks) throws Exception {
        if (processorConfig.isClaimTasksOnWorker()) {
            Mockito.when(processorTaskClaimer.claimTasks(Mockito.anyInt()))
                    .thenReturn(List.of(tasks));
        } else {
            givenAMasterNodeAssigning(tasks);
        }
        final List<DistributedTask> distributedTasks = factory.fetch(THIS_NODE, tasks.length);
        Mockito.clearInvocations(targetNodeSetFactory, processorTaskResource, processorTaskClaimer);
        return distributedTasks;
    }

    private DataProcessorTaskFactory factory() {
        final TaskContext taskContext = Mockito.mock(TaskContext.class);
        Mockito.when(taskContext.getTaskId()).thenReturn(TaskId.createTestTaskId());
        final TaskContextFactory taskContextFactory = Mockito.mock(TaskContextFactory.class);
        Mockito.when(taskContextFactory.current()).thenReturn(taskContext);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);

        final DataProcessorTaskFactory factory = new DataProcessorTaskFactory(
                targetNodeSetFactory,
                processorTaskResource,
                () -> Mockito.mock(DataProcessorTaskHandler.class),
                nodeInfo,
                taskContextFactory,
                processorTaskClaimer,
                () -> processorConfig);
        factory.setRunnableFactory((processorTask, alreadyClaimed) -> {
            alreadyClaimedFlags.add(alreadyClaimed);
            return () -> {
            };
        });
        return factory;
    }

    private ProcessorTask task(final long id) {
        return ProcessorTask.builder()
                .id(id)
                .build();
    }
}
