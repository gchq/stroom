package stroom.proxy.app.handler;

public class ForwardException extends Exception {

    private final boolean isRecoverable;

    private ForwardException(final String message,
                             final boolean isRecoverable,
                             final Throwable cause) {
        super(message, cause);
        this.isRecoverable = isRecoverable;
    }

    public static ForwardException recoverable(final String message, final Throwable cause) {
        return new ForwardException(message, true, cause);
    }

    public static ForwardException recoverable(final String message) {
        return new ForwardException(message, true, null);
    }

    public static ForwardException nonRecoverable(final String message, final Throwable cause) {
        return new ForwardException(message, false, cause);
    }

    public static ForwardException nonRecoverable(final String message) {
        return new ForwardException(message, false, null);
    }

    public boolean isRecoverable() {
        return isRecoverable;
    }
}
