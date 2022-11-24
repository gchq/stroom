package stroom.util.exception;

import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface InterruptibleConsumer<T> {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InterruptibleConsumer.class);

    void accept(T t) throws InterruptedException;

    /**
     * Wraps a consumer that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Consumer<T> unchecked(final InterruptibleConsumer<T> consumer) {
        return unchecked(consumer, null);
    }

    /**
     * Wraps a consumer that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Consumer<T> unchecked(final InterruptibleConsumer<T> consumer,
                                     final Supplier<String> debugMsgSupplier) {
        return (t) -> {
            try {
                consumer.accept(t);
            } catch (InterruptedException e) {
                LOGGER.debug(() ->
                                NullSafe.getOrElse(
                                        debugMsgSupplier,
                                        Supplier::get,
                                        "Interrupted"),
                        e);
                Thread.currentThread().interrupt();
                throw new UncheckedInterruptedException("Interrupted: " + e.getMessage(), e);
            }
        };
    }
}
