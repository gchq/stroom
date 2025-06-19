package stroom.security.impl.db;

public class SecurityException extends RuntimeException {

    public SecurityException() {
    }

    public SecurityException(final String message) {
        super(message);
    }

    public SecurityException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public SecurityException(final Throwable cause) {
        super(cause);
    }

    public SecurityException(final String message,
                             final Throwable cause,
                             final boolean enableSuppression,
                             final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
