package stroom.processor.api;

import java.util.Objects;

public class TaskType {
    private final String name;

    public TaskType(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TaskType taskType = (TaskType) o;
        return Objects.equals(name, taskType.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
