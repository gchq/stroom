package stroom.util.concurrent;

import java.util.function.Supplier;

public interface Guard {
    <R> R acquire(Supplier<R> supplier);

    void destroy();
}
