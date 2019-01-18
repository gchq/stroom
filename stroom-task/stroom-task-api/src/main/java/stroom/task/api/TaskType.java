package stroom.task.api;

import stroom.task.shared.Task;

import java.util.Objects;

public class TaskType {
    private final Class<?> taskClass;

    public <T extends Task> TaskType(final Class<T> taskClass) {
        this.taskClass = taskClass;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TaskType taskType = (TaskType) o;
        return Objects.equals(taskClass, taskType.taskClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskClass);
    }

    @Override
    public String toString() {
        return "TaskType{" +
                "taskClass=" + taskClass +
                '}';
    }
}
