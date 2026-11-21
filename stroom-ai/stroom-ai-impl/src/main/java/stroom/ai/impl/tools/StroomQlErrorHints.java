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

/// Hints for a StroomQL query Stroom refused, for SQL habits StroomQL doesn't share: a clause after select (StroomQL
/// puts every clause but show before it; Sonnet wrote where after select in eval case 72), order by (StroomQL's is
/// sort by), a join line after another clause (join lines go straight after from), and between with one value
/// (Sonnet wrote `between "a,b"` in cases 72 and 73). Stroom's parser names only
/// the token it stopped at, e.g. "Unexpected token GROUP after SELECT", "Syntax exception, expected AS" or
/// "Unexpected token JOIN after WHERE" (seen with Haiku in eval cases 71 to 73, and Sonnet in 71). The query is
/// read with Stroom's own tokeniser, so text in quotes never counts, and only the query's own clauses do, not a
/// bracketed query's.
final class StroomQlErrorHints {

    static final String CLAUSE_ORDER = " In StroomQL, every clause but show goes before select: from, join lines, "
                                       + "where, eval, window, filter, sort by, group by, having, limit, select, show.";
    static final String ORDER_BY = " StroomQL uses sort by, not order by.";
    static final String JOIN_AFTER_FROM = " Join lines go straight after from.";
    static final String BETWEEN_AND = " between takes two values joined by and, e.g. EventTime between "
                                      + "\"2026-01-05T00:00:00Z\" and \"2026-01-12T00:00:00Z\".";

    // The clauses that go before select (join lines have a hint of their own).
    private static final Set<TokenType> BEFORE_SELECT = Set.of(TokenType.WHERE, TokenType.EVAL, TokenType.WINDOW,
            TokenType.FILTER, TokenType.SORT, TokenType.GROUP, TokenType.HAVING, TokenType.LIMIT);
    // The clauses a join line may follow: the first source (and its sql), and another join line with its keys.
    private static final Set<TokenType> BEFORE_JOIN = Set.of(TokenType.FROM, TokenType.SQL, TokenType.JOIN,
            TokenType.ON);
    // Keywords inside a clause (a condition's, or keys' and), not clauses of their own.
    private static final Set<TokenType> INSIDE_A_CLAUSE = Set.of(TokenType.AND, TokenType.OR, TokenType.NOT);

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
        boolean joinLate = false;
        // A between whose and hasn't come yet, and whether one never had it before the next clause.
        boolean betweenOpen = false;
        boolean betweenWithoutAnd = false;
        TokenType lastClause = null;
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
                if (TokenType.JOIN.equals(type) && lastClause != null && !BEFORE_JOIN.contains(lastClause)) {
                    joinLate = true;
                }
                if (TokenType.isKeyword(type) && !INSIDE_A_CLAUSE.contains(type)) {
                    lastClause = type;
                }
                if (TokenType.BETWEEN.equals(type)) {
                    betweenOpen = true;
                } else if (TokenType.AND.equals(type) || TokenType.BETWEEN_AND.equals(type)) {
                    betweenOpen = false;
                } else if (betweenOpen && TokenType.isKeyword(type) && !INSIDE_A_CLAUSE.contains(type)) {
                    betweenWithoutAnd = true;
                    betweenOpen = false;
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
                : "") + (joinLate
                ? JOIN_AFTER_FROM
                : "") + (betweenWithoutAnd || betweenOpen
                ? BETWEEN_AND
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
