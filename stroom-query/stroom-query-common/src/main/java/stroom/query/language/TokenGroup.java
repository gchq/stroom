package stroom.query.language;

import java.util.List;
import java.util.Objects;

public class TokenGroup extends AbstractTokenGroup {

    private final String name;

    public TokenGroup(final TokenType tokenType,
                      final char[] chars,
                      final int start,
                      final int end,
                      final List<AbstractToken> children,
                      final String name) {
        super(tokenType, chars, start, end, children);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    void append(final StringBuilder sb, final boolean indent, final int depth) {
        appendIndent(indent, sb, depth);
        sb.append("<");
        sb.append(tokenType);
        if (name != null) {
            sb.append(" NAME=\"");
            sb.append(name);
            sb.append("\"");
        }
        sb.append(">");
        appendNewLine(indent, sb);
        appendChildren(sb, indent, depth + 1);
        appendIndent(indent, sb, depth);
        appendCloseType(sb);
        appendNewLine(indent, sb);
    }

    public static class Builder extends AbstractTokenGroupBuilder<TokenGroup, Builder> {

        private String name;

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

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
            return new TokenGroup(tokenType, chars, start, end, children, name);
        }
    }
}
