package stroom.util.exception;

import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.function.Supplier;

@FunctionalInterface
public interface InterruptibleSupplier<T> {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InterruptibleSupplier.class);

    T get() throws InterruptedException;

    /**
     * Wraps a supplier that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Supplier<T> unchecked(final InterruptibleSupplier<T> supplier) {
        return unchecked(supplier, null);
    }

    /**
     * Wraps a supplier that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T> Supplier<T> unchecked(final InterruptibleSupplier<T> supplier,
                                     final Supplier<String> debugMsgSupplier) {
        return () -> {
            try {
                return supplier.get();
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
