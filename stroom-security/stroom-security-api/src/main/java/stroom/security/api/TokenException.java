package stroom.security.api;

public class TokenException extends Exception {

    public TokenException(final String message) {
        super(message);
    }

    public TokenException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public TokenException(final Throwable cause) {
        super(cause);
    }
}
