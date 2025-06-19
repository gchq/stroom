package stroom.query.api.token;

import java.util.Objects;

public class QuotedStringToken extends Token {

    private final String unescaped;

    public QuotedStringToken(final TokenType tokenType,
                             final char[] chars,
                             final int start,
                             final int end,
                             final String unescaped) {
        super(tokenType, chars, start, end);
        this.unescaped = unescaped;
    }

    @Override
    public String getUnescapedText() {
        return unescaped;
    }

    public static class Builder extends AbstractTokenBuilder<QuotedStringToken, Builder> {

        public Builder() {
        }

        public Builder(final QuotedStringToken token) {
            this.tokenType = token.tokenType;
            this.chars = token.chars;
            this.start = token.start;
            this.end = token.end;
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        public QuotedStringToken build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new QuotedStringToken(tokenType, chars, start, end,
                    QuotedStringUtil.unescape(chars, start, end, '\\'));
        }
    }
}
