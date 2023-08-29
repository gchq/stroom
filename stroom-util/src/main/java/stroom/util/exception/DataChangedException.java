package stroom.util.exception;

/**
 * Indicates that some data that is being modified has been changed by another
 * thread/node.
 */
public class DataChangedException extends RuntimeException {

    public DataChangedException(final String message) {
        super(message);
    }

    public DataChangedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
