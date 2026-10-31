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

package stroom.ai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Splits text into the parts that match a search and the parts between, so the matches can be highlighted.
final class ChatSearchText {

    private ChatSearchText() {
        // Static methods only.
    }

    /// @param text  The text.
    /// @param query What was searched for, matched ignoring case, or null or blank for no matches.
    /// @return The text in order, each part marked if it is a match. Empty for null or empty text.
    static List<Part> split(final String text, final String query) {
        final List<Part> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return parts;
        }
        final String needle = query == null
                ? ""
                : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            parts.add(new Part(text, false));
            return parts;
        }
        final String haystack = text.toLowerCase(Locale.ROOT);
        int from = 0;
        int at = haystack.indexOf(needle);
        // Lower-casing can change a string's length (rarely); don't highlight then, rather than mark the wrong part.
        if (haystack.length() != text.length()) {
            at = -1;
        }
        while (at >= 0) {
            if (at > from) {
                parts.add(new Part(text.substring(from, at), false));
            }
            parts.add(new Part(text.substring(at, at + needle.length()), true));
            from = at + needle.length();
            at = haystack.indexOf(needle, from);
        }
        if (from < text.length()) {
            parts.add(new Part(text.substring(from), false));
        }
        return parts;
    }

    /// A part of the text.
    ///
    /// @param text  The part.
    /// @param match True if it matches the search.
    record Part(String text, boolean match) {

    }
}
