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

import java.util.regex.Pattern;

/// Glob patterns: `*` for any characters, `?` for one, `[abc]` for one of a set and `[!abc]` for one not in it,
/// case-sensitive.
public final class Globs {

    private Globs() {
    }

    /// @param text    The text, e.g. a pipeline's name.
    /// @param pattern The glob pattern, e.g. `Event Data (*)`.
    /// @return True if the whole text matches the pattern.
    public static boolean matches(final String text, final String pattern) {
        if (text == null || pattern == null) {
            return false;
        }
        return Pattern.compile(regex(pattern), Pattern.DOTALL).matcher(text).matches();
    }

    private static String regex(final String pattern) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            final char c = pattern.charAt(i);
            i++;
            if (c == '*') {
                sb.append(".*");
            } else if (c == '?') {
                sb.append('.');
            } else if (c == '[') {
                final int end = setEnd(pattern, i);
                if (end < 0) {
                    sb.append("\\[");
                } else {
                    sb.append(set(pattern.substring(i, end)));
                    i = end + 1;
                }
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    // Where a set starting at from ends (its ']'), or -1 if it doesn't: a ']' first in the set is one of its
    // characters, as fnmatch has it.
    private static int setEnd(final String pattern, final int from) {
        int j = from;
        if (j < pattern.length() && pattern.charAt(j) == '!') {
            j++;
        }
        if (j < pattern.length() && pattern.charAt(j) == ']') {
            j++;
        }
        while (j < pattern.length() && pattern.charAt(j) != ']') {
            j++;
        }
        return j < pattern.length()
                ? j
                : -1;
    }

    private static String set(final String inside) {
        final boolean negated = inside.startsWith("!");
        final String chars = negated
                ? inside.substring(1)
                : inside;
        final StringBuilder sb = new StringBuilder("[");
        if (negated) {
            sb.append('^');
        }
        for (final char c : chars.toCharArray()) {
            if (c == '\\' || c == '[' || c == ']' || c == '^' || c == '&') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append(']').toString();
    }
}
