package stroom.query.api.token;

import java.util.Arrays;
import java.util.Objects;

public class ParamToken extends Token {

    private final String unescaped;

    public ParamToken(final TokenType tokenType,
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

    public static class Builder extends AbstractTokenBuilder<ParamToken, Builder> {

        public Builder() {
        }

        public Builder(final ParamToken token) {
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
        public ParamToken build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            final String string = new String(Arrays.copyOfRange(chars, start, end + 1));

            int end = 0;
            int start = string.indexOf("${", end);
            if (start == -1) {
                start = 0;
            } else {
                start += 2;
            }

            end = string.indexOf("}", start);
            if (end == -1) {
                end = string.length();
            }

            final String unescaped = string.substring(start, end);
            return new ParamToken(tokenType, chars, this.start, this.end, unescaped);
        }
    }
}
