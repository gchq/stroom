/*
 * Copyright 2023 Crown Copyright
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

import stroom.node.api.NodeInfo;
import stroom.processor.shared.ProcessorTask;
import stroom.task.shared.TaskId;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

/**
 * Gets tasks to process the way the running system would, whichever of the two task selection
 * modes is configured (gh-5699, {@code stroom.processor.claimTasksOnWorker}). Tests should not
 * care which is in use, so this is the one place that does.
 */
public class ProcessorTaskTestHelper {

    private final PrioritisedFilters prioritisedFilters;
    private final ProcessorTaskCreator processorTaskCreator;
    private final ProcessorTaskQueueManager processorTaskQueueManager;
    private final ProcessorTaskClaimer processorTaskClaimer;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private final NodeInfo nodeInfo;

    @Inject
    public ProcessorTaskTestHelper(final PrioritisedFilters prioritisedFilters,
                                   final ProcessorTaskCreator processorTaskCreator,
                                   final ProcessorTaskQueueManager processorTaskQueueManager,
                                   final ProcessorTaskClaimer processorTaskClaimer,
                                   final Provider<ProcessorConfig> processorConfigProvider,
                                   final NodeInfo nodeInfo) {
        this.prioritisedFilters = prioritisedFilters;
        this.processorTaskCreator = processorTaskCreator;
        this.processorTaskQueueManager = processorTaskQueueManager;
        this.processorTaskClaimer = processorTaskClaimer;
        this.processorConfigProvider = processorConfigProvider;
        this.nodeInfo = nodeInfo;
    }

    public void createAndQueueTasks() {
        processorConfigProvider.get().setSkipNonProducingFiltersDuration(StroomDuration.ZERO);
        // Tests add data then create tasks in one go, so they can't afford to spend a poll establishing
        // the max meta id. See TestProcessorTaskCreator for coverage of the lagged behaviour itself.
        processorConfigProvider.get().setUseMaxMetaIdFromPreviousPoll(false);
        prioritisedFilters.clear();
        processorTaskCreator.exec();
        if (!isClaimTasksOnWorker()) {
            // Only the master queue needs filling; a claiming node finds created tasks for itself.
            processorTaskQueueManager.exec();
        }
    }

    /**
     * Get up to {@code count} tasks for this node to process, by whichever route the configured
     * mode uses.
     */
    public List<ProcessorTask> assignTasks(final int count) {
        if (isClaimTasksOnWorker()) {
            return processorTaskClaimer.claimTasks(count);
        }
        return processorTaskQueueManager
                .assignTasks(TaskId.createTestTaskId(), nodeInfo.getThisNodeName(), count)
                .getList();
    }

    /**
     * Whether tasks handed out by {@link #assignTasks(int)} arrive already claimed, which is what
     * {@code DataProcessorTaskHandler.exec} needs to know.
     */
    public boolean isClaimTasksOnWorker() {
        return processorConfigProvider.get().isClaimTasksOnWorker();
    }

    /**
     * Select the task selection mode for this test. Worker claiming is experimental and off by
     * default, so a test that wants to cover it has to ask for it. Each test class gets its own
     * injector, so this cannot leak beyond the class that sets it.
     */
    public void setClaimTasksOnWorker(final boolean claimTasksOnWorker) {
        processorConfigProvider.get().setClaimTasksOnWorker(claimTasksOnWorker);
    }
}
