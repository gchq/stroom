package stroom.util.concurrent;

import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CancellationException;

public final class InterruptionUtil {

    private InterruptionUtil() {
        // Utility class.
    }

    public static boolean isInterruption(final Throwable exception) {
        Throwable throwable = exception;
        while (throwable != null) {
            if (throwable instanceof InterruptedException ||
                    throwable instanceof ClosedByInterruptException ||
                    throwable instanceof UncheckedInterruptedException ||
                    throwable instanceof CancellationException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
