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

import stroom.util.date.DateUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Gives Stroom's `formatDate` and `parseDate` calls the call's time zone where they don't name one, as the query
/// tools run every other date in it: Stroom's own work in UTC unless given a zone as their third argument, in
/// StroomQL, dashboards and the table tool's expressions alike. So, in Australia/Canberra,
/// `formatDate(EventTime, 'HH')` becomes `formatDate(EventTime, 'HH', 'Australia/Canberra')`, and
/// `formatDate(EventTime)` becomes `formatDate(EventTime, '<Stroom's default pattern>', 'Australia/Canberra')`. A call
/// that names a zone is left as it is. Text in strings and comments is never changed.
final class DateFunctionZones {

    private static final Set<String> FUNCTIONS = Set.of("formatdate", "parsedate");

    private DateFunctionZones() {
        // Static methods only.
    }

    /// @param text A StroomQL query or an expression, or null.
    /// @param zone The call's zone.
    /// @return The text with the zone added to each formatDate and parseDate call that has none; the text as given
    /// in UTC (as they already work in), or if it has none.
    static String withZone(final String text, final ToolTimeZone zone) {
        if (text == null || zone.isUtc()) {
            return text;
        }
        final List<int[]> calls = new ArrayList<>();
        find(text, calls);
        if (calls.isEmpty()) {
            return text;
        }
        final StringBuilder sb = new StringBuilder(text);
        // Added at the last closing bracket first, so adding one doesn't move those before it (a call inside
        // another closes first).
        calls.sort(Comparator.comparingInt((int[] call) -> call[0]).reversed());
        for (final int[] call : calls) {
            // The default pattern quotes its 'T', so it goes in double quotes.
            final String added = call[1] == 1
                    ? ", \"" + DateUtil.DEFAULT_PATTERN + "\", '" + zone.id() + "'"
                    : ", '" + zone.id() + "'";
            sb.insert(call[0], added);
        }
        return sb.toString();
    }

    // The closing bracket's position and the number of arguments of each formatDate or parseDate call with one or
    // two arguments, in the order written.
    private static void find(final String text, final List<int[]> calls) {
        int i = 0;
        while (i < text.length()) {
            final int skipped = skip(text, i);
            if (skipped != i) {
                i = skipped;
                continue;
            }
            final char c = text.charAt(i);
            if (Character.isLetter(c) && (i == 0 || !isNamePart(text.charAt(i - 1)))) {
                int end = i;
                while (end < text.length() && isNamePart(text.charAt(end))) {
                    end++;
                }
                final String name = text.substring(i, end).toLowerCase(Locale.ROOT);
                int open = end;
                while (open < text.length() && Character.isWhitespace(text.charAt(open))) {
                    open++;
                }
                if (FUNCTIONS.contains(name) && open < text.length() && text.charAt(open) == '(') {
                    final int[] call = arguments(text, open);
                    if (call != null && (call[1] == 1 || call[1] == 2)) {
                        calls.add(call);
                    }
                }
                i = end;
            } else {
                i++;
            }
        }
    }

    // From an opening bracket: the matching closing bracket's position and the number of arguments between them
    // (0 for none), or null if it isn't closed.
    private static int[] arguments(final String text, final int open) {
        int depth = 0;
        int args = 0;
        boolean any = false;
        int i = open;
        while (i < text.length()) {
            final int skipped = skip(text, i);
            if (skipped != i) {
                any = true;
                i = skipped;
                continue;
            }
            final char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return new int[]{i, any
                            ? args + 1
                            : 0};
                }
            } else if (c == ',' && depth == 1) {
                args++;
            } else if (depth >= 1 && !Character.isWhitespace(c)) {
                any = true;
            }
            i++;
        }
        return null;
    }

    // Past a string ('...' or "...", a doubled or backslashed quote inside it) or a comment (// to the line's end,
    // or /* */) starting at the position; the position itself if none starts there.
    private static int skip(final String text, final int from) {
        final char c = text.charAt(from);
        if (c == '\'' || c == '"') {
            int i = from + 1;
            while (i < text.length()) {
                final char d = text.charAt(i);
                if (d == '\\') {
                    i += 2;
                } else if (d == c) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == c) {
                        i += 2;
                    } else {
                        return i + 1;
                    }
                } else {
                    i++;
                }
            }
            return text.length();
        } else if (c == '/' && from + 1 < text.length()) {
            if (text.charAt(from + 1) == '/') {
                final int end = text.indexOf('\n', from);
                return end < 0
                        ? text.length()
                        : end;
            } else if (text.charAt(from + 1) == '*') {
                final int end = text.indexOf("*/", from + 2);
                return end < 0
                        ? text.length()
                        : end + 2;
            }
        }
        return from;
    }

    private static boolean isNamePart(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
