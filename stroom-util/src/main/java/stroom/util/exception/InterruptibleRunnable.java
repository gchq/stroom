package stroom.util.exception;

import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.function.Supplier;

@FunctionalInterface
public interface InterruptibleRunnable {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InterruptibleRunnable.class);

    void run() throws InterruptedException;

    /**
     * Wraps a runnable that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static Runnable unchecked(InterruptibleRunnable runnable) {
        return unchecked(runnable, null);

    }

    /**
     * Wraps a runnable that throws an {@link InterruptedException} with a catch block that
     * will reset the interrupt flag and wrap the exception with a {@link UncheckedInterruptedException},
     * thus making it unchecked and usable in a lambda.
     */
    static Runnable unchecked(InterruptibleRunnable runnable,
                              final Supplier<String> debugMsgSupplier) {
        return () -> {
            try {
                runnable.run();
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
