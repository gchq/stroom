package stroom.processor.impl;

import stroom.node.api.NodeInfo;
import stroom.processor.shared.ProcessorTaskList;

import javax.inject.Inject;

public class ProcessorTaskTestHelper {

    private final PrioritisedFilters prioritisedFilters;
    private final ProcessorTaskCreator processorTaskCreator;
    private final ProcessorTaskQueueManager processorTaskQueueManager;
    private final NodeInfo nodeInfo;

    @Inject
    public ProcessorTaskTestHelper(final PrioritisedFilters prioritisedFilters,
                                   final ProcessorTaskCreator processorTaskCreator,
                                   final ProcessorTaskQueueManager processorTaskQueueManager,
                                   final NodeInfo nodeInfo) {
        this.prioritisedFilters = prioritisedFilters;
        this.processorTaskCreator = processorTaskCreator;
        this.processorTaskQueueManager = processorTaskQueueManager;
        this.nodeInfo = nodeInfo;
    }

    public void createAndQueueTasks() {
        prioritisedFilters.reset();
        processorTaskCreator.exec();
        processorTaskQueueManager.exec();
    }

    public ProcessorTaskList assignTasks(final int count) {
        return processorTaskQueueManager.assignTasks(nodeInfo.getThisNodeName(), count);
    }
}
