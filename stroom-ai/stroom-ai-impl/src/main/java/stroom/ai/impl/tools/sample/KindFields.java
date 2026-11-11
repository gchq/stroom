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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.FieldHomes;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// The fields of structured records (delimited, JSON, key=value) that tell their kinds of event apart, chosen by their
/// values: a few short values, each repeating, not one a record. A field named like a kind field (`type`, `action`,
/// `event_id`...) is preferred, but only if its values pass too; a field with another name (`msg_act`) is a
/// candidate the user decides on. The survey and the draft choose the same way, so the kinds the survey counts are the
/// kinds the draft makes rules for.
///
/// Asked for by the user (seen in testing, a CSV mail log whose kind column was `msg_act`): the survey chose its
/// columns afresh in each part read, by name or a narrow fallback, so its kinds shifted from part to part and it was
/// never covered; the draft knew the column by no name, so every record was one 'other' event, nothing was put to the
/// user to classify, and records went to the catch-all as Unknown.
public final class KindFields {

    /// The kind fields that say the records are all one kind of event (the user's choice): no field tells them apart.
    public static final String ONE_KIND = "(one kind)";
    /// The answer that says the records are all one kind of event.
    public static final String ONE_KIND_ANSWER = "All one kind of event";
    /// The most distinct values a kind field may have.
    static final int MAX_VALUES = 200;
    /// The values a kind field may have without each repeating: an excerpt may show each of a few dozen kinds once.
    static final int FEW_VALUES = 64;
    /// The records a field must have values in, each a value of its own, to read as an identifier rather than kinds.
    static final int EACH_ONCE_RECORDS = 5;
    // The longest a kind value is, and the most words it has: kinds are codes or short phrases, not sentences.
    private static final int MAX_LENGTH = 40;
    private static final int MAX_WORDS = 3;
    // The records a field must have values in to be put to the user as a kind field without a kind field's name,
    // each of its values in two of them on average: a few records show nothing repeating (seen: {"a": 1}, {"a": 2}).
    static final int MIN_RECORDS = 6;
    // The most candidates put to the user.
    private static final int MAX_CANDIDATES = 5;
    // The most of a candidate's values said.
    private static final int VALUES_SAID = 4;
    private static final Set<String> NIL = Set.of("-", "", "null", "none", "n/a");
    private static final Set<String> STRUCTURED = Set.of(SampleProfiler.DELIMITED, SampleProfiler.JSON_LINES,
            SampleProfiler.JSON_ARRAY, SampleProfiler.JSON_OBJECT, SampleProfiler.KEY_VALUE_FORMAT);
    // A value that is an address, an id or a time, whatever the field's name.
    private static final Pattern ADDRESS = Pattern.compile("@|^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$|^[0-9a-fA-F]{12,}$"
                                                           + "|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}|\\d{4}-\\d{2}-\\d{2}|^\\d{10,13}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern QUOTES = Pattern.compile("^['\"`]|['\"`]$");

    private KindFields() {
        // Static only.
    }

    /// @param records The records, parsed (a field's value a string, number or boolean; others are passed over).
    /// @return The kind fields and how sure the choice is.
    public static Choice choose(final List<Map<String, Object>> records) {
        final Map<String, List<String>> values = valuesByField(records);
        final List<Candidate> candidates = new ArrayList<>();
        values.forEach((field, held) -> {
            if (namedLikeKinds(field)
                    ? passesNamed(held)
                    : passes(field, held)) {
                candidates.add(candidate(field, held));
            }
        });
        final List<String> named = candidates.stream()
                .filter(Candidate::named)
                .map(Candidate::field)
                .toList();
        if (!named.isEmpty()) {
            return new Choice(named, true, List.of(), Set.copyOf(values.keySet()));
        }
        if (candidates.isEmpty()) {
            return new Choice(List.of(), true, List.of(), Set.copyOf(values.keySet()));
        }
        final List<Candidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt(Candidate::records).reversed()
                .thenComparingInt(candidate -> candidate.values().size()));
        final List<Candidate> shown = ranked.subList(0, Math.min(MAX_CANDIDATES, ranked.size()));
        return new Choice(List.of(shown.getFirst().field()), false, List.copyOf(shown), Set.copyOf(values.keySet()));
    }

    /// @param format A format, as [SampleProfiler] names it.
    /// @return True if its records' fields are the source's own (delimited, JSON, key=value), so a field among them
    /// may tell kinds apart; not for text a splitter or extraction reads into fields.
    public static boolean structured(final String format) {
        return STRUCTURED.contains(format);
    }

    /// @param name A field's name.
    /// @return True if it is named like a field that tells kinds of event apart (`type`, `action`, `event_id`,
    /// `msg_id`...).
    public static boolean namedLikeKinds(final String name) {
        return ToolText.search(Shapes.NAMING, name);
    }

    /// @param given A kind_field argument, as the user said it: a field, fields as `a, b`, or [#ONE_KIND_ANSWER].
    /// @return The kind fields it names ([#ONE_KIND] alone for one kind), or null when none is given.
    public static List<String> parse(final String given) {
        if (given == null || given.isBlank()) {
            return null;
        }
        final String text = given.strip();
        if (text.equalsIgnoreCase(ONE_KIND_ANSWER) || text.equalsIgnoreCase(ONE_KIND)
            || text.toLowerCase(Locale.ROOT).startsWith("all one kind")) {
            return List.of(ONE_KIND);
        }
        final List<String> fields = new ArrayList<>();
        for (final String part : text.split("[,;+]")) {
            final String field = unquoted(part);
            if (!field.isEmpty() && !fields.contains(field)) {
                fields.add(field);
            }
        }
        return List.copyOf(fields);
    }

    /// @param fields Kind fields as kept (a choice's, or the user's).
    /// @return True if they say the records are all one kind of event.
    public static boolean oneKind(final List<String> fields) {
        return fields != null && fields.size() == 1 && ONE_KIND.equals(fields.getFirst());
    }

    /// @param answer What the user answered: an option ([Candidate#label()]), [#ONE_KIND_ANSWER], or a field's
    ///               name in their own words.
    /// @param choice The choice put to them.
    /// @return The kind fields they chose: a candidate's field, [#ONE_KIND] alone, or the fields they named that the
    /// records have; empty when their answer names none.
    public static List<String> fromAnswer(final String answer, final Choice choice) {
        final Set<String> fields = choice.recordFields();
        final String given = answer == null
                ? ""
                : answer.strip();
        if (given.equalsIgnoreCase(ONE_KIND_ANSWER) || given.toLowerCase(Locale.ROOT).startsWith("all one kind")) {
            return List.of(ONE_KIND);
        }
        for (final Candidate candidate : choice.candidates()) {
            if (given.equalsIgnoreCase(candidate.label())) {
                return List.of(candidate.field());
            }
        }
        final List<String> named = new ArrayList<>();
        for (final String part : given.split("[,;+]|\\band\\b")) {
            final String word = unquoted(part);
            fields.stream().filter(field -> field.equalsIgnoreCase(word)).findFirst().ifPresent(named::add);
        }
        return List.copyOf(new LinkedHashSet<>(named));
    }

    // A field's name as given, without spaces or quotes around it.
    private static String unquoted(final String part) {
        return QUOTES.matcher(part.strip()).replaceAll("");
    }

    // Each field's values, as text: scalars only, nil values aside.
    private static Map<String, List<String>> valuesByField(final List<Map<String, Object>> records) {
        final Map<String, List<String>> out = new LinkedHashMap<>();
        for (final Map<String, Object> record : records) {
            if (record == null) {
                continue;
            }
            record.forEach((field, value) -> {
                if (value instanceof String || value instanceof Number || value instanceof Boolean
                    || value instanceof BigInteger) {
                    final String text = String.valueOf(value).strip();
                    final List<String> held = out.computeIfAbsent(field, k -> new ArrayList<>());
                    if (!NIL.contains(text.toLowerCase(Locale.ROOT))) {
                        held.add(text);
                    }
                }
            });
        }
        return out;
    }

    // Whether a field named like a kind field holds kinds: values that aren't one a record (a mail log's msg_id, each
    // message's own), nor addresses or times; one value is enough (the part read may hold one kind of many).
    private static boolean passesNamed(final List<String> held) {
        final Set<String> distinct = new LinkedHashSet<>(held);
        if (distinct.isEmpty() || distinct.size() > MAX_VALUES) {
            return false;
        }
        if (held.size() >= EACH_ONCE_RECORDS && distinct.size() == held.size()) {
            return false;
        }
        if (distinct.size() > FEW_VALUES && distinct.size() * 2L > held.size()) {
            return false;
        }
        return distinct.stream().allMatch(value -> value.length() <= MAX_LENGTH * 2
                                                   && !ADDRESS.matcher(value).find()
                                                   && !TIME.matcher(value).find());
    }

    // Whether a field's values are a kind field's: at least two values, short codes or phrases, not addresses or
    // times, repeating (or few), and not one a record; and the field has no home of its own (a host, a user).
    private static boolean passes(final String field, final List<String> held) {
        final Set<String> distinct = new LinkedHashSet<>(held);
        if (distinct.size() < 2 || distinct.size() > MAX_VALUES || held.size() < MIN_RECORDS
            || distinct.size() * 2L > held.size()) {
            return false;
        }
        if (FieldHomes.HOMES.stream().anyMatch(home -> FieldHomes.matches(home.regex(), field))) {
            return false;
        }
        return distinct.stream().allMatch(value -> value.length() <= MAX_LENGTH
                                                   && WHITESPACE.split(value).length <= MAX_WORDS
                                                   && !ADDRESS.matcher(value).find()
                                                   && !TIME.matcher(value).find());
    }

    private static Candidate candidate(final String field, final List<String> held) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        held.forEach(value -> counts.merge(value, 1, Integer::sum));
        final List<String> byCount = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        return new Candidate(field, byCount, held.size(), namedLikeKinds(field));
    }


    // --------------------------------------------------------------------------------


    /// The fields chosen, and how sure.
    ///
    /// @param fields     The kind fields, best first: empty when no field tells kinds apart.
    /// @param certain    True when the choice needs no one to confirm it: a field named like a kind field passes,
    ///                   or no field could be one.
    /// @param candidates   When not certain, the fields that could be, best first (the first is the one chosen),
    ///                     for the user to decide among.
    /// @param recordFields Every field the records have, which the user may name instead.
    public record Choice(List<String> fields, boolean certain, List<Candidate> candidates, Set<String> recordFields) {

        /// @return The options to put to the user: each candidate's label, then [#ONE_KIND_ANSWER].
        public List<String> options() {
            final List<String> out = new ArrayList<>(candidates.stream().map(Candidate::label).toList());
            out.add(ONE_KIND_ANSWER);
            return out;
        }

        /// @return The candidates as the user reads them, a line each, in plain text.
        public String said() {
            final List<String> lines = new ArrayList<>();
            candidates.forEach(candidate -> lines.add(candidate.label() + ": " + candidate.records()
                                                      + " records"));
            return String.join("\n", lines);
        }
    }

    /// A field that could tell the kinds of event apart.
    ///
    /// @param field   Its name.
    /// @param values  Its distinct values, most frequent first.
    /// @param records How many records have a value in it.
    /// @param named   True if it is named like a kind field.
    public record Candidate(String field, List<String> values, int records, boolean named) {

        /// @return How the user is shown it: its name and its commonest values, e.g. `msg_act (allow, quarantine,
        /// reject)`.
        public String label() {
            final List<String> some = values.subList(0, Math.min(VALUES_SAID, values.size()));
            return field + " (" + String.join(", ", some) + (values.size() > VALUES_SAID
                    ? ", +" + (values.size() - VALUES_SAID) + " more"
                    : "") + ")";
        }
    }
}
