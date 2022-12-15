package stroom.query.language;

import java.util.List;
import java.util.Objects;

public class TokenGroup extends AbstractTokenGroup {

    public TokenGroup(final TokenType tokenType,
                      final char[] chars,
                      final int start,
                      final int end,
                      final List<AbstractToken> children) {
        super(tokenType, chars, start, end, children);
    }

    public static class Builder extends AbstractTokenGroupBuilder<TokenGroup, Builder> {

        @Override
        Builder self() {
            return this;
        }

        @Override
        public TokenGroup build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            Objects.requireNonNull(children, "Null children");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new TokenGroup(tokenType, chars, start, end, children);
        }
    }
}
