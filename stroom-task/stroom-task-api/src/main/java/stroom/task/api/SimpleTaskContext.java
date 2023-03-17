package stroom.task.api;

import stroom.task.shared.TaskId;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class SimpleTaskContext implements TaskContext {

    private final AtomicBoolean terminated = new AtomicBoolean();

    @Override
    public void info(final Supplier<String> messageSupplier) {
    }

    @Override
    public TaskId getTaskId() {
        return null;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean isTerminated() {
        return terminated.get();
    }

    public void terminate() {
        terminated.set(true);
    }
}
