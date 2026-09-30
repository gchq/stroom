/*
 * Copyright 2025 Crown Copyright
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

package stroom.query.language.token;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InTermsUtil {
    public static List<String> getInTerms(final String value) {
        final List<String> terms = new ArrayList<>();
        if (NullSafe.isNonBlankString(value)) {
            final char[] chars = value.toCharArray();
            final Token unknown = new Token(TokenType.UNKNOWN, chars, 0, chars.length - 1);
            List<Token> tokens = Collections.singletonList(unknown);

            // Tag quoted strings.
            tokens = Tokeniser.extractQuotedTokens(tokens);
            // Tag whitespace.
            tokens = Tokeniser.split("\\s+", 0, TokenType.WHITESPACE, tokens);
            // Tag commas.
            tokens = Tokeniser.split(",", 0, TokenType.COMMA, tokens);

            // Add all non delimiter tokens back together.
            final StringBuilder sb = new StringBuilder();
            for (final Token token : tokens) {
                if (TokenType.COMMA.equals(token.getTokenType()) || TokenType.WHITESPACE.equals(token.getTokenType())) {
                    if (!sb.isEmpty()) {
                        terms.add(sb.toString());
                        sb.setLength(0);
                    }
                } else {
                    sb.append(token.getUnescapedText());
                }
            }
            if (!sb.isEmpty()) {
                terms.add(sb.toString());
            }
        }
        return terms;
    }
}
