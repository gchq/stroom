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

public class Token extends AbstractToken {

    /**
     * @param start Inclusive array index
     * @param end   Inclusive array index
     */
    public Token(final TokenType tokenType, final char[] chars, final int start, final int end) {
        super(tokenType, chars, start, end);
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractTokenBuilder<Token, Builder> {

        public Builder() {
        }

        public Builder(final Token token) {
            this.tokenType = token.tokenType;
            this.chars = token.chars;
            this.start = token.start;
            this.end = token.end;
        }

        @Override
        Builder self() {
            return this;
        }

        @Override
        public Token build() {
            Objects.requireNonNull(tokenType, "Null token type");
            Objects.requireNonNull(chars, "Null chars");
            if (start == -1 || end == -1) {
                throw new IndexOutOfBoundsException();
            }
            return new Token(tokenType, chars, start, end);
        }
    }
}
