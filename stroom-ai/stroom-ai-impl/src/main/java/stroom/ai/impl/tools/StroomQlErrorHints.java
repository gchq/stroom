/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.Tokeniser;

import java.util.List;
import java.util.Set;

/// Hints for a StroomQL query Stroom refused, for SQL habits StroomQL doesn't share: group by, sort by or limit
/// after select (StroomQL puts them before it), and order by (StroomQL's is sort by). Stroom's parser names only
/// the token it stopped at, e.g. "Unexpected token GROUP after SELECT" or "Syntax exception, expected AS" (seen
/// with Haiku in eval cases 71 to 73). The query is read with Stroom's own tokeniser, so text in quotes never
/// counts, and only the query's own clauses do, not a bracketed query's.
final class StroomQlErrorHints {

    static final String CLAUSE_ORDER = " In StroomQL, group by, sort by and limit go before select.";
    static final String ORDER_BY = " StroomQL uses sort by, not order by.";

    private static final Set<TokenType> BEFORE_SELECT = Set.of(TokenType.GROUP, TokenType.SORT, TokenType.LIMIT);

    private StroomQlErrorHints() {
        // Static methods only.
    }

    /// @param query A query Stroom refused.
    /// @return The hints that apply to it, each starting with a space, or empty.
    static String of(final String query) {
        if (query == null) {
            return "";
        }
        final List<Token> tokens;
        try {
            tokens = Tokeniser.parse(query);
        } catch (final RuntimeException e) {
            return "";
        }
        boolean afterSelect = false;
        boolean clauseAfterSelect = false;
        boolean orderBy = false;
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final TokenType type = tokens.get(i).getTokenType();
            if (TokenType.OPEN_BRACKET.equals(type)) {
                depth++;
            } else if (TokenType.CLOSE_BRACKET.equals(type)) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                if (TokenType.SELECT.equals(type)) {
                    afterSelect = true;
                } else if (afterSelect && BEFORE_SELECT.contains(type)) {
                    clauseAfterSelect = true;
                }
                if (isOrderBy(tokens, i)) {
                    orderBy = true;
                }
            }
        }
        return (clauseAfterSelect
                ? CLAUSE_ORDER
                : "") + (orderBy
                ? ORDER_BY
                : "");
    }

    /// @param error A failed query's error.
    /// @param query The query.
    /// @return The error with the hints that apply to the query after it, the error ended with a full stop first
    /// when there are any.
    static String appendTo(final String error, final String query) {
        final String hints = of(query);
        if (hints.isEmpty()) {
            return error;
        }
        final String stripped = error.strip();
        return (stripped.endsWith(".")
                ? stripped
                : stripped + ".") + hints;
    }

    // An unquoted 'order' followed by 'by', with only whitespace between.
    private static boolean isOrderBy(final List<Token> tokens, final int index) {
        final Token token = tokens.get(index);
        if (!TokenType.STRING.equals(token.getTokenType()) || !"order".equalsIgnoreCase(token.getText())) {
            return false;
        }
        for (int i = index + 1; i < tokens.size(); i++) {
            final Token next = tokens.get(i);
            if (!TokenType.WHITESPACE.equals(next.getTokenType())) {
                return "by".equalsIgnoreCase(next.getText().strip());
            }
        }
        return false;
    }
}
