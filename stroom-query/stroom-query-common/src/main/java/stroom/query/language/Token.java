package stroom.query.language;

import java.util.Objects;

public class Token extends AbstractToken {

    public Token(final TokenType tokenType, final char[] chars, final int start, final int end) {
        super(tokenType, chars, start, end);
    }

    public static class Builder extends AbstractTokenBuilder<Token, Builder> {

        @Override
        Builder self() {
            return this;
        }

        @Override
        public Token build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new Token(tokenType, chars, start, end);
        }
    }
}
