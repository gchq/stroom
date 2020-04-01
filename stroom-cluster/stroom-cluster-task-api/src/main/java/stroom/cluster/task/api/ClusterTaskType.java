package stroom.cluster.task.api;

import java.util.Objects;

public class ClusterTaskType {
    private final Class<?> taskClass;

    public <T extends ClusterTask<?>> ClusterTaskType(final Class<T> taskClass) {
        this.taskClass = taskClass;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ClusterTaskType taskType = (ClusterTaskType) o;
        return Objects.equals(taskClass, taskType.taskClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskClass);
    }

    @Override
    public String toString() {
        return "ClusterTaskType{" +
                "taskClass=" + taskClass +
                '}';
    }
}
