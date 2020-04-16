package stroom.task.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TaskContextFactory {
    Runnable context(String taskName, Consumer<TaskContext> consumer);

    Runnable context(TaskContext parentContext, String taskName, Consumer<TaskContext> consumer);

    <R> Supplier<R> contextResult(String taskName, Function<TaskContext, R> function);

    <R> Supplier<R> contextResult(TaskContext parentContext, String taskName, Function<TaskContext, R> function);

    TaskContext currentContext();
}
