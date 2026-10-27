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

package stroom.query.language.token;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.util.shared.NullSafe;

import java.util.List;

/// Formats StroomQL query text.
///
/// The formatter only ever changes whitespace between tokens produced by [Tokeniser], so the
/// content of every token (e.g. `${param}` parameters, quoted strings, comments and
/// functions) is left exactly as written. The rules applied are:
///
/// * Each clause keyword (e.g. `from`, `where`, `eval`, `select`) starts a new line.
/// * A line break within a clause is kept, but blank lines are removed and the
///   continuation line is indented by [#INDENT].
/// * Any other run of whitespace between tokens is reduced to a single space.
/// * Whitespace at the start and end of the query is removed.
/// * A line comment is always followed by a line break so that it can never comment out
///   the token that follows it.
public final class StroomQLFormatter {

    /// The indent applied to continuation lines within a clause.
    public static final String INDENT = "    ";

    private static final char NEW_LINE = '\n';

    private StroomQLFormatter() {
        // Static utility class.
    }

    /// Formats the supplied StroomQL query text.
    ///
    /// @param query The StroomQL query text to format. May be `null` or blank.
    /// @return The formatted query text, or `query` unchanged if it is `null` or blank.
    public static String format(final String query) {
        if (NullSafe.isBlankString(query)) {
            return query;
        }

        final List<Token> tokens = Tokeniser.parse(query);
        final StringBuilder sb = new StringBuilder(query.length());
        Separator separator = Separator.NONE;

        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            final TokenType tokenType = token.getTokenType();

            if (TokenType.WHITESPACE.equals(tokenType)) {
                separator = separator.combine(containsNewLine(token)
                        ? Separator.NEW_LINE
                        : Separator.SPACE);
            } else {
                if (!sb.isEmpty()) {
                    appendSeparator(sb, separator, isClauseStart(tokens, i));
                }
                sb.append(token.getText());

                // A line comment runs to the end of the line so the next token must go on a new line.
                separator = TokenType.COMMENT.equals(tokenType)
                        ? Separator.NEW_LINE
                        : Separator.NONE;
            }
        }
        return sb.toString();
    }

    private static void appendSeparator(final StringBuilder sb,
                                        final Separator separator,
                                        final boolean clauseStart) {
        if (clauseStart) {
            sb.append(NEW_LINE);
        } else if (Separator.NEW_LINE.equals(separator)) {
            sb.append(NEW_LINE).append(INDENT);
        } else if (Separator.SPACE.equals(separator)) {
            sb.append(' ');
        }
    }

    /// @return True if the token at `index` should start a new unindented line. That is the
    /// case for a clause keyword, and for a comment on its own line that precedes a clause keyword
    /// (or the end of the query), so that such comments line up with the clause they describe.
    private static boolean isClauseStart(final List<Token> tokens, final int index) {
        final Token token = tokens.get(index);
        final TokenType tokenType = token.getTokenType();
        if (TokenType.ALL_KEYWORDS.contains(tokenType)) {
            return true;
        } else if (isComment(tokenType) && isOnOwnLine(tokens, index)) {
            final TokenType nextType = findNextCodeTokenType(tokens, index + 1);
            return nextType == null || TokenType.ALL_KEYWORDS.contains(nextType);
        } else {
            return false;
        }
    }

    private static boolean isOnOwnLine(final List<Token> tokens, final int index) {
        if (index == 0) {
            return true;
        }
        final Token previous = tokens.get(index - 1);
        return TokenType.COMMENT.equals(previous.getTokenType())
               || (TokenType.WHITESPACE.equals(previous.getTokenType()) && containsNewLine(previous));
    }

    /// @return The type of the first token at or after `fromIndex` that is neither whitespace
    /// nor a comment, or `null` if there is no such token.
    private static TokenType findNextCodeTokenType(final List<Token> tokens, final int fromIndex) {
        for (int i = fromIndex; i < tokens.size(); i++) {
            final TokenType tokenType = tokens.get(i).getTokenType();
            if (!TokenType.WHITESPACE.equals(tokenType) && !isComment(tokenType)) {
                return tokenType;
            }
        }
        return null;
    }

    private static boolean isComment(final TokenType tokenType) {
        return TokenType.COMMENT.equals(tokenType) || TokenType.BLOCK_COMMENT.equals(tokenType);
    }

    private static boolean containsNewLine(final Token token) {
        return token.getText().indexOf(NEW_LINE) >= 0;
    }


    // --------------------------------------------------------------------------------


    /// The whitespace to output between two non-whitespace tokens.
    private enum Separator {
        NONE,
        SPACE,
        NEW_LINE;

        private Separator combine(final Separator other) {
            return this.ordinal() >= other.ordinal()
                    ? this
                    : other;
        }
    }
}
