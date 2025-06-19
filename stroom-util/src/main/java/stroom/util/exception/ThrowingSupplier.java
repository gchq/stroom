package stroom.util.exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable> {

    T get() throws E;

    /**
     * Wraps a function that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <T, E extends Throwable> Supplier<T> unchecked(final ThrowingSupplier<T, E> s) {
        return () -> {
            try {
                return s.get();
            } catch (final Throwable e) {
                if (e instanceof final IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } else {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
