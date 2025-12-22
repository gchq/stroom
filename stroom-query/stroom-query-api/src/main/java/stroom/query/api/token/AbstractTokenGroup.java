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

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTokenGroup extends AbstractToken {

    private final List<AbstractToken> children;

    public AbstractTokenGroup(final TokenType tokenType,
                              final char[] chars,
                              final int start,
                              final int end,
                              final List<AbstractToken> children) {
        super(tokenType, chars, start, end);
        this.children = children;
    }

    public List<AbstractToken> getChildren() {
        return children;
    }

    public void append(final StringBuilder sb, final boolean indent, final int depth) {
        appendIndent(indent, sb, depth);
        sb.append("<");
        sb.append(tokenType);
        appendAttributes(sb);
        sb.append(">");
        appendNewLine(indent, sb);
        appendChildren(sb, indent, depth + 1);
        appendIndent(indent, sb, depth);
        appendCloseType(sb);
        appendNewLine(indent, sb);
    }

    void appendAttributes(final StringBuilder sb) {
    }
//
//    void append(final StringBuilder sb, final boolean indent, final int depth) {
//        appendIndent(indent, sb, depth);
//        appendOpenType(sb);
//        appendNewLine(indent, sb);
//        appendChildren(sb, indent, depth + 1);
//        appendIndent(indent, sb, depth);
//        appendCloseType(sb);
//        appendNewLine(indent, sb);
//    }

    void appendChildren(final StringBuilder sb, final boolean indent, final int depth) {
        for (final AbstractToken child : children) {
            child.append(sb, indent, depth);
        }
    }

    public abstract static class AbstractTokenGroupBuilder
            <T extends AbstractTokenGroup, B extends AbstractTokenGroupBuilder<T, B>>
            extends AbstractTokenBuilder<T, B> {

        final List<AbstractToken> children = new ArrayList<>();

        public B addAll(final List<AbstractToken> tokens) {
            this.children.addAll(tokens);
            return self();
        }

        public B add(final AbstractToken token) {
            this.children.add(token);
            return self();
        }

        public boolean isEmpty() {
            return children.isEmpty();
        }
    }
}
