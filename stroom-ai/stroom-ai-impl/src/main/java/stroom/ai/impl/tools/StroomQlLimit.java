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

/// Caps the rows a StroomQL query the agent runs may return, so none of its searches is unbounded: a query with no
/// `limit` is given one, and a larger limit is lowered. Without sorting or grouping, Stroom stops a search as soon
/// as it has that many rows. The query is read with Stroom's own tokeniser, so text in strings and comments is
/// never taken for a keyword.
final class StroomQlLimit {

    private StroomQlLimit() {
        // Static methods only.
    }

    /// @param query   A StroomQL query, which Stroom will check itself when it runs it.
    /// @param maxRows The most rows it may return.
    /// @return The query with its limit capped at `maxRows`, and what was changed. The query as given, unchanged,
    /// if it can't be read, as Stroom will then refuse it with its own error.
    static Limited apply(final String query, final long maxRows) {
        final List<Token> tokens;
        try {
            tokens = Tokeniser.parse(query);
        } catch (final RuntimeException e) {
            return new Limited(query, Change.NONE);
        }
        int limitIndex = -1;
        int selectIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            final TokenType type = tokens.get(i).getTokenType();
            if (TokenType.LIMIT.equals(type) && limitIndex == -1) {
                limitIndex = i;
            } else if (TokenType.SELECT.equals(type) && selectIndex == -1) {
                selectIndex = i;
            }
        }
        if (limitIndex == -1) {
            // A limit goes before select (and anything after it), or at the end of a query without one.
            final String added = "limit " + maxRows;
            if (selectIndex == -1) {
                return new Limited(query.stripTrailing() + "\n" + added, Change.ADDED);
            }
            final int at = tokens.get(selectIndex).getStart();
            return new Limited(query.substring(0, at) + added + "\n" + query.substring(at), Change.ADDED);
        }

        // Lower each number given to the limit (one for each level of grouping) that is over the most.
        // Last first, so replacing one doesn't move those before it.
        final StringBuilder sb = new StringBuilder(query);
        boolean lowered = false;
        for (int i = firstKeywordAfter(tokens, limitIndex) - 1; i > limitIndex; i--) {
            final Token token = tokens.get(i);
            if (TokenType.NUMBER.equals(token.getTokenType()) && isOver(token.getText(), maxRows)) {
                sb.replace(token.getStart(), token.getEnd() + 1, String.valueOf(maxRows));
                lowered = true;
            }
        }
        return new Limited(sb.toString(), lowered
                ? Change.LOWERED
                : Change.NONE);
    }

    // The index of the first keyword after the limit (e.g. select), or the end of the query.
    private static int firstKeywordAfter(final List<Token> tokens, final int limitIndex) {
        for (int i = limitIndex + 1; i < tokens.size(); i++) {
            if (TokenType.isKeyword(tokens.get(i).getTokenType())) {
                return i;
            }
        }
        return tokens.size();
    }

    private static boolean isOver(final String number, final long maxRows) {
        try {
            return Long.parseLong(number.strip()) > maxRows;
        } catch (final NumberFormatException e) {
            // Too large to read, so certainly over.
            return true;
        }
    }


    // --------------------------------------------------------------------------------


    /// What [#apply(String, long)] changed.
    enum Change {
        /// The query's limit was within the most.
        NONE,
        /// The query had no limit, so one was added.
        ADDED,
        /// The query's limit was over the most, so it was lowered.
        LOWERED
    }


    // --------------------------------------------------------------------------------


    /// @param query  The query to run.
    /// @param change What was changed.
    record Limited(String query, Change change) {

    }
}
