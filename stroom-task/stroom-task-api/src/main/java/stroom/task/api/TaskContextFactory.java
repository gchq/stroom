package stroom.task.api;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TaskContextFactory {

    Runnable context(final String taskName,
                     final Consumer<TaskContext> consumer);

    Runnable childContext(final String taskName,
                          final Consumer<TaskContext> consumer);

    Runnable childContext(final TaskContext parentContext,
                          final String taskName,
                          final Consumer<TaskContext> consumer);

    <R> Supplier<R> contextResult(final String taskName,
                                  final Function<TaskContext, R> function);

    <R> Supplier<R> contextResult(final TaskContext parentContext,
                                  final String taskName,
                                  final Function<TaskContext, R> function);

//    default <T> Consumer<T> contextConsumer(final String taskName,
//                                            final BiConsumer<TaskContext, T> consumer) {
//        return t ->
//                childContext(taskName,
//                        taskContext ->
//                                consumer.accept(taskContext, t))
//                        .run();
//    }

    default <T> Consumer<T> contextConsumer(final TaskContext parentContext,
                                            final String taskName,
                                            final BiConsumer<TaskContext, T> consumer) {
        return t ->
                childContext(
                        parentContext,
                        taskName,
                        taskContext ->
                                consumer.accept(taskContext, t))
                        .run();
    }
}
