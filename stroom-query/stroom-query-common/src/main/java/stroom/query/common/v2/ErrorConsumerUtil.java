package stroom.query.common.v2;

import java.nio.channels.ClosedByInterruptException;

public final class ErrorConsumerUtil {
    private ErrorConsumerUtil() {
        // Utility class.
    }

    public static boolean isInterruption(final Throwable exception) {
        Throwable throwable = exception;
        while (throwable != null) {
            if (throwable instanceof InterruptedException || throwable instanceof ClosedByInterruptException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }
}
