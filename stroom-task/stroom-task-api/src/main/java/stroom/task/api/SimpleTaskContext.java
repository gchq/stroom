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
}
