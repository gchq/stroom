package stroom.processor.impl;

import stroom.processor.shared.ProcessorTask;

import java.util.List;

public class CreatedTasks {
    private final List<ProcessorTask> availableTaskList;
    private final int availableTasksCreated;
    private final int totalTasksCreated;
    private final long eventCount;

    public CreatedTasks(final List<ProcessorTask> availableTaskList, final int availableTasksCreated,
                 final int totalTasksCreated, final long eventCount) {
        this.availableTaskList = availableTaskList;
        this.availableTasksCreated = availableTasksCreated;
        this.totalTasksCreated = totalTasksCreated;
        this.eventCount = eventCount;
    }

    List<ProcessorTask> getAvailableTaskList() {
        return availableTaskList;
    }

    public int getAvailableTasksCreated() {
        return availableTasksCreated;
    }

    int getTotalTasksCreated() {
        return totalTasksCreated;
    }

    public long getEventCount() {
        return eventCount;
    }
}
