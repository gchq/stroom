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
        return processorTaskQueueManager.assignTasks(new TaskId(), nodeInfo.getThisNodeName(), count);
    }
}
