package stroom.util.exception;

import java.io.IOException;
import java.io.UncheckedIOException;

@FunctionalInterface
public interface ThrowingRunnable<E extends Throwable> {

    void run() throws E;

    /**
     * Wraps a runnable that throws a checked exception with a catch block that will wrap
     * any thrown exception with a {@link RuntimeException}, thus making it unchecked and
     * usable in a lambda.
     */
    static <E extends Throwable> Runnable unchecked(final ThrowingRunnable<E> runnable) {
        return () -> {
            try {
                runnable.run();
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
