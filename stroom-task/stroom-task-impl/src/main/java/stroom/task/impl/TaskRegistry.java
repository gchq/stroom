package stroom.task.impl;

import stroom.task.shared.TaskId;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class TaskRegistry {
    private final Map<TaskId, TaskContextImpl> currentTasks = new ConcurrentHashMap<>(1024, 0.75F, 1024);

    void put(final TaskId taskId, final TaskContextImpl taskContext) {
        currentTasks.put(taskId, taskContext);
    }

    TaskContextImpl get(final TaskId taskId) {
        return currentTasks.get(taskId);
    }

    TaskContextImpl remove(final TaskId taskId) {
        return currentTasks.remove(taskId);
    }

    List<TaskContextImpl> list() {
        return List.copyOf(currentTasks.values());
    }

    @Override
    public String toString() {
        return TaskThreadInfoUtil.getInfo(list());
    }
}
