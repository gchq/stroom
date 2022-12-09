package stroom.query.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TokenGroup extends Token {

    private final List<Token> children;

    public TokenGroup(final TokenType tokenType,
                      final char[] chars,
                      final int start,
                      final int end,
                      final List<Token> children) {
        super(tokenType, chars, start, end);
        this.children = children;
    }

    public List<Token> getChildren() {
        return children;
    }

    @Override
    public void append(final StringBuilder sb, final boolean indent, final int depth) {
        appendIndent(sb, depth);
        appendOpenType(sb);
        if (indent) {
            sb.append("\n");
        }
        for (final Token child : children) {
            child.append(sb, indent, depth + 1);
        }
        appendIndent(sb, depth);
        appendCloseType(sb);
        if (indent) {
            sb.append("\n");
        }
    }

    public static class Builder extends Token.AbstractBuilder<TokenGroup, Builder> {

        private final List<Token> children = new ArrayList<>();

        public Builder addAll(final List<Token> tokens) {
            this.children.addAll(tokens);
            return self();
        }

        public Builder add(final Token token) {
            this.children.add(token);
            return self();
        }

        public boolean isEmpty() {
            return children.isEmpty();
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        public TokenGroup build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new TokenGroup(tokenType, chars, start, end, new ArrayList<>(children));
        }
    }
}
