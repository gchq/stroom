package stroom.query.impl;

import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.TokenException;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TokenError;

public class TokenExceptionUtil {


    public static TokenError toTokenError(final TokenException tokenException) {
        final AbstractToken token = tokenException.getToken();
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
        return new TokenError(from, to, tokenException.getMessage());
    }
}
