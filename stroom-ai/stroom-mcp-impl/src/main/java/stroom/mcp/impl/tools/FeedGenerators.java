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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// The EventSource/Generator an aggregated feed's common module writes for every source type group: the feed's,
/// naming no source type (decided by the user 2026-11-23). Seen in chat 188: the first group's draft source name,
/// "Microsoft 365 audit (Exchange Online and Teams)", became the shared Generator, and a later Entra ID group then
/// wrote "Exchange Online and Teams" too, or changed the shared module to say its own.
///
/// A value names a source type when it holds the source type's name, or a display name the registry holds for it
/// (its System Name, the value its row matches), compared word by word, in any case or spacing (`ExchangeOnline`,
/// "Exchange Online"); or its last word alone ("Teams" of `MicrosoftTeams`), unless that word is the feed's own
/// (a word of the feed's name). Nothing is known of any source by name: only the registry's names are read.
final class FeedGenerators {

    /// The registry's key for the feed's own Generator, as the user gave it: `value`, `by` and `at`.
    static final String REGISTRY_KEY = "generator";
    /// The value the common module's Generator part takes where the feed's own isn't known yet: the draft's default.
    static final String PLACEHOLDER = "TODO generator";

    // Where a name splits into words: between a lower case letter or digit and an upper case one, before the last
    // upper case letter of a run followed by a lower case one, and at anything but a letter or digit.
    private static final Pattern WORD_BREAK = Pattern.compile(
            "(?<=[\\p{Ll}\\d])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})|[^\\p{L}\\d]+");
    // Brackets left holding nothing but joining words or punctuation once the source types' names are taken out.
    private static final Pattern EMPTY_BRACKETS = Pattern.compile(
            "[(\\[]\\s*(?:(?:and|or|&|,|/|\\+|-|;)\\s*)*[)\\]]", Pattern.CASE_INSENSITIVE);
    // Joining words or punctuation left at either end.
    private static final Pattern LOOSE_ENDS = Pattern.compile(
            "^(?:\\s|and\\b|or\\b|&|,|/|\\+|-|;|:)+|(?:\\s|\\band|\\bor|&|,|/|\\+|-|;|:)+$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");
    // The shortest word that names a source type alone.
    private static final int SHORTEST_WORD = 3;

    private FeedGenerators() {
        // Static only.
    }

    /// @param state A source type registry's state.
    /// @return The names it holds for its source types: each row's name, the value it matches (a value row's) and
    /// its System Name, each once.
    static List<String> sourceTypeNames(final Map<String, Object> state) {
        final Set<String> out = new LinkedHashSet<>();
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            for (final String key : List.of("name", "value", "system_name")) {
                final String name = JsonValues.text(row.get(key));
                if (!name.isBlank()) {
                    out.add(name);
                }
            }
        }
        return List.copyOf(out);
    }

    /// @param state A source type registry's state.
    /// @return The feed's own Generator, as the user gave it and the registry keeps it; null where none is kept.
    static String kept(final Map<String, Object> state) {
        final String value = JsonValues.text(JsonValues.mapOrEmpty(state.get(REGISTRY_KEY)).get("value"));
        return value.isBlank()
                ? null
                : value;
    }

    /// @param value A Generator's value, or null.
    /// @param names The registry's names for its source types ([#sourceTypeNames]).
    /// @param feed  The feed's name, whose own words name no source type alone.
    /// @return The names it names, in the order given; none for a value naming none.
    static List<String> named(final String value, final List<String> names, final String feed) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        final Set<String> feedWords = Set.copyOf(words(feed));
        return names.stream()
                .filter(name -> run(value, words(name), feedWords) != null)
                .toList();
    }

    /// The value with the source types' names it holds taken out, as proposed to the user for the feed's own
    /// Generator: "Microsoft 365 audit (Exchange Online and Teams)" proposes "Microsoft 365 audit".
    ///
    /// @param value    A Generator's value naming source types.
    /// @param names    The registry's names for its source types.
    /// @param feed     The feed's name.
    /// @param fallback What is proposed where nothing is left.
    /// @return The proposal.
    static String withoutSourceTypes(final String value,
                                     final List<String> names,
                                     final String feed,
                                     final String fallback) {
        String left = Objects.requireNonNullElse(value, "");
        final Set<String> feedWords = Set.copyOf(words(feed));
        for (final String name : names) {
            final List<String> run = run(left, words(name), feedWords);
            if (run != null) {
                left = pattern(run).matcher(left).replaceAll(" ");
            }
        }
        String before;
        do {
            before = left;
            left = EMPTY_BRACKETS.matcher(left).replaceAll(" ");
            left = LOOSE_ENDS.matcher(left.strip()).replaceAll("");
            left = SPACES.matcher(left).replaceAll(" ").strip();
        } while (!left.equals(before));
        return left.isBlank()
                ? fallback
                : left;
    }

    // The longest run of a name's words, ending with its last, that the value holds (in any case, with or without
    // spaces or punctuation between them, as a word of its own); null for none. The last word alone counts only when
    // it is long enough, not a number, and not a word of the feed's name.
    private static List<String> run(final String value, final List<String> name, final Set<String> feedWords) {
        if (name.isEmpty()) {
            return null;
        }
        for (int from = 0; from < name.size(); from++) {
            final List<String> run = name.subList(from, name.size());
            if (run.size() == 1 && from > 0) {
                final String last = run.getFirst();
                if (last.length() < SHORTEST_WORD || last.chars().allMatch(Character::isDigit)
                    || feedWords.contains(last)) {
                    return null;
                }
            }
            if (pattern(run).matcher(value).find()) {
                return run;
            }
        }
        return null;
    }

    // The words in a row, as a word of their own, in any case, anything but letters and digits between them.
    private static Pattern pattern(final List<String> run) {
        return Pattern.compile("(?<![\\p{L}\\d])" + String.join("[\\W_]*", run.stream()
                .map(Pattern::quote)
                .toList()) + "(?![\\p{L}\\d])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static List<String> words(final String text) {
        if (text == null) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        Arrays.stream(WORD_BREAK.split(text))
                .filter(word -> !word.isBlank())
                .map(word -> word.toLowerCase(Locale.ROOT))
                .forEach(out::add);
        return out;
    }
}
