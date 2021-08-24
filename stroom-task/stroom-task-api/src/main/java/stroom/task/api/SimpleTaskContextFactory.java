package stroom.task.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleTaskContextFactory implements TaskContextFactory {

    @Override
    public Runnable context(final String taskName,
                            final Consumer<TaskContext> consumer) {
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public Runnable childContext(final String taskName, final Consumer<TaskContext> consumer) {
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public Runnable childContext(final TaskContext parentContext,
                                 final String taskName,
                                 final Consumer<TaskContext> consumer) {
        Objects.requireNonNull(parentContext, "Expecting a parent context when creating a child context");
        return () -> consumer.accept(new SimpleTaskContext());
    }

    @Override
    public <R> Supplier<R> contextResult(final String taskName, final Function<TaskContext, R> function) {
        return () -> function.apply(new SimpleTaskContext());
    }

    @Override
    public <R> Supplier<R> contextResult(final TaskContext parentContext,
                                         final String taskName,
                                         final Function<TaskContext, R> function) {
        return () -> function.apply(new SimpleTaskContext());
    }
}
