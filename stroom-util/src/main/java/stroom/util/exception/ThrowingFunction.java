package stroom.util.exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable> {

    R apply(T t) throws E;

    /**
     * Wraps a function that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <T, R, E extends Throwable> Function<T, R> unchecked(final ThrowingFunction<T, R, E> f) {
        return t -> {
            try {
                return f.apply(t);
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
