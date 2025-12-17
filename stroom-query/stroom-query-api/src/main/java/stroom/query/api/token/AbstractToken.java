/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api.token;

import java.util.Objects;

public abstract class AbstractToken {

    final TokenType tokenType;
    final char[] chars;
    final int start;
    final int end;

    /**
     * @param start Inclusive array index
     * @param end   Inclusive array index
     */
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

    /**
     * @return Inclusive array index
     */
    public int getStart() {
        return start;
    }

    /**
     * @return Inclusive array index
     */
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

    public void append(final StringBuilder sb, final boolean indent, final int depth) {
        appendIndent(indent, sb, depth);
        appendOpenType(sb);
        if (!indent || !tokenType.equals(TokenType.WHITESPACE)) {
            sb.append(chars, start, end - start + 1);
        }
        appendCloseType(sb);
        appendNewLine(indent, sb);
    }

    public String getUnescapedText() {
        return getText();
    }

    public String getText() {
        return new String(chars, start, end - start + 1);
    }

    public String toTokenString(final boolean indent) {
        final StringBuilder sb = new StringBuilder();
        append(sb, indent, 0);
        return sb.toString();
    }

    @Override
    public String toString() {
        return toTokenString(false);
    }


    // --------------------------------------------------------------------------------


    abstract static class AbstractTokenBuilder<T extends AbstractToken, B extends AbstractTokenBuilder<T, ?>> {

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

        /**
         * @param start Inclusive array index
         */
        public B start(final int start) {
            this.start = start;
            return self();
        }

        /**
         * @param end Inclusive array index
         */
        public B end(final int end) {
            this.end = end;
            return self();
        }

        abstract B self();

        public abstract T build();
    }
}
