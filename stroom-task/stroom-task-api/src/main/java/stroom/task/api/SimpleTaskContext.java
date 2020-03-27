package stroom.task.api;

import stroom.task.shared.TaskId;

import java.util.function.Supplier;

public class SimpleTaskContext implements TaskContext {
    @Override
    public void setName(final String name) {
    }

    @Override
    public void info(final Supplier<String> messageSupplier) {
    }

    @Override
    public TaskId getTaskId() {
        return null;
    }

    @Override
    public void terminate() {
    }

    @Override
    public <U> WrappedSupplier<U> sub(final Supplier<U> supplier) {
        return new WrappedSupplier<>(this, supplier);
    }

    @Override
    public WrappedRunnable sub(final Runnable runnable) {
        return new WrappedRunnable(this, runnable);
    }
}
