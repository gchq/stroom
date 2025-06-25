package stroom.util.exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {

    void accept(T t) throws E;

    /**
     * Wraps a consumer that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <T, E extends Throwable> Consumer<T> unchecked(final ThrowingConsumer<T, E> consumer) {
        return (t) -> {
            try {
                consumer.accept(t);
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
