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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.workspace.SafePattern;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The first match of each extraction regex in each text, worked out once for one tool call however many of its
/// parts read the same records: the draft giving records their pairs ([DraftMapping#withPairs]), the catalogue's
/// record index and the records listed with their held fields (each [LocalCheck#extractedValues]). Seen in eval
/// case 36 on Haiku: 60 pair regexes over 1,000 records run three times a `catalogue_event_types` call, each on
/// records read on their own, some 70% of the call.
///
/// A match is kept by its regex, its flags and the text it was found in, so a record read again (another instance
/// of the same text) finds it, and a text that differs is matched afresh. What is kept is bounded
/// ([#MAX_ENTRIES], [#MAX_CHARACTERS]): past either, matches are worked out without being kept. One is made for a
/// call and not shared between threads.
public final class PairMatches {

    /// The most matches kept for one call.
    public static final int MAX_ENTRIES = 250_000;
    /// The most characters kept for one call: the texts matched (each once) and the groups found in them.
    public static final long MAX_CHARACTERS = 16_000_000L;

    // What a match kept costs beyond the characters of its groups, in characters.
    private static final int ENTRY_CHARACTERS = 16;
    // Kept for a regex that doesn't match the text.
    private static final String[] NO_MATCH = new String[0];
    private static final PairMatches NONE = new PairMatches(0, 0);

    private final int maxEntries;
    private final long maxCharacters;
    private final Map<String, Map<Regex, String[]>> byText = new HashMap<>();
    private int entries;
    private long characters;

    private PairMatches(final int maxEntries, final long maxCharacters) {
        this.maxEntries = maxEntries;
        this.maxCharacters = maxCharacters;
    }

    /// @return Matches for one call, kept to [#MAX_ENTRIES] and [#MAX_CHARACTERS].
    public static PairMatches create() {
        return new PairMatches(MAX_ENTRIES, MAX_CHARACTERS);
    }

    /// @param maxEntries    The most matches kept.
    /// @param maxCharacters The most characters kept.
    /// @return Matches kept to those bounds.
    static PairMatches create(final int maxEntries, final long maxCharacters) {
        return new PairMatches(maxEntries, maxCharacters);
    }

    /// @return Matches that keep none: each worked out as it is asked for (one record's, say, where nothing is read
    /// twice). Safe to share, as it holds nothing.
    public static PairMatches none() {
        return NONE;
    }

    /// The first match of a regex in a text, as [Matcher#find] finds it.
    ///
    /// @param pattern The regex.
    /// @param text    The text.
    /// @param timed   True to match through [SafePattern#matcher] (the call's time limit checked as it reads), as
    ///                the evaluator does; false to match the text directly. Kept alike either way: the match is the
    ///                same.
    /// @return Each group of the first match, from group 1 (null for a group not in it); null when the regex doesn't
    /// match. Not to be changed.
    public String[] firstMatch(final Pattern pattern, final String text, final boolean timed) {
        if (maxEntries <= 0) {
            return found(pattern, text, timed);
        }
        final Regex regex = new Regex(pattern.pattern(), pattern.flags());
        final Map<Regex, String[]> known = byText.get(text);
        final String[] kept = known == null
                ? null
                : known.get(regex);
        if (kept != null) {
            return kept == NO_MATCH
                    ? null
                    : kept;
        }
        final String[] groups = found(pattern, text, timed);
        keep(text, known, regex, groups);
        return groups;
    }

    /// @return How many matches are kept.
    int entries() {
        return entries;
    }

    /// @return How many characters are kept, as counted against [#MAX_CHARACTERS].
    long characters() {
        return characters;
    }

    private void keep(final String text,
                      final Map<Regex, String[]> known,
                      final Regex regex,
                      final String[] groups) {
        long cost = ENTRY_CHARACTERS;
        if (known == null) {
            cost += text.length();
        }
        if (groups != null) {
            for (final String group : groups) {
                if (group != null) {
                    cost += group.length();
                }
            }
        }
        if (entries + 1 > maxEntries || characters + cost > maxCharacters) {
            return;
        }
        final Map<Regex, String[]> into = known == null
                ? byText.computeIfAbsent(text, key -> new HashMap<>())
                : known;
        into.put(regex, groups == null
                ? NO_MATCH
                : groups);
        entries++;
        characters += cost;
    }

    private static String[] found(final Pattern pattern, final String text, final boolean timed) {
        final Matcher matcher = timed
                ? SafePattern.matcher(pattern, text)
                : pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        final String[] groups = new String[matcher.groupCount()];
        for (int i = 1; i <= groups.length; i++) {
            groups[i - 1] = matcher.group(i);
        }
        return groups;
    }


    // --------------------------------------------------------------------------------


    // A regex as it is compiled: its text and flags.
    private record Regex(String regex, int flags) {

    }
}
