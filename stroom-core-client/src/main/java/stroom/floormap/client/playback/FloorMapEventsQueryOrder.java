/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.playback;

import stroom.query.api.token.BasicTokeniser;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;

import java.util.List;

/**
 * Answers whether a floor map's events query lets the Map tab trust the order its rows arrive in.
 *
 * <p>Two independent things can break that trust, so there are two questions:
 * {@link #hasSortClause} and {@link #bindsEntityIdToStoreKey}. Either one failing sends the caller
 * to the weaker reduction; both are conservative, answering "cannot trust" when unsure.</p>
 *
 * <h3>Why anyone cares</h3>
 * <p>The Map tab reduces each read to one row per entity with
 * {@code FloorMapQueryPresenter.latestPerEntity}. Given no time column that reduction takes the
 * <b>last row</b> per entity, which is correct because the rows arrive oldest-first: Plan B iterates
 * one LMDB cursor whose key is {@code <entity><big-endian time>}, an ungrouped search is keyed by a
 * monotonic insertion id, and neither the write queue nor the result creator reorders.</p>
 *
 * <p>A {@code sort} clause breaks that chain — the result store takes its sorted path instead, so
 * "the last row" becomes the sort's last. {@code sort by EffectiveTime desc} would make the
 * reduction pick each entity's <em>oldest</em> event.</p>
 *
 * <p>So when a sort is present the caller compares effective times instead of trusting arrival
 * order. That is a lesser hazard rather than none — the time column arrives rendered to the viewing
 * user's date-time preference, so a pattern that is not lexicographically ordered can still pick
 * wrongly — but it is bounded, and it only affects a query someone has deliberately customised.</p>
 *
 * <h3>Why this detects rather than rewrites</h3>
 * <p>Removing the clause would be the ideal fix, and was the plan. It means finding where the clause
 * <em>ends</em>, which means recognising every other keyword that could follow it, and getting that
 * wrong corrupts a query that works today. Detection needs only the one keyword, cannot damage
 * anything, and buys most of the benefit — so it is what this does.</p>
 *
 * <h3>Why the scan is hand-rolled</h3>
 * <p>{@link BasicTokeniser} is the only tokeniser available to GWT-compiled code, and its keyword
 * tagging is commented out; the full {@code Tokeniser} that tags keywords lives server-side and uses
 * {@code java.util.regex}, which GWT cannot compile. What {@link BasicTokeniser} does give is the
 * part that matters most — quoted strings, comments and parameters are tagged, so the word scan below
 * runs only over the spans that could hold real syntax. That is what a substring search would get
 * wrong.</p>
 */
public final class FloorMapEventsQueryOrder {

    private static final String SORT = "sort";

    /** The Plan B temporal-state key field, and the only expression whose ordering is known. */
    private static final String KEY_FIELD = "Key";

    private FloorMapEventsQueryOrder() {
        // Static only.
    }

    /**
     * Whether {@code query} carries a {@code sort} keyword that the query engine would honour.
     *
     * <p>Mirrors the context rule the server-side tokeniser applies — a keyword must start the
     * query, follow whitespace that is not preceded by {@code =}, or follow {@code )}; and must be
     * followed by whitespace, {@code (}, or the end. The {@code =} exclusion is why
     * {@code eval x = sort} is a field reference rather than a clause.</p>
     *
     * @param query the query text; {@code null} or blank counts as no sort
     * @return {@code true} if arrival order cannot be trusted for this query
     */
    public static boolean hasSortClause(final String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        final List<Token> tokens;
        try {
            tokens = BasicTokeniser.parse(query);
        } catch (final RuntimeException e) {
            // Defence only: the tokeniser throws for null or blank input, which is handled above,
            // and tolerates unterminated quotes and comments. If that ever changes, assume the
            // worst — such a query fails server-side anyway, so the only cost is the caller taking
            // the weaker reduction.
            return true;
        }

        for (final Token token : tokens) {
            // Only spans that could hold syntax. Quoted strings, comments and params are already
            // tagged as themselves, and a `sort` inside one of those is not a keyword.
            if (TokenType.UNKNOWN.equals(token.getTokenType())
                && containsSortKeyword(token.getText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code query} aliases the store's {@code Key} field directly to
     * {@code entityIdColumn}.
     *
     * <p>The other precondition for trusting arrival order, and the one that cannot be proved.
     * Plan B's rows arrive grouped by key prefix — all of one prefix in time order, then all of the
     * next — so "the last row for an entity" is only that entity's latest if the entity <em>is</em>
     * the key. An entity id derived from the value instead, say {@code jq(Value, '.person')},
     * scatters one entity's history across many prefixes, and the last row to arrive is merely the
     * last prefix's latest.</p>
     *
     * <p>No client API can evaluate an arbitrary select expression, so this is deliberately a
     * textual check for the one shape that is known safe — the shape
     * {@code FloorMapEventsQuery.defaultQuery()} generates. It errs towards {@code false}: a query
     * that is safe but written differently loses the stronger ordering and nothing else, whereas
     * erring the other way silently draws entities at stale positions.</p>
     *
     * @param query           the query text
     * @param entityIdColumn  the column the document reads entity ids from
     * @return {@code true} only when the binding is unmistakable
     */
    public static boolean bindsEntityIdToStoreKey(final String query, final String entityIdColumn) {
        if (query == null || entityIdColumn == null || entityIdColumn.isBlank()) {
            return false;
        }

        final List<Token> tokens;
        try {
            tokens = BasicTokeniser.parse(query);
        } catch (final RuntimeException e) {
            return false;
        }

        // The binding spans two tokens: an UNKNOWN span ending in `Key as`, then the quoted alias.
        // Scanning tokens rather than raw text is what stops `jq(Value, '.key') as "Entity ID"`
        // matching on the letters inside the string.
        //
        // Of the four conditions below, two discriminate and two are structural. The whole-word
        // test inside endsWithKeyAs and the alias comparison both reject real queries, and are
        // tested. The UNKNOWN and isQuoted tests reject nothing that this tokeniser can produce:
        // no token type it emits both ends in ` as` and is followed directly by a quoted string —
        // strings and params end in their closing character, a block comment ends in `*/`, and a
        // line comment is terminated by the newline that starts the next UNKNOWN span. They are
        // kept because they state the shape being matched. Dropping them would leave correctness
        // resting on that argument about which token sequences are reachable, which is a far more
        // fragile thing to depend on than two lines of check. No test covers them because no input
        // can falsify them.
        for (int i = 0; i < tokens.size() - 1; i++) {
            if (TokenType.UNKNOWN.equals(tokens.get(i).getTokenType())
                && endsWithKeyAs(tokens.get(i).getText())
                && isQuoted(tokens.get(i + 1))
                && entityIdColumn.equals(unquote(tokens.get(i + 1).getText()))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a span ends with the bare field {@code Key} followed by {@code as}. */
    private static boolean endsWithKeyAs(final String text) {
        if (text == null) {
            return false;
        }
        final String trimmed = text.stripTrailing();
        if (!trimmed.toLowerCase().endsWith(" as")) {
            return false;
        }
        // Everything before the `as`, which must end in the field name as a whole word.
        final String beforeAs = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
        if (!beforeAs.endsWith(KEY_FIELD)) {
            return false;
        }
        final int at = beforeAs.length() - KEY_FIELD.length();
        // A bare field, not the tail of `.key` or `EntityKey`. Only a separator may precede it.
        return at == 0 || isSpace(beforeAs.charAt(at - 1)) || beforeAs.charAt(at - 1) == ',';
    }

    private static boolean isQuoted(final Token token) {
        return TokenType.DOUBLE_QUOTED_STRING.equals(token.getTokenType())
               || TokenType.SINGLE_QUOTED_STRING.equals(token.getTokenType());
    }

    private static String unquote(final String text) {
        return text != null && text.length() >= 2
                ? text.substring(1, text.length() - 1)
                : text;
    }

    private static boolean containsSortKeyword(final String text) {
        if (text == null) {
            return false;
        }
        final String lower = text.toLowerCase();
        int from = 0;
        while (true) {
            final int at = lower.indexOf(SORT, from);
            if (at < 0) {
                return false;
            }
            if (isKeywordAt(lower, at)) {
                return true;
            }
            from = at + SORT.length();
        }
    }

    /** The server-side context rule: {@code (^\s*|[^=]\s+|\))(sort)(\s|\(|$)}. */
    private static boolean isKeywordAt(final String lower, final int at) {
        return precededAsKeyword(lower, at) && followedAsKeyword(lower, at + SORT.length());
    }

    private static boolean precededAsKeyword(final String lower, final int at) {
        int i = at - 1;
        if (i < 0) {
            return true;
        }
        if (lower.charAt(i) == ')') {
            return true;
        }
        if (!isSpace(lower.charAt(i))) {
            // Part of a longer word, e.g. `resort`.
            return false;
        }
        while (i >= 0 && isSpace(lower.charAt(i))) {
            i--;
        }
        // Start of the span, or anything but `=`. An `=` means this is a value, not a clause —
        // `eval x = sort` refers to a field called sort.
        return i < 0 || lower.charAt(i) != '=';
    }

    private static boolean followedAsKeyword(final String lower, final int after) {
        if (after >= lower.length()) {
            return true;
        }
        final char c = lower.charAt(after);
        return isSpace(c) || c == '(';
    }

    private static boolean isSpace(final char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }
}
