package stroom.query.language;

public class TokenException extends RuntimeException {
    private final AbstractToken token;

    public TokenException(final AbstractToken token, final String message) {
        super(message);
        this.token = token;
    }
}
