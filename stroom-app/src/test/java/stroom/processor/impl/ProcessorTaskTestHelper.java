/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.processor.shared.ProcessorTaskList;
import stroom.task.shared.TaskId;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ProcessorTaskTestHelper {

    private final PrioritisedFilters prioritisedFilters;
    private final ProcessorTaskCreator processorTaskCreator;
    private final ProcessorTaskQueueManager processorTaskQueueManager;
    private final Provider<ProcessorConfig> processorConfigProvider;
    private final NodeInfo nodeInfo;

    @Inject
    public ProcessorTaskTestHelper(final PrioritisedFilters prioritisedFilters,
                                   final ProcessorTaskCreator processorTaskCreator,
                                   final ProcessorTaskQueueManager processorTaskQueueManager,
                                   final Provider<ProcessorConfig> processorConfigProvider,
                                   final NodeInfo nodeInfo) {
        this.prioritisedFilters = prioritisedFilters;
        this.processorTaskCreator = processorTaskCreator;
        this.processorTaskQueueManager = processorTaskQueueManager;
        this.processorConfigProvider = processorConfigProvider;
        this.nodeInfo = nodeInfo;
    }

    public void createAndQueueTasks() {
        processorConfigProvider.get().setSkipNonProducingFiltersDuration(StroomDuration.ZERO);
        prioritisedFilters.clear();
        processorTaskCreator.exec();
        processorTaskQueueManager.exec();
    }

    public ProcessorTaskList assignTasks(final int count) {
        return processorTaskQueueManager.assignTasks(TaskId.createTestTaskId(),
                nodeInfo.getThisNodeName(), count);
    }
}
