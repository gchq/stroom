package stroom.query.language.token;

import java.util.List;
import java.util.Objects;

public class KeywordGroup extends AbstractTokenGroup {

    public KeywordGroup(final TokenType tokenType,
                        final char[] chars,
                        final int start,
                        final int end,
                        final List<AbstractToken> children) {
        super(tokenType, chars, start, end, children);
    }

    public static class Builder extends AbstractTokenGroupBuilder<KeywordGroup, Builder> {

        @Override
        Builder self() {
            return this;
        }

        @Override
        public KeywordGroup build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            Objects.requireNonNull(children, "Null children");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new KeywordGroup(tokenType, chars, start, end, children);
        }
    }
}
