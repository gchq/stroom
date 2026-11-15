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

import stroom.ai.impl.tools.validation.CloseMatches;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// The fields of a data source a user's words name, matched loosely: the user may say "Recipient ID" for
/// `Email/Recipient/Id` or `email.recipient.id`. The words given and each field's name are split into words ignoring
/// case, separators (`/ . _ -` and spaces) and camelCase, and word order is ignored. A field naming only some of the
/// words matches less well (ranked by how many it covers); a misspelt word matches a close one; where no field's name
/// matches, the words are looked for in the fields' descriptions ("who received it"). One clear match is that field;
/// several as close are all given, each with why it matched, for the user to choose: never a silent guess.
public final class FieldMatches {

    /// The most fields given when several match.
    public static final int MOST_GIVEN = 8;
    /// The most fields given as the closest by name when none matches.
    public static final int CLOSEST_GIVEN = 5;

    // A word misspelt is still this alike (difflib's ratio) to the one meant.
    private static final double CLOSE = 0.8;
    // A field naming fewer than this share of the words given doesn't match.
    private static final double LEAST_COVERED = 0.5;
    private static final Pattern CAMEL = Pattern.compile(
            "(?<=[\\p{Ll}\\p{N}])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    // Words that say nothing of which field is meant ("the field holding who received it").
    private static final Set<String> STOP_WORDS = Set.of("a", "an", "and", "any", "are", "as", "at", "by", "did",
            "do", "does", "each", "field", "fields", "for", "from", "has", "have", "holding", "holds", "in", "is",
            "it", "its", "of", "on", "or", "that", "the", "their", "them", "this", "to", "value", "values", "was",
            "were", "what", "when", "where", "which", "who", "whom", "whose", "with");

    private FieldMatches() {
        // Static only.
    }

    /// @param text A field's name, or the user's words for one.
    /// @return Its words, lower case: split at separators (anything but letters and digits) and camelCase
    /// (`IPAddress` is `ip`, `address`).
    public static List<String> words(final String text) {
        final List<String> out = new ArrayList<>();
        for (final String part : SEPARATORS.split(CAMEL.matcher(NullSafe.string(text)).replaceAll(" "))) {
            if (!part.isEmpty()) {
                out.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /// @param query        The user's words for a field, e.g. `Recipient ID`.
    /// @param names        The data source's field names.
    /// @param descriptions What each field holds, by name, from its documentation (may lack some, or be empty).
    /// @return The fields matched: one, several (ranked) or none (with the closest by name).
    public static Matched match(final String query,
                                final List<String> names,
                                final Map<String, String> descriptions) {
        final List<String> wanted = List.copyOf(new LinkedHashSet<>(words(query)));
        if (wanted.isEmpty() || names.isEmpty()) {
            return new Matched(Kind.NONE, List.of());
        }
        // The name itself, however it is written.
        final List<Candidate> same = names.stream()
                .filter(name -> String.join("", words(name)).equals(String.join("", words(query))))
                .map(name -> new Candidate(name, 1.0, "its name"))
                .toList();
        if (same.size() == 1) {
            return new Matched(Kind.ONE, same);
        }
        final List<Scored> byName = new ArrayList<>();
        for (final String name : names) {
            final Scored scored = byName(wanted, name);
            if (scored.covered() >= LEAST_COVERED) {
                byName.add(scored);
            }
        }
        byName.sort(Comparator.comparingDouble(Scored::covered).reversed()
                .thenComparing(Comparator.comparingDouble(Scored::exactness).reversed())
                .thenComparing(Comparator.comparingDouble(Scored::named).reversed())
                .thenComparingInt(scored -> scored.name().length()));
        final List<Scored> whole = byName.stream().filter(scored -> scored.covered() >= 1.0).toList();
        if (!whole.isEmpty()) {
            // Every word given is in its name, and its name has no other: the field the user named.
            final List<Scored> exact = whole.stream()
                    .filter(scored -> scored.exactness() >= 1.0 && scored.named() >= 1.0)
                    .toList();
            if (whole.size() == 1 || exact.size() == 1) {
                final Scored one = whole.size() == 1
                        ? whole.getFirst()
                        : exact.getFirst();
                return new Matched(Kind.ONE, List.of(one.candidate()));
            }
            return new Matched(Kind.SEVERAL, whole.stream().limit(MOST_GIVEN).map(Scored::candidate).toList());
        }
        final List<Candidate> described = byDescription(wanted, names, descriptions);
        if (described.size() == 1 && described.getFirst().score() >= 1.0) {
            return new Matched(Kind.ONE, described);
        }
        if (!described.isEmpty() || !byName.isEmpty()) {
            final List<Candidate> several = new ArrayList<>(described);
            byName.stream()
                    .map(Scored::candidate)
                    .filter(candidate -> several.stream().noneMatch(c -> c.name().equals(candidate.name())))
                    .forEach(several::add);
            return new Matched(Kind.SEVERAL, several.stream().limit(MOST_GIVEN).toList());
        }
        return new Matched(Kind.NONE, closest(query, names));
    }

    // How well a field's name covers the words given: each word found as it is, as the same word (singular or
    // plural), run together with the next (username for user name), as the start of one (addr for address), or
    // misspelt. Covered counts each word found however; alikeness how alike they are (1 for every word as it is).
    private static Scored byName(final List<String> wanted, final String name) {
        final List<String> have = words(name);
        final Set<Integer> used = new LinkedHashSet<>();
        final List<String> why = new ArrayList<>();
        int covered = 0;
        double alikeness = 0;
        for (final String word : wanted) {
            int best = -1;
            double score = 0;
            String said = null;
            for (int i = 0; i < have.size(); i++) {
                final String held = have.get(i);
                final double alike = alike(word, held);
                final String joined = i + 1 < have.size()
                        ? held + have.get(i + 1)
                        : null;
                if (alike > score) {
                    best = i;
                    score = alike;
                    said = alike >= 1.0
                            ? word
                            : alike >= 0.9
                                    ? word + " as " + held
                                    : word + " (close to " + held + ")";
                }
                if (joined != null && score < 1.0 && word.equals(joined)) {
                    best = i;
                    score = 1.0;
                    said = word + " as " + held + " " + have.get(i + 1);
                    used.add(i + 1);
                }
            }
            if (best >= 0) {
                used.add(best);
                covered++;
                alikeness += score;
                why.add(said);
            }
        }
        // A field word the given words run together (user name for username).
        for (int i = 0; i + 1 < wanted.size(); i++) {
            final String joined = wanted.get(i) + wanted.get(i + 1);
            final int at = have.indexOf(joined);
            if (at >= 0 && !used.contains(at)) {
                used.add(at);
                why.add(wanted.get(i) + " " + wanted.get(i + 1) + " as " + joined);
                covered += 2;
                alikeness += 2;
            }
        }
        final double share = Math.min(1.0, (double) covered / wanted.size());
        final double named = have.isEmpty()
                ? 0
                : (double) used.size() / have.size();
        final String said = share >= 1.0
                ? "its name has every word (" + String.join(", ", why) + ")"
                : "its name has only " + String.join(", ", why) + " of the words given";
        return new Scored(name, share, Math.min(1.0, alikeness / wanted.size()), named, said);
    }

    // How alike two words are: 1 the same (or the same but for a plural or tense ending), 0.9 one the start of the
    // other (at least 3 letters), the ratio for a misspelling (both 4 letters or more), else 0.
    private static double alike(final String word, final String held) {
        if (word.equals(held) || stem(word).equals(stem(held)) || word.equals(stem(held))
            || stem(word).equals(held)) {
            return 1.0;
        }
        if (Math.min(word.length(), held.length()) >= 3 && (held.startsWith(word) || word.startsWith(held))) {
            return 0.9;
        }
        if (word.length() >= 4 && held.length() >= 4) {
            final double ratio = CloseMatches.ratio(word, held);
            return ratio >= CLOSE
                    ? Math.min(ratio, 0.85)
                    : 0;
        }
        return 0;
    }

    // A word without a plural or tense ending: addresses as address, received as receiv, sending as send.
    private static String stem(final String word) {
        for (final String ending : List.of("ing", "ed", "es", "s")) {
            if (word.endsWith(ending) && word.length() - ending.length() >= 3) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }

    // The fields whose descriptions hold the words given that say which (not "the", "who"...), the most covered
    // first; those holding fewer than half are left out.
    private static List<Candidate> byDescription(final List<String> wanted,
                                                 final List<String> names,
                                                 final Map<String, String> descriptions) {
        final List<String> content = wanted.stream().filter(word -> !STOP_WORDS.contains(word)).toList();
        if (content.isEmpty() || descriptions == null || descriptions.isEmpty()) {
            return List.of();
        }
        final List<Candidate> out = new ArrayList<>();
        for (final String name : names) {
            final String description = descriptions.get(name);
            if (NullSafe.isBlankString(description)) {
                continue;
            }
            final List<String> held = words(description);
            final List<String> found = content.stream()
                    .filter(word -> held.stream().anyMatch(h -> alike(word, h) >= 0.9))
                    .toList();
            final double share = (double) found.size() / content.size();
            if (share >= LEAST_COVERED) {
                out.add(new Candidate(name, share, "its description has " + String.join(", ", found) + ": \""
                                                   + ToolText.head(description.strip(), 120) + "\""));
            }
        }
        out.sort(Comparator.comparingDouble(Candidate::score).reversed());
        // One as covered as the best is no clear match.
        if (out.size() > 1 && out.get(1).score() >= out.getFirst().score()) {
            return out.stream().map(c -> new Candidate(c.name(), Math.min(c.score(), 0.99), c.why())).toList();
        }
        return out;
    }

    // The fields whose names are most like the words given, run together.
    private static List<Candidate> closest(final String query, final List<String> names) {
        final String given = String.join("", words(query));
        return names.stream()
                .map(name -> new Candidate(name, CloseMatches.ratio(String.join("", words(name)), given),
                        "the closest by name"))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(CLOSEST_GIVEN)
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// How the words given matched.
    public enum Kind {
        /// One field clearly: the one meant.
        ONE,
        /// Several as close: the user is to choose.
        SEVERAL,
        /// None: the candidates are the closest by name.
        NONE
    }


    /// A field matched.
    ///
    /// @param name  Its name.
    /// @param score How well it matched, from 0 to 1.
    /// @param why   Why it matched, as the user reads it.
    public record Candidate(String name, double score, String why) {

    }


    /// The fields the words given name.
    ///
    /// @param kind       One, several or none.
    /// @param candidates The field (one), the fields ranked (several), or the closest by name (none).
    public record Matched(Kind kind, List<Candidate> candidates) {

    }


    // A field's name scored against the words given: the share of them it covers, how alike they are (1 for each
    // found as it is), the share of its own words they name, and why.
    private record Scored(String name, double covered, double exactness, double named, String why) {

        Candidate candidate() {
            return new Candidate(name, covered, why);
        }
    }
}
