package stroom.util.exception;

import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

@FunctionalInterface
public interface InterruptibleFunction<T, R> {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InterruptibleFunction.class);

    R apply(T t) throws InterruptedException;

    /**
     * Wraps a function that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T, R> Function<T, R> unchecked(final InterruptibleFunction<T, R> function) {
        return unchecked(function, null);
    }

    /**
     * Wraps a function that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static <T, R> Function<T, R> unchecked(final InterruptibleFunction<T, R> function,
                                           final Supplier<String> debugMsgSupplier) {
        return t -> {
            try {
                return function.apply(t);
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
