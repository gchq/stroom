package stroom.query.language;

public class TokenException extends RuntimeException {
    private final Token token;

    public TokenException(final Token token, final String message) {
        super(message);
        this.token = token;
    }
}
