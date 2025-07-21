package stroom.util.exception;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface ThrowingBiConsumer<T, U, E extends Throwable> {

    void accept(T t, U u) throws E;

    /**
     * Wraps a consumer that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <T, U, E extends Throwable> BiConsumer<T, U> unchecked(final ThrowingBiConsumer<T, U, E> biConsumer) {
        return (t, u) -> {
            try {
                biConsumer.accept(t, u);
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
