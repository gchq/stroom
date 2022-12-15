package stroom.query.language;

import java.util.List;
import java.util.Objects;

public class FunctionGroup extends AbstractTokenGroup {

    private final String name;

    public FunctionGroup(final TokenType tokenType,
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

    @Override
    void appendAttributes(final StringBuilder sb) {
        sb.append(" FUNCTION_NAME=\"");
        sb.append(name);
        sb.append("\"");
    }

    public static class Builder extends AbstractTokenGroupBuilder<FunctionGroup, Builder> {

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
        public FunctionGroup build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            Objects.requireNonNull(children, "Null children");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new FunctionGroup(tokenType, chars, start, end, children, name);
        }
    }
}
