package stroom.query.language;

import java.util.Objects;

public abstract class AbstractToken {

    final TokenType tokenType;
    final char[] chars;
    final int start;
    final int end;

    public AbstractToken(final TokenType tokenType,
                         final char[] chars,
                         final int start,
                         final int end) {
        Objects.requireNonNull(tokenType, "Null token type");
        Objects.requireNonNull(chars, "Null chars");
        if (start == -1 || end == -1) {
            throw new IndexOutOfBoundsException();
        }
        this.tokenType = tokenType;
        this.chars = chars;
        this.start = start;
        this.end = end;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public char[] getChars() {
        return chars;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    void appendIndent(final boolean indent, final StringBuilder sb, final int depth) {
        if (indent) {
            for (int i = 0; i < depth; i++) {
                for (int j = 0; j < 3; j++) {
                    sb.append(" ");
                }
            }
        }
    }

    void appendNewLine(final boolean indent, final StringBuilder sb) {
        if (indent) {
            sb.append("\n");
        }
    }

    void appendOpenType(final StringBuilder sb) {
        sb.append("<");
        sb.append(tokenType);
        sb.append(">");
    }

    void appendCloseType(final StringBuilder sb) {
        sb.append("</");
        sb.append(tokenType);
        sb.append(">");
    }

    void append(final StringBuilder sb, final boolean indent, final int depth) {
        appendIndent(indent, sb, depth);
        appendOpenType(sb);
        if (!indent || !tokenType.equals(TokenType.WHITESPACE)) {
            sb.append(chars, start, end - start + 1);
        }
        appendCloseType(sb);
        appendNewLine(indent, sb);
    }

    public String getText() {
        return new String(chars, start, end - start + 1);
    }

    public String toTokenString(final boolean indent) {
        final StringBuilder sb = new StringBuilder();
        append(sb, indent, 0);
        return sb.toString();
    }

    public String toString() {
        return toTokenString(false);
    }

    static abstract class AbstractTokenBuilder<T extends AbstractToken, B extends AbstractTokenBuilder<T, ?>> {

        TokenType tokenType;
        char[] chars;
        int start = -1;
        int end = -1;

        public B tokenType(final TokenType tokenType) {
            this.tokenType = tokenType;
            return self();
        }

        public B chars(final char[] chars) {
            this.chars = chars;
            return self();
        }

        public B start(final int start) {
            this.start = start;
            return self();
        }

        public B end(final int end) {
            this.end = end;
            return self();
        }

        abstract B self();

        public abstract T build();
    }
}
