package stroom.task.api;

import java.util.function.Supplier;

public class SimpleTaskContext implements TaskContext {
    @Override
    public void setName(final String name) {
    }

    @Override
    public void info(final Supplier<String> messageSupplier) {
    }

    @Override
    public void terminate() {
    }

    @Override
    public <U> Supplier<U> subTask(final Supplier<U> supplier) {
        return supplier;
    }

    @Override
    public Runnable subTask(final Runnable runnable) {
        return runnable;
    }
}
