package stroom.query.language;

import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TokenError;

public class TokenException extends RuntimeException {

    private final AbstractToken token;

    public TokenException(final AbstractToken token, final String message) {
        super(message);
        this.token = token;
    }

    public AbstractToken getToken() {
        return token;
    }

    public TokenError toTokenError() {
        final char[] chars = token.getChars();
        int lineNo = 1;
        int colNo = 0;
        int index = 0;
        for (; index < token.getStart(); index++) {
            final char c = chars[index];
            if (c == '\n') {
                lineNo++;
                colNo = 0;
            } else if (c == '\r') {
                // Ignore.
            } else {
                colNo++;
            }
        }
        final DefaultLocation from = new DefaultLocation(lineNo, colNo);
        for (; index <= token.getEnd(); index++) {
            final char c = chars[index];
            if (c == '\n') {
                lineNo++;
                colNo = 0;
            } else if (c == '\r') {
                // Ignore.
            } else {
                colNo++;
            }
        }
        final DefaultLocation to = new DefaultLocation(lineNo, colNo);
        return new TokenError(from, to, getMessage());
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
