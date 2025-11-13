package stroom.credentials.impl;

/**
 * Exception type to throw when we need a RuntimeException to conform
 * to an interface.
 */
public class CredentialsJgitRuntimeException extends RuntimeException {

    public CredentialsJgitRuntimeException(final String message) {
        super(message);
    }

    public CredentialsJgitRuntimeException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public CredentialsJgitRuntimeException(final Throwable cause) {
        super(cause);
    }
}
