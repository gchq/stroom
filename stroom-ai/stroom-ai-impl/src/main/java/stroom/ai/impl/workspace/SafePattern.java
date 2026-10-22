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

package stroom.ai.impl.workspace;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// A search pattern given by the model. Searches are literal unless a regular expression is asked for; a regular
/// expression is limited in length and is always matched through a [DeadlineCharSequence], so a pattern that
/// backtracks badly is stopped at the tool call's time limit.
///
/// The regular expressions the model writes for reading records (a splitter's pattern, a record start, an
/// extraction's regex) are compiled with [#compile(String, int)], matched with [#matcher(Pattern, CharSequence)] and
/// run within [#limited(String, Supplier)], so one that backtracks badly is stopped at [#REGEX_TIME_LIMIT] even
/// where the tool call has no time limit of its own.
public final class SafePattern {

    /// The longest a pattern (or the text to find) may be, in characters.
    public static final int MAX_PATTERN_LENGTH = 200;
    /// The longest a regular expression the model writes for reading records may be, in characters.
    public static final int MAX_REGEX_LENGTH = 2_000;
    /// The longest matching the model's regular expressions may take, for one piece of work (a dry run, a check).
    public static final Duration REGEX_TIME_LIMIT = Duration.ofSeconds(10);
    // What a regular expression usually has that text to find literally seldom does: an alternation, a repeated
    // wildcard, a character class or escape, or an anchor at either end.
    private static final Pattern REGEX_LIKE = Pattern.compile(
            "\\||\\.[*+?]|\\\\[dDsSwWb]|\\[[^\\]]+\\]|^\\^|\\$$");

    private final String literal;
    private final boolean ignoreCase;
    private final Pattern pattern;

    private SafePattern(final String literal, final boolean ignoreCase, final Pattern pattern) {
        this.literal = literal;
        this.ignoreCase = ignoreCase;
        this.pattern = pattern;
    }

    /// @param text       The text or pattern to find.
    /// @param regex      True if it is a regular expression, false to match it literally.
    /// @param ignoreCase True to ignore case.
    /// @return The pattern.
    /// @throws WorkspaceException if it is too long or not a valid regular expression.
    public static SafePattern of(final String text, final boolean regex, final boolean ignoreCase) {
        if (text == null || text.isEmpty()) {
            throw new WorkspaceException("Give the text to find.");
        }
        if (text.length() > MAX_PATTERN_LENGTH) {
            throw new WorkspaceException("The text to find may be at most " + MAX_PATTERN_LENGTH + " characters.");
        }
        if (!regex) {
            return new SafePattern(ignoreCase
                    ? text.toLowerCase(Locale.ROOT)
                    : text, ignoreCase, null);
        }
        try {
            final int flags = ignoreCase
                    ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                    : 0;
            return new SafePattern(null, ignoreCase, Pattern.compile(text, flags));
        } catch (final PatternSyntaxException e) {
            throw new WorkspaceException("'" + text + "' is not a valid regular expression: "
                                         + e.getDescription() + ".");
        }
    }

    /// Compiles a regular expression the model wrote for reading records.
    ///
    /// @param regex The regular expression.
    /// @param flags Its [Pattern] flags.
    /// @return It compiled.
    /// @throws PatternSyntaxException if it is not valid, or is longer than [#MAX_REGEX_LENGTH] (said as its
    ///                                description, so a caller that says why a pattern is invalid says so).
    public static Pattern compile(final String regex, final int flags) {
        if (regex != null && regex.length() > MAX_REGEX_LENGTH) {
            throw new PatternSyntaxException("it is " + regex.length() + " characters long, and may be at most "
                                             + MAX_REGEX_LENGTH, regex.substring(0, 100) + "...", -1);
        }
        return Pattern.compile(regex, flags);
    }

    /// @param pattern A pattern.
    /// @param text    The text to match.
    /// @return A matcher of the text that checks the time limit as it reads (see [DeadlineCharSequence]).
    public static Matcher matcher(final Pattern pattern, final CharSequence text) {
        return pattern.matcher(new DeadlineCharSequence(text));
    }

    /// Does work that matches the model's regular expressions within [#REGEX_TIME_LIMIT] (or the time limit
    /// already in force, if it is sooner).
    ///
    /// @param pattern The pattern (or patterns) matched, for the message if it takes too long, or null.
    /// @param work    The work, which matches through [#matcher(Pattern, CharSequence)] or a
    ///                [DeadlineCharSequence].
    /// @param <T>     What it gives.
    /// @return What it gives.
    /// @throws PatternTimeoutException if it runs past [#REGEX_TIME_LIMIT].
    /// @throws ToolTimeoutException    if the time limit already in force, being sooner, runs out, or the thread is
    ///                                 interrupted.
    public static <T> T limited(final String pattern, final Supplier<T> work) {
        // Whether this limit is the one that would stop the work, rather than the call's own, sooner.
        final boolean ownLimit = ToolDeadline.remaining()
                .map(left -> left.compareTo(REGEX_TIME_LIMIT) >= 0)
                .orElse(true);
        try {
            return ToolDeadline.run(REGEX_TIME_LIMIT, work);
        } catch (final ToolTimeoutException e) {
            if (!ownLimit || Thread.currentThread().isInterrupted()) {
                throw e;
            }
            throw new PatternTimeoutException(pattern);
        }
    }

    /// @return True if this is a literal search for text that looks like a regular expression (e.g.
    /// {@code error|warn}), which may well have been meant as one.
    public boolean isLiteralThatLooksLikeRegex() {
        return pattern == null && REGEX_LIKE.matcher(literal).find();
    }

    /// @param line A line of text.
    /// @return True if the line contains a match.
    /// @throws ToolTimeoutException if matching runs past the time limit.
    public boolean find(final String line) {
        return indexIn(line) >= 0;
    }

    /// @param text Some text.
    /// @return Where the first match starts in the text, or -1 if there is none.
    /// @throws ToolTimeoutException if matching runs past the time limit.
    public int indexIn(final String text) {
        if (pattern == null) {
            ToolDeadline.check();
            // Lower-casing can change a string's length (rarely), which would give the wrong place; the match is
            // then said to start at the beginning.
            final String haystack = ignoreCase
                    ? text.toLowerCase(Locale.ROOT)
                    : text;
            final int index = haystack.indexOf(literal);
            return index >= 0 && haystack.length() != text.length()
                    ? 0
                    : index;
        }
        final Matcher matcher = pattern.matcher(new DeadlineCharSequence(text));
        return matcher.find()
                ? matcher.start()
                : -1;
    }
}
