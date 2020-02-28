package stroom.util.task;

import java.util.function.Supplier;

public interface TaskWrapper {
    <U> Supplier<U> wrap(Supplier<U> supplier);

    Runnable wrap(Runnable runnable);
}
