package stroom.util.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UncheckedInterruptedException extends RuntimeException {

    private static final Logger LOGGER = LoggerFactory.getLogger(UncheckedInterruptedException.class);

    public UncheckedInterruptedException(final String message, final InterruptedException interruptedException) {
        super(message, interruptedException);
    }

    public UncheckedInterruptedException(final InterruptedException interruptedException) {
        super(interruptedException.getMessage(), interruptedException);
    }

    public static UncheckedInterruptedException reset(final InterruptedException e) {
        LOGGER.debug(e.getMessage(), e);
        // Continue to interrupt the thread.
        Thread.currentThread().interrupt();
        return new UncheckedInterruptedException(e);
    }
}
