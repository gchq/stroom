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
