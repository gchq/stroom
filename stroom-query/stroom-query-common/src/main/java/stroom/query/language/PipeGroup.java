package stroom.query.language;

import java.util.List;
import java.util.Objects;

public class PipeGroup extends AbstractTokenGroup {
    public enum PipeOperation {
        WHERE, FILTER, AND, OR, NOT, EVAL, TABLE, LIMIT, SORT, GROUP, HAVING;
    }

    private final String name;
    private PipeOperation pipeOperation;

    public PipeGroup(final TokenType tokenType,
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

    public PipeOperation getPipeOperation() {
        if (pipeOperation == null) {
            // Lazily evaluate the pipe operation.
            try {
                pipeOperation = PipeOperation.valueOf(name.toUpperCase());
            } catch (RuntimeException e) {
                throw new TokenException(this, "Unknown pipe operation: " + name);
            }
        }
        return pipeOperation;
    }

    @Override
    void appendAttributes(final StringBuilder sb) {
        sb.append(" PIPE_OPERATION=\"");
        sb.append(name);
        sb.append("\"");
    }

    public static class Builder extends AbstractTokenGroupBuilder<PipeGroup, Builder> {

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
        public PipeGroup build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            Objects.requireNonNull(children, "Null children");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new PipeGroup(tokenType, chars, start, end, children, name);
        }
    }
}
