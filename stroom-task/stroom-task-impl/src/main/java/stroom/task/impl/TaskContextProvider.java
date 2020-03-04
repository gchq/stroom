package stroom.task.impl;

import stroom.task.api.TaskContext;
import stroom.task.shared.TaskId;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class TaskContextProvider implements Provider<TaskContext> {
    private final TaskManagerImpl taskManager;

    @Inject
    TaskContextProvider(final TaskManagerImpl taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public TaskContext get() {
        final TaskId parentTaskId = CurrentTaskState.currentTaskId();
        return new TaskContextImpl(taskManager, parentTaskId, taskManager.getTaskName(), taskManager.getUserIdentity());
    }
}
