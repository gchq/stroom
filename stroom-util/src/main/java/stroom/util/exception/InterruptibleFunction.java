package stroom.util.exception;

import java.util.function.Function;

@FunctionalInterface
public interface InterruptibleFunction<T, R> {

    R apply(T t) throws InterruptedException;

    /**
     * Wraps a function that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link RuntimeException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T, R> Function<T, R> unchecked(final InterruptibleFunction<T, R> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted: " + e.getMessage(), e);
            }
        };
    }
}
