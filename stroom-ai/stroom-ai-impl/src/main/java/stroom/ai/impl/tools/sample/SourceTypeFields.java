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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The source type field of an aggregated feed: one field (the wrapper's or the record's own, at any depth) whose
/// values say which source a record came from, and so which fields it has and what its message is (PEGA's source
/// type, Azure's workload). Chosen by values and shapes alone, never by a product's field names: the field whose
/// values best tell apart records that differ in their fields, their message's format or its template, where each
/// value holds kinds of event of its own (a kind field's values are each one shape, so a kind field isn't taken).
///
/// Where no one reading tells the shapes apart well enough but two together do, the pair is offered first
/// ([SourceTypeCombinations]).
///
/// Also how a record's message is read for the profiles: its format (JSON, XML with its root, syslog, key=value,
/// text) and whether it parsed.
public final class SourceTypeFields {

    /// The answer that says the feed is not aggregated: its records come from one source.
    public static final String NOT_AGGREGATED = "Not an aggregated feed: one source";
    /// The format of a record with no message field.
    public static final String NO_MESSAGE = "no message field";
    /// A message of JSON.
    public static final String JSON = "json";
    /// A message of XML (its root element follows).
    public static final String XML = "xml";
    /// A message in syslog form.
    public static final String SYSLOG = "syslog";
    /// A message of key=value pairs.
    public static final String KEY_VALUE = "key=value";
    /// A message of other text.
    public static final String TEXT = "text";

    // The most distinct values of a source type field: past them it names something finer than a source.
    static final int MAX_VALUES = 200;
    // The share of records a source type field must have a value in.
    static final double PRESENT = 0.9;
    // How far the field's values must tell the records' shapes apart beyond chance (0 none, 1 completely); lower
    // where the user said the feed aggregates sources.
    static final double MIN_SEPARATION = 0.6;
    static final double MIN_SEPARATION_SAID = 0.3;
    // The fewest records to choose from.
    static final int MIN_RECORDS = 12;
    // How much better a held reading must separate than a record's own field to be offered before it.
    static final double HELD_HANDICAP = 0.1;
    // The most candidates put to the user.
    static final int MAX_CANDIDATES = 4;
    // The words of a message's template kept: enough to tell its kind, not its values.
    static final int TEMPLATE_WORDS = 6;
    // The most characters of text before a message's JSON or XML (`Request: <soapenv:Envelope...`).
    static final int MAX_PREFIX = 40;
    // The most of a candidate's values said.
    private static final int VALUES_SAID = 4;
    // The homes of fields that say who, where or when, never which source. Not the address homes: their names
    // (`source`, `src`) are also a source's name or path (a collector's `source`), and an address is told by its
    // value.
    private static final Set<String> NOT_SOURCES = Set.of("EventSource/Device/HostName", "EventSource/Device/Name",
            "EventSource/Device/MACAddress", "EventSource/Client/Port", "EventSource/Server/Port",
            "EventSource/User/Id", "EventSource/RunAs/Id", "EventSource/SessionId", "EventDetail/Description");
    /// The values a field holds for no value, as the tools and a source type group read them (any case), the empty
    /// value aside.
    public static final List<String> NIL_VALUES = HeldText.NIL_VALUES;
    private static final Pattern ADDRESS = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?$|^[0-9a-fA-F]{12,}$"
                                                           + "|^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-|@");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}:\\d{2}|\\d{4}-\\d{2}-\\d{2}|^\\d{10,13}$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern XML_NAME = Pattern.compile("<([A-Za-z_][\\w:.-]*)");
    private static final Pattern DECLARATION = Pattern.compile("^<\\?xml[^>]*\\?>\\s*");
    private static final Pattern CALENDAR = Pattern.compile(
            "(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|mon|tue|wed|thu|fri|sat|sun)[a-z]*,?$");
    private static final Pattern PAIR = Pattern.compile("(?<![\\w.])[A-Za-z_][\\w.-]{0,40}=");
    private static final int MIN_PAIRS = 3;
    private static final int FREE_TEXT = 40;
    // A field named for kinds of event within a source (an event type, action or operation): never a source type.
    private static final Pattern KIND_ROLE = Pattern.compile("(^|[._-])(event_?type|event_?name|event_?id|action|"
                                                             + "activity|operation|op|kind|result_?type|msg_?id|"
                                                             + "message_?id|subtype)$", Pattern.CASE_INSENSITIVE);

    private SourceTypeFields() {
        // Static only.
    }

    /// The fields that could be the source type field, best first: the records' own fields, and the names inside
    /// the structured data their string fields hold ([HeldText#expand]: `event>logger_name`), each judged the same
    /// way ([HeldCandidates]). Where a held field's formats name the source differently (`logger_name` in JSON and
    /// key=value, `@logger` in XML), a reading trying each in turn is offered first (`event>logger_name |
    /// event>xml:@logger`), for the user to confirm. Never a field named for kinds of event (an event type, action
    /// or operation: classify_kinds' to tell apart within a source type), nor a held value's format alone (the
    /// user's choice only, as a last resort).
    ///
    /// @param records The records read, parsed (flattened to dotted names).
    /// @param said    True when the user said the feed aggregates sources: weaker separation is enough, and a field
    ///                whose values are each one shape is offered too.
    /// @return The candidates, at most four; none when no field tells sources apart.
    public static List<Candidate> candidates(final List<Map<String, Object>> records, final boolean said) {
        final List<Map<String, Object>> held = records.stream().filter(record -> record != null).toList();
        if (held.size() < MIN_RECORDS) {
            return List.of();
        }
        final double threshold = said
                ? MIN_SEPARATION_SAID
                : MIN_SEPARATION;
        final Map<String, Map<String, Integer>> valuesByField = scalarValues(held);
        final List<Candidate> found = new ArrayList<>();
        for (final Map.Entry<String, Map<String, Integer>> entry : valuesByField.entrySet()) {
            final String field = entry.getKey();
            final Map<String, Integer> counts = entry.getValue();
            final int present = counts.values().stream().mapToInt(Integer::intValue).sum();
            if (present < PRESENT * held.size() || counts.size() < 2 || counts.size() > MAX_VALUES
                || counts.size() * 2L > present || !mayName(field, counts.keySet())) {
                continue;
            }
            final List<String> values = held.stream().map(record -> valueOf(record, field)).toList();
            final Candidate candidate = judge(field, values, i -> shapeKey(held.get(i), field),
                    i -> naming(held.get(i), field), counts, present, Map.of());
            if (candidate.separation() >= threshold && (said || candidate.holdsKinds())) {
                found.add(candidate);
            }
        }
        found.addAll(HeldCandidates.of(held, threshold, said));
        // A record's own field first, unless a held reading separates clearly better: the simpler reading.
        found.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.separation() - (candidate.held()
                        ? HELD_HANDICAP
                        : 0.0)).reversed()
                .thenComparingInt(candidate -> candidate.values().size()));
        final List<Candidate> ordered = new ArrayList<>(HeldCandidates.joinedFirst(found));
        // A pair only where no single reading tells the record shapes apart well enough: offered first.
        SourceTypeCombinations.pair(held, List.copyOf(ordered.subList(0, Math.min(MAX_CANDIDATES, ordered.size()))),
                        threshold, said)
                .ifPresent(pair -> ordered.addFirst(pair));
        return List.copyOf(ordered.subList(0, Math.min(MAX_CANDIDATES, ordered.size())));
    }

    /// @param record A record, parsed.
    /// @param field  The source type field: a field, dotted, or a reading into held values ([SourceTypeReading]).
    /// @return Its value as text, or null when it has none (nil values aside, and a reading that isn't one).
    public static String valueOf(final Map<String, Object> record, final String field) {
        if (record == null || field == null) {
            return null;
        }
        if (!SourceTypeReading.isReading(field)) {
            return HeldText.normalised(SourceTypeReading.Path.top(record, field));
        }
        final SourceTypeReading reading = SourceTypeReading.cached(field);
        return reading == null
                ? null
                : reading.valueOf(record);
    }

    /// A record's shape, as the source type field is chosen by: its fields' names (the given field aside), its
    /// message's format, and the message's root element, JSON keys or first words masked.
    ///
    /// @param record   A record, parsed.
    /// @param excluded A field left out of the names, or null.
    /// @return Its shape.
    public static String shapeKey(final Map<String, Object> record, final String excluded) {
        final List<String> names = new ArrayList<>(record.keySet());
        names.remove(excluded);
        names.sort(ToolText::compare);
        final Held held = held(record);
        return String.join(",", names) + " | " + held.format() + (held.root() == null
                ? ""
                : " " + held.root()) + " | " + held.template();
    }

    /// @param record A record, parsed.
    /// @return The field holding its message: one named like a message (`message`, `msg`, `body`...) with text,
    /// else its longest free-text field; null when it has neither.
    public static String messageField(final Map<String, Object> record) {
        String longest = null;
        for (final Map.Entry<String, Object> entry : record.entrySet()) {
            // A part's meta attribute laid over its records (a source type field) is never their message.
            if (!(entry.getValue() instanceof final String text) || text.isBlank()
                || SourceTypeMeta.isMeta(entry.getKey())) {
                continue;
            }
            if (ToolText.search(Shapes.MESSAGE, ToolText.lastPart(entry.getKey(), '.'))) {
                return entry.getKey();
            }
            if (text.length() >= FREE_TEXT && ToolText.count(text, " ") >= 5
                && (longest == null || text.length() > ((String) record.get(longest)).length())) {
                longest = entry.getKey();
            }
        }
        return longest;
    }

    /// @param record A record, parsed.
    /// @return How its message is held: [#NO_MESSAGE] when it has none.
    public static Held held(final Map<String, Object> record) {
        final String field = messageField(record);
        return field == null
                ? new Held(NO_MESSAGE, null, false, Map.of(), "")
                : format((String) record.get(field));
    }

    /// A message's format, read as a source type's messages are: JSON (after a short prefix), XML with its root
    /// element (after a short prefix, a declaration aside), syslog, key=value pairs, or text.
    ///
    /// @param message A message.
    /// @return Its format, whether it failed to parse, and (for JSON) its fields.
    public static Held format(final String message) {
        final String text = ToolText.strip(message.replace("﻿", ""));
        final int brace = text.indexOf('{');
        if (brace >= 0 && brace <= MAX_PREFIX && (brace == 0 || !text.substring(0, brace).contains("<"))) {
            try {
                final Object json = SampleProfiler.loadsDecoded(text.substring(brace));
                if (json instanceof Map) {
                    final Map<String, Object> fields = SampleProfiler.flatten(json);
                    final List<String> keys = new ArrayList<>(fields.keySet());
                    keys.sort(ToolText::compare);
                    return new Held(JSON, null, false, fields, String.join(",", keys));
                }
            } catch (final ToolJson.JsonDecodeException e) {
                if (brace == 0) {
                    return new Held(JSON, null, true, Map.of(), "");
                }
            }
        }
        final String body = DECLARATION.matcher(text).replaceFirst("");
        final int start = SampleProfiler.xmlStart(body);
        if (start >= 0 && start <= MAX_PREFIX) {
            final Matcher name = XML_NAME.matcher(body);
            if (name.find(start) && name.start() == start) {
                final String root = name.group(1);
                return new Held(XML, root, !wellFormed(body.substring(start)), Map.of(), "");
            }
        }
        if (ToolText.search(SampleProfiler.SYSLOG_5424, text) || ToolText.search(SampleProfiler.SYSLOG_3164, text)) {
            return new Held(SYSLOG, null, false, Map.of(), template(syslogBody(text)));
        }
        if (ToolText.findall(PAIR, text).size() >= MIN_PAIRS) {
            final List<String> keys = new ArrayList<>(new TreeSet<>(ToolText.findall(PAIR, text)));
            return new Held(KEY_VALUE, null, false, Map.of(), String.join(",", keys));
        }
        return new Held(TEXT, null, false, Map.of(), template(text));
    }

    /// @param text A message's text.
    /// @return Its first words with their variable parts masked (numbers, addresses, dates): its template.
    public static String template(final String text) {
        final List<String> words = new ArrayList<>();
        for (final String word : Shapes.mask(text)) {
            words.add(ToolText.fullmatch(CALENDAR, word)
                    ? "<t>"
                    : word);
            if (words.size() >= TEMPLATE_WORDS) {
                break;
            }
        }
        return String.join(" ", words);
    }

    // A syslog line after its header (priority, time and host), from its program on.
    private static String syslogBody(final String text) {
        final Matcher m3164 = ToolText.matchAt(SampleProfiler.SYSLOG_3164, text);
        if (m3164 != null) {
            return text.substring(m3164.end());
        }
        final Matcher m5424 = ToolText.matchAt(SampleProfiler.SYSLOG_5424, text);
        return m5424 != null
                ? text.substring(m5424.end())
                : text;
    }

    private static boolean wellFormed(final String xml) {
        try {
            XmlTree.parse(xml);
            return true;
        } catch (final XmlTree.XmlException | IllegalArgumentException e) {
            return false;
        }
    }

    // Each scalar field's values and how many records hold each.
    private static Map<String, Map<String, Integer>> scalarValues(final List<Map<String, Object>> records) {
        final Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (final Map<String, Object> record : records) {
            for (final String field : record.keySet()) {
                final String value = valueOf(record, field);
                if (value != null) {
                    out.computeIfAbsent(field, k -> new LinkedHashMap<>()).merge(value, 1, Integer::sum);
                }
            }
        }
        return out;
    }

    /// Whether a field may say which source a record came from: not one whose name gives it a home that says who,
    /// where or when (a host, a user, an address), not a message, not one named for kinds of event (an event type,
    /// action or operation: telling those apart within a source type is classify_kinds' and the rules' job, never a
    /// source type's), and its values short names, not sentences, addresses or times (a path may hold a date: a log
    /// file rotated daily).
    ///
    /// @param field  A field, dotted, or a reading's path into a held value (judged by its last name).
    /// @param values Its values.
    /// @return True if it may.
    static boolean mayName(final String field, final Set<String> values) {
        final String last = SourceTypeReading.lastName(field);
        final String dotted = SourceTypeReading.isReading(field)
                ? field.replace(SourceTypeReading.HELD, ".")
                : field;
        final boolean homed = FieldHomes.HOMES.stream()
                .anyMatch(home -> NOT_SOURCES.contains(home.path()) && FieldHomes.matches(home.regex(), dotted));
        if (homed || ToolText.search(Shapes.MESSAGE, last) || ToolText.search(KIND_ROLE, last)) {
            return false;
        }
        return values.stream().allMatch(value -> value.length() <= MAX_VALUES
                                                 && WHITESPACE.split(value).length <= 4
                                                 && !ADDRESS.matcher(value).find()
                                                 && (SourceTypeRows.structured(value) || !TIME.matcher(value).find()));
    }

    /// How well a field's values tell the records' shapes apart, and whether its values hold kinds of their own.
    ///
    /// @param field   The field, or a reading.
    /// @param values  Each record's value, null for none.
    /// @param shapeOf A record's shape, by its index.
    /// @param namesOf A record's values of fields named like kinds, by its index.
    /// @param counts  The records holding each value.
    /// @param present The records holding a value.
    /// @param formats The records with a value by the format of the held value read ([HeldText]); none for a field.
    /// @return The candidate.
    static Candidate judge(final String field,
                           final List<String> values,
                           final IntFunction<String> shapeOf,
                           final IntFunction<String> namesOf,
                           final Map<String, Integer> counts,
                           final int present,
                           final Map<String, Integer> formats) {
        final Map<String, Map<String, Integer>> valuesByShape = new HashMap<>();
        final Map<String, Set<String>> kindsByValue = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            final String value = values.get(i);
            if (value == null) {
                continue;
            }
            final String shape = shapeOf.apply(i);
            valuesByShape.computeIfAbsent(shape, k -> new HashMap<>()).merge(value, 1, Integer::sum);
            kindsByValue.computeIfAbsent(value, k -> new HashSet<>()).add(shape + " | " + namesOf.apply(i));
        }
        final int noValue = values.size() - present;
        if (valuesByShape.size() < 2) {
            return new Candidate(field, byCount(counts), counts, present, 0.0, false, formats, noValue);
        }
        final long pure = valuesByShape.values().stream()
                .mapToLong(byValue -> byValue.values().stream().mapToInt(Integer::intValue).max().orElse(0))
                .sum();
        final double purity = (double) pure / present;
        final double largest = (double) counts.values().stream().mapToInt(Integer::intValue).max().orElse(0)
                               / present;
        final double separation = largest >= 1.0
                ? 0.0
                : Math.max(0.0, (purity - largest) / (1.0 - largest));
        // A source holds several kinds of event; a kind field's values are each one.
        final int kinds = kindsByValue.values().stream().mapToInt(Set::size).sum();
        final long varied = kindsByValue.values().stream().filter(set -> set.size() >= 2).count();
        final boolean holdsKinds = kinds >= counts.size() + 2 && varied >= 2;
        return new Candidate(field, byCount(counts), counts, present, ToolText.round(separation, 3), holdsKinds,
                formats, noValue);
    }

    /// @param record   A record, parsed.
    /// @param excluded A field left out.
    /// @return The values of the fields named like kinds of event (other than the given field), the message's
    /// JSON's too.
    static String naming(final Map<String, Object> record, final String excluded) {
        final List<String> pairs = new ArrayList<>();
        final Map<String, Object> all = new LinkedHashMap<>(record);
        all.putAll(held(record).fields());
        all.forEach((name, value) -> {
            if (!name.equals(excluded) && KindFields.namedLikeKinds(ToolText.lastPart(name, '.'))) {
                final String text = valueOf(all, name);
                if (text != null && text.length() <= 60) {
                    pairs.add(name + "=" + text);
                }
            }
        });
        pairs.sort(ToolText::compare);
        return String.join(",", pairs);
    }

    static List<String> byCount(final Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// A field that could be the source type field.
    ///
    /// @param field      Its name, dotted, or a reading into held values ([SourceTypeReading]).
    /// @param values     Its distinct values, commonest first.
    /// @param counts     The records holding each value.
    /// @param records    The records holding a value in it.
    /// @param separation How far its values tell the records' shapes apart beyond chance: 0 not at all, 1 each
    ///                   shape only ever in one value's records.
    /// @param holdsKinds True when its values each hold several kinds of event (shapes or kind values), as sources
    ///                   do and a kind field's values don't.
    /// @param formats    For a reading into held values: the records with a value by the held value's format
    ///                   ([HeldText]); none for a field.
    /// @param noValue    The records read with no value in it: for a held reading, those whose held value is plain
    ///                   text, another format, or lacks it (the registry's `(no value)` row); for a combination,
    ///                   those with a part of no value.
    /// @param combined   For a pair of readings ([SourceTypeCombinations]): its rows proposed and the evidence it is
    ///                   offered on; null for one reading.
    public record Candidate(String field,
                            List<String> values,
                            Map<String, Integer> counts,
                            int records,
                            double separation,
                            boolean holdsKinds,
                            Map<String, Integer> formats,
                            int noValue,
                            SourceTypeCombinations.Combined combined) {

        /// One reading.
        ///
        /// @param field      Its name, dotted, or a reading into held values.
        /// @param values     Its distinct values, commonest first.
        /// @param counts     The records holding each value.
        /// @param records    The records holding a value in it.
        /// @param separation How far its values tell the records' shapes apart beyond chance.
        /// @param holdsKinds True when its values each hold several kinds of event.
        /// @param formats    For a reading into held values: the records with a value by the held value's format.
        /// @param noValue    The records read with no value in it.
        public Candidate(final String field,
                         final List<String> values,
                         final Map<String, Integer> counts,
                         final int records,
                         final double separation,
                         final boolean holdsKinds,
                         final Map<String, Integer> formats,
                         final int noValue) {
            this(field, values, counts, records, separation, holdsKinds, formats, noValue, null);
        }

        /// A field of the records' own.
        ///
        /// @param field      Its name, dotted.
        /// @param values     Its distinct values, commonest first.
        /// @param counts     The records holding each value.
        /// @param records    The records holding a value in it.
        /// @param separation How far its values tell the records' shapes apart beyond chance.
        /// @param holdsKinds True when its values each hold several kinds of event.
        public Candidate(final String field,
                         final List<String> values,
                         final Map<String, Integer> counts,
                         final int records,
                         final double separation,
                         final boolean holdsKinds) {
            this(field, values, counts, records, separation, holdsKinds, Map.of(), 0);
        }

        /// @return How the user is shown it: its name and its commonest values, e.g. `sourcetype (PegaDB,
        /// PegaRULES, +4 more)`.
        public String label() {
            final List<String> some = values.subList(0, Math.min(VALUES_SAID, values.size()));
            return field + " (" + String.join(", ", some) + (values.size() > VALUES_SAID
                    ? ", +" + (values.size() - VALUES_SAID) + " more"
                    : "") + ")";
        }

        /// @return True if it reads into held values (a combination: a part of it does).
        public boolean held() {
            if (SourceTypeReading.isCombination(field)) {
                final SourceTypeReading reading = SourceTypeReading.cached(field);
                return reading != null && reading.held();
            }
            return SourceTypeReading.isReading(field);
        }

        /// @return True if it is a pair of readings, offered with its rows and evidence.
        public boolean combination() {
            return combined != null;
        }
    }

    /// How a record's message is held.
    ///
    /// @param format   [#JSON], [#XML], [#SYSLOG], [#KEY_VALUE], [#TEXT] or [#NO_MESSAGE].
    /// @param root     An XML message's root element (with any prefix), else null.
    /// @param failed   True when it looked like JSON or XML and didn't parse (cut short, say).
    /// @param fields   A JSON message's fields, flattened; none otherwise.
    /// @param template Its keys (JSON, key=value) or first words masked (text, syslog).
    public record Held(String format, String root, boolean failed, Map<String, Object> fields, String template) {

        /// @return The format as the registry says it: `xml Envelope`, `json`, `text`...
        public String named() {
            return root == null
                    ? format
                    : format + " " + root;
        }
    }
}
