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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// A search pattern given by the model. Searches are literal unless a regular expression is asked for; a regular
/// expression is limited in length and is always matched through a [DeadlineCharSequence], so a pattern that
/// backtracks badly is stopped at the tool call's time limit.
public final class SafePattern {

    static final int MAX_PATTERN_LENGTH = 200;

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

    /// @param line A line of text.
    /// @return True if the line contains a match.
    /// @throws ToolTimeoutException if matching runs past the time limit.
    public boolean find(final String line) {
        if (pattern == null) {
            ToolDeadline.check();
            return (ignoreCase
                    ? line.toLowerCase(Locale.ROOT)
                    : line).contains(literal);
        }
        final Matcher matcher = pattern.matcher(new DeadlineCharSequence(line));
        return matcher.find();
    }
}
