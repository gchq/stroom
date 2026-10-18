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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Lays out a StroomQL query the agent wrote on one line, as people write them: each clause on its own line, each
/// top-level `and`/`or` of a where, filter or having on an indented line of its own, and a long select with one
/// column a line. Only the whitespace between tokens changes, so the query means exactly what it did. The query is
/// read with Stroom's own tokeniser, so text in strings is never taken for a keyword. A query that already has line
/// breaks or comments is left as its author laid it out (moving a line comment's line break would comment out what
/// follows it).
final class StroomQlFormatter {

    /// The indent of a continued line.
    static final String INDENT = "    ";
    /// A select clause longer than this has one column a line.
    static final int MAX_SELECT_CHARS = 80;

    private static final Set<TokenType> CONDITION_CLAUSES = Set.of(TokenType.WHERE, TokenType.FILTER,
            TokenType.HAVING);

    private StroomQlFormatter() {
        // Static methods only.
    }

    /// @param query A StroomQL query.
    /// @return It laid out over several lines, or as it was if it already has line breaks or comments, or can't be
    /// read (Stroom will then refuse it with its own error).
    static String format(final String query) {
        if (query == null || query.isBlank() || query.strip().contains("\n")) {
            return query;
        }
        final List<Token> tokens;
        try {
            tokens = Tokeniser.parse(query.strip());
        } catch (final RuntimeException e) {
            return query;
        }
        if (tokens.stream().anyMatch(token -> TokenType.COMMENT.equals(token.getTokenType())
                                              || TokenType.BLOCK_COMMENT.equals(token.getTokenType()))) {
            return query;
        }

        // Each token with what goes before it: nothing if it touched the token before (e.g. "count()"), else a
        // space, a line break or an indented line break.
        final List<String> texts = new ArrayList<>();
        final List<String> before = new ArrayList<>();
        final List<TokenType> clauses = new ArrayList<>();
        boolean spaced = false;
        int depth = 0;
        TokenType clause = null;
        // The and of a between (between 1 and 10) isn't a condition of its own.
        boolean inBetween = false;
        for (final Token token : tokens) {
            final TokenType type = token.getTokenType();
            if (TokenType.WHITESPACE.equals(type)) {
                spaced = true;
                continue;
            }
            String separator = spaced
                    ? " "
                    : "";
            if (depth == 0 && TokenType.isKeyword(type)) {
                clause = type;
                separator = "\n";
            } else if (TokenType.BETWEEN.equals(type)) {
                inBetween = true;
            } else if (inBetween && (TokenType.AND.equals(type) || TokenType.BETWEEN_AND.equals(type))) {
                inBetween = false;
            } else if (depth == 0 && CONDITION_CLAUSES.contains(clause)
                       && (TokenType.AND.equals(type) || TokenType.OR.equals(type))) {
                separator = "\n" + INDENT;
            }
            if (TokenType.OPEN_BRACKET.equals(type)) {
                depth++;
            } else if (TokenType.CLOSE_BRACKET.equals(type)) {
                depth = Math.max(0, depth - 1);
            }
            texts.add(token.getText());
            before.add(texts.size() == 1
                    ? ""
                    : separator);
            clauses.add(clause);
            spaced = false;
        }
        splitLongSelect(texts, before, clauses);

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.size(); i++) {
            sb.append(before.get(i)).append(texts.get(i));
        }
        return sb.toString();
    }

    // Puts each column of a long select on its own indented line, after the select keyword.
    private static void splitLongSelect(final List<String> texts,
                                        final List<String> before,
                                        final List<TokenType> clauses) {
        int start = -1;
        int length = 0;
        for (int i = 0; i < texts.size(); i++) {
            if (TokenType.SELECT.equals(clauses.get(i))) {
                if (start == -1) {
                    start = i;
                }
                length += before.get(i).length() + texts.get(i).length();
            }
        }
        if (start == -1 || length <= MAX_SELECT_CHARS) {
            return;
        }
        int depth = 0;
        boolean first = true;
        for (int i = start + 1; i < texts.size() && TokenType.SELECT.equals(clauses.get(i)); i++) {
            final String text = texts.get(i);
            if (first) {
                before.set(i, "\n" + INDENT);
                first = false;
            } else if (depth == 0 && ",".equals(texts.get(i - 1))) {
                before.set(i, "\n" + INDENT);
            }
            if ("(".equals(text)) {
                depth++;
            } else if (")".equals(text)) {
                depth = Math.max(0, depth - 1);
            }
        }
    }
}
