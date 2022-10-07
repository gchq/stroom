package stroom.util.exception;

import java.util.function.Consumer;

public interface InterruptibleConsumer<T> {

    void accept(T t) throws InterruptedException;

    /**
     * Wraps a consumer that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link RuntimeException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Consumer<T> unchecked(InterruptibleConsumer<T> consumer) {
        return (t) -> {
            try {
                consumer.accept(t);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted: " + e.getMessage(), e);
            }
        };
    }
}
