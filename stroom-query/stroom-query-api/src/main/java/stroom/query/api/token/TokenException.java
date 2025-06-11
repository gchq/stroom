package stroom.query.api.token;

public class TokenException extends RuntimeException {

    private final AbstractToken token;

    public TokenException(final AbstractToken token, final String message) {
        super(message);
        this.token = token;
    }

    public AbstractToken getToken() {
        return token;
    }

    @Override
    public String toString() {
        if (token != null) {
            return "TokenException{" +
                    "tokenType=" + token.getTokenType() +
                    ", start=" + token.getStart() +
                    ", end=" + token.getEnd() +
                    ", text=" + token.getText() +
                    ", message=" + getMessage() +
                    '}';
        } else {
            return "TokenException{" +
                    "message=" + getMessage() +
                    '}';
        }
    }
}
