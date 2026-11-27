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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The fields of structured records (delimited, JSON, key=value) that tell their kinds of event apart, chosen by their
/// values: a few short values, each repeating, not one a record. A field named like a kind field (`type`, `action`,
/// `event_id`...) is preferred, but only if its values pass too and it has a value in most records; a field with
/// another name (`msg_act`) is a candidate the user decides on. A field named for where a record was handled (a host,
/// a node, a level, a shipper's own field) is never offered, and a qualified name (a Java logger's class) is a code
/// however long. The survey and the draft choose the same way, so the kinds the survey counts are the
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
    // The most words in a value of a field named like a kind field: a short phrase (`SSLVPN LOGIN`, `user login
    // failed`), not a sentence naming the user it is about (seen in testing: a Synology DSM `event` field, "User
    // [anja.michel] logged in from [10.0.0.5] via [SSH]", drafted as a kind per user and action).
    private static final int MAX_NAMED_WORDS = 6;
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
    // The records a field's values must be in, each a value of its own, for a field given as the kind field to be
    // refused as an identifier.
    private static final int EACH_ONCE_CHECKED = 20;
    // A time of day or a date inside a value: a kind names neither.
    private static final Pattern CLOCK = Pattern.compile("\\b\\d{1,2}:\\d{2}:\\d{2}\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b");
    // An address inside a value (`from [10.0.0.5]`): a kind names no address.
    private static final Pattern HOLDS_ADDRESS = Pattern.compile("@|\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}|\\d{4}-\\d{2}-\\d{2}|^\\d{10,13}$");
    // A field's own name said of a time (`time`, `timestamp`, `event_time`, `orig_rt`, `ts`, `date`, `eventTime`):
    // never offered as a kind field, whatever its values (seen in testing, 2026-11-18: an aggregated feed's `time`, in
    // epoch milliseconds, and `fields.orig_rt`, in epoch nanoseconds, put to the user as the kinds of event).
    private static final Pattern TIME_NAMED = Pattern.compile("(?i)^@?(time|timestamp|ts|rt|date|datetime|"
                                                              + "date_?time)$|[_-](time|timestamp|ts|rt|date|"
                                                              + "datetime|date_?time)$|^(time|timestamp|date)[_-]");
    private static final Pattern TIME_NAMED_CAMEL = Pattern.compile("[a-z0-9](Time|TimeStamp|Timestamp|Date|Ts)$");
    // A number in scientific notation (a JSON number read as a double: 1.763452800123E12).
    private static final Pattern SCIENTIFIC = Pattern.compile("^\\d(\\.\\d+)?[eE]\\+?\\d+$");
    // What an epoch time may be written as: whole digits, seconds perhaps with a fraction.
    private static final Pattern EPOCH_DIGITS = Pattern.compile("\\d{9,19}(\\.\\d{1,9})?");
    // Where a field's own name starts in a path: a JSON object's key, a held value's name, an XML element.
    private static final Pattern PATH_STEP = Pattern.compile("[.>/]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern QUOTES = Pattern.compile("^['\"`]|['\"`]$");
    // The longest a qualified name ([#QUALIFIED]) may be and still be a kind.
    private static final int MAX_QUALIFIED_LENGTH = 200;
    // A qualified name: three or more identifiers (each a letter, `_` or `$`, then word characters or `$`) joined by
    // `.`, `::`, `/` or `$`, with no whitespace, e.g. a Java logger's class. A code however long (seen in testing,
    // 2026-11-19: Pega's `logger_name`, class names past 80 characters, refused as "whole messages"). Digit-led
    // parts are not identifiers, so an IP address or a version string is no qualified name.
    private static final Pattern QUALIFIED = Pattern.compile(
            "^[A-Za-z_$][\\w$]*(?:(?:\\.|::|/|\\$)[A-Za-z_$][\\w$]*){2,}$");
    private static final Pattern QUALIFIED_SEPARATOR = Pattern.compile("\\.|::|/|\\$");
    // A field's own name (or its parent's and its own, joined: FieldHomes.keys) saying where a record was handled
    // rather than what happened: a host or node, a severity, a log shipper's own field. Never offered as a kind
    // field, though the user may name one (seen in testing, 2026-11-19: Pega's source_host, nodeId and level offered,
    // its logger never).
    private static final Pattern HANDLED_WHERE = Pattern.compile(
            "^(host|hostname|host_name|source_host|src_host|node|node_id|nodeid|server|server_name|pod|pod_name"
            + "|instance|instance_id"
            + "|level|log_level|loglevel|severity|priority|sev"
            + "|collector|forwarder|shipper|agent|beat|agent_name|agent_id)$"
            + "|host$|_node$|node_id$");

    private KindFields() {
        // Static only.
    }

    /// @param records The records, parsed (a field's value a string, number or boolean; others are passed over).
    /// @return The kind fields and how sure the choice is.
    public static Choice choose(final List<Map<String, Object>> records) {
        final Map<String, List<String>> values = valuesByField(records);
        final List<Candidate> candidates = passing(values);
        // Named like a kind field and with a value in most records: else the kinds it tells leave most records in
        // none (seen in testing, 2026-11-19: Pega's `operation`, on the SOAP third of the Integration records,
        // settled the choice, no form was shown, and the draft ended in one kind), so it is a candidate with the rest.
        final List<String> named = candidates.stream()
                .filter(Candidate::named)
                .filter(candidate -> inMost(candidate, records.size()))
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

    /// Why a field can't tell the records' kinds of event apart, judged by its values as [#choose] judges them: a field
    /// the records don't have, one whose values are each a record's own (an id), sentences, addresses or times, or
    /// too many to be kinds. Asked for by the user (seen in testing: `kind_field: "body"`, a whole syslog message,
    /// taken by name, and the kinds then chosen silently another way).
    ///
    /// @param field   A field, as the records spell it.
    /// @param records The records, parsed.
    /// @return Why it can't, said for the agent and the user; empty when it can.
    public static Optional<String> whyNot(final String field, final List<Map<String, Object>> records) {
        return concern(field, records).map(Concern::said);
    }

    /// As [#whyNot(String, List)], with whether the reason is a hard one or only its values' shape ([Concern]).
    ///
    /// @param field   A field, as the records spell it.
    /// @param records The records, parsed.
    /// @return Why it can't tell the kinds apart; empty when it can.
    public static Optional<Concern> concern(final String field, final List<Map<String, Object>> records) {
        final List<String> held = valuesByField(records).get(field);
        if (held == null) {
            return Optional.of(Concern.hard("the records have no field '" + field + "'"));
        }
        return concernOfValues(field, held);
    }

    /// @param field   A field, as the records spell it.
    /// @param records The records, parsed.
    /// @return The field as the user is shown it among the candidates (its name and commonest values); empty when the
    /// records hold no value in it.
    public static Optional<Candidate> candidateOf(final String field, final List<Map<String, Object>> records) {
        final List<String> held = valuesByField(records).get(field);
        return held == null || held.isEmpty()
                ? Optional.empty()
                : Optional.of(candidate(field, held));
    }

    /// As [#whyNot(String, List)], for a field's values already read.
    ///
    /// @param field  The field's name, as said.
    /// @param values Its values, a record's each (nil values may be among them: they are left out).
    /// @return Why it can't tell kinds apart; empty when it can.
    public static Optional<String> whyNotValues(final String field, final List<String> values) {
        return concernOfValues(field, values).map(Concern::said);
    }

    /// As [#whyNotValues(String, List)], with whether the reason is a hard one or only its values' shape
    /// ([Concern]): no value, or a value of its own in each record (an identifier), is hard; messages, addresses,
    /// times or too many values are its values' shape, which the user may overrule.
    ///
    /// @param field  The field's name, as said.
    /// @param values Its values, a record's each (nil values may be among them: they are left out).
    /// @return Why it can't tell kinds apart; empty when it can.
    public static Optional<Concern> concernOfValues(final String field, final List<String> values) {
        final List<String> held = values.stream()
                .filter(value -> value != null && !NIL.contains(value.strip().toLowerCase(Locale.ROOT)))
                .map(String::strip)
                .toList();
        if (held.isEmpty()) {
            return Optional.of(Concern.hard("'" + field + "' holds no value in the records"));
        }
        final Set<String> distinct = new LinkedHashSet<>(held);
        // Over enough records that kinds would repeat: an excerpt may show each of a few kinds once.
        final boolean eachOwn = held.size() >= EACH_ONCE_CHECKED && distinct.size() == held.size();
        // Messages and addresses first: what the user is told says what the field holds; hard, though, where each
        // record has a value of its own too (a whole message each).
        final Optional<Concern> messages = messagesConcern(field, distinct);
        if (messages.isPresent()) {
            return eachOwn
                    ? Optional.of(Concern.hard(messages.get().said()))
                    : messages;
        }
        if (eachOwn) {
            return Optional.of(Concern.hard("each record has a value of its own in '" + field + "' (an id, a time "
                                            + "or a message), so it names no kinds"));
        }
        if (distinct.size() > MAX_VALUES || distinct.size() > FEW_VALUES && distinct.size() * 2L > held.size()) {
            return Optional.of(Concern.shape("'" + field + "' has " + distinct.size() + " different values in "
                                             + held.size() + " records, too many to be kinds of event",
                    "it has " + distinct.size() + " different values in " + held.size() + " records",
                    distinct.iterator().next()));
        }
        return Optional.empty();
    }

    /// Whether a field's values are messages or addresses rather than kinds: values of sentences (more than a short
    /// phrase), holding an address or a time of day, or all epoch times ([#epochTimes]). Codes are kinds whatever
    /// they look like (a FortiGate logid, `0101039424`; a Windows event id; a qualified name such as a Java class,
    /// however long, [#qualifiedName]).
    ///
    /// @param field  The field's name, as said.
    /// @param values Its values.
    /// @return Why its values aren't kinds; empty when they may be.
    public static Optional<String> messagesNotKinds(final String field, final Collection<String> values) {
        return messagesConcern(field, values).map(Concern::said);
    }

    /// @param value A value.
    /// @return True if it is a qualified name ([#QUALIFIED]): three or more identifiers joined by `.`, `::`, `/` or
    /// `$`, with no whitespace, at most 200 characters (`com.example.app.Service`, `std::io::Error`).
    public static boolean qualifiedName(final String value) {
        return value != null && value.length() <= MAX_QUALIFIED_LENGTH && QUALIFIED.matcher(value).matches();
    }

    /// @param field A field's name, as the records spell it.
    /// @return True if its name says where a record was handled rather than what happened: a host or node, a
    /// severity, a log shipper's own field ([#HANDLED_WHERE], by its own name and its parent's joined to it). Never
    /// offered as a kind field.
    public static boolean namedAsHandledWhere(final String field) {
        return field != null && FieldHomes.keys(field).stream().anyMatch(key -> HANDLED_WHERE.matcher(key).find());
    }

    // Why a field's values aren't kinds (messages, addresses or times), as its values' shape: the user may overrule.
    private static Optional<Concern> messagesConcern(final String field, final Collection<String> values) {
        final Optional<String> sentence = values.stream()
                .filter(value -> value != null && (value.length() > MAX_LENGTH * 2 && !qualifiedName(value)
                                                   || WHITESPACE.split(value.strip()).length > MAX_NAMED_WORDS))
                .findFirst();
        if (sentence.isPresent()) {
            return Optional.of(Concern.shape("'" + field + "' holds whole messages (e.g. \""
                                             + ToolText.head(sentence.get(), 60) + "\"), not the short codes or "
                                             + "phrases that name kinds of event",
                    "its values look long", sentence.get()));
        }
        final Optional<String> addressOrTime = values.stream()
                .filter(value -> value != null && (HOLDS_ADDRESS.matcher(value).find()
                                                   || CLOCK.matcher(value).find()))
                .findFirst();
        if (addressOrTime.isPresent()) {
            return Optional.of(Concern.shape("'" + field + "' holds addresses or times (e.g. \""
                                             + ToolText.head(addressOrTime.get(), 60) + "\"), which name no kind "
                                             + "of event",
                    "its values hold addresses or times", addressOrTime.get()));
        }
        if (!epochTimes(values)) {
            return Optional.empty();
        }
        final String example = values.stream().filter(value -> value != null && !value.isBlank()).findFirst()
                .orElse("");
        return Optional.of(Concern.shape("'" + field + "' holds epoch times (e.g. \"" + ToolText.head(example, 60)
                                         + "\"), which name no kind of event",
                "its values look like epoch times", example));
    }

    /// Whether a field's values are all epoch times: whole numbers of seconds, milliseconds, microseconds or
    /// nanoseconds in the years 1990 to 2100, as [TimePatterns] reads them (a number written in scientific notation,
    /// as a JSON number read as a double is, read as the whole number it is). Such values repeat within a batch of
    /// records read at once, so repeating doesn't make them kinds.
    ///
    /// @param values A field's values (nil and null ones are passed over).
    /// @return True if there is at least one value and every one is an epoch time.
    public static boolean epochTimes(final Collection<String> values) {
        boolean any = false;
        for (final String value : values) {
            if (value == null || NIL.contains(value.strip().toLowerCase(Locale.ROOT))) {
                continue;
            }
            final String number = wholeNumber(value.strip());
            // Digits only, before their pattern is read: most values are words, and are no epoch time.
            final String pattern = EPOCH_DIGITS.matcher(number).matches()
                    ? TimePatterns.inferTimePattern(number)
                    : null;
            if (pattern == null || !TimePatterns.isEpoch(pattern)) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /// Whether a field's own name says it holds a time (`time`, `timestamp`, `ts`, `date`, `*_time`, `*_ts`, `*_rt`,
    /// `*_date`, `eventTime`...), its own name being the last step of a path (`fields.orig_rt`, `event>log_time`).
    /// Such a field is never offered as a kind field: a time tells no kinds of event apart, however its values repeat.
    ///
    /// @param field A field's name, as the records spell it.
    /// @return True if it is named as a time.
    public static boolean namedAsTime(final String field) {
        if (field == null) {
            return false;
        }
        final String[] steps = PATH_STEP.split(field.strip());
        final String own = steps.length == 0
                ? field.strip()
                : steps[steps.length - 1];
        return TIME_NAMED.matcher(own).find() || TIME_NAMED_CAMEL.matcher(own).find();
    }

    /// Every field that could tell the records' kinds apart, named like a kind field or not, best first (most
    /// records with a value, then most values: a level, `info` or `warning`, beside the kinds it is in, isn't them;
    /// seen in testing, a mail gateway's [info] put before its [SEGTrap]), for the user to choose among: where the
    /// kinds a field named like one tells leave most records in none (seen in testing: one kind of 433 records told,
    /// 5,567 of 6,000 in none).
    ///
    /// @param records The records, parsed.
    /// @return The choice: not certain, its candidates every field that passes (at most five), the first chosen.
    public static Choice candidates(final List<Map<String, Object>> records) {
        final Map<String, List<String>> values = valuesByField(records);
        final List<Candidate> candidates = passing(values);
        candidates.sort(Comparator.comparingInt(Candidate::records).reversed()
                .thenComparing(Comparator.comparingInt((Candidate candidate) -> candidate.values().size())
                        .reversed()));
        final List<Candidate> shown = List.copyOf(candidates.subList(0, Math.min(MAX_CANDIDATES,
                candidates.size())));
        return new Choice(shown.isEmpty()
                ? List.of()
                : List.of(shown.getFirst().field()), false, shown, Set.copyOf(values.keySet()));
    }

    /// @param format A format, as [SampleProfiler] names it.
    /// @return True if its records' fields are the source's own (delimited, JSON, key=value), so a field among them
    /// may tell kinds apart; not for text a splitter or extraction reads into fields.
    public static boolean structured(final String format) {
        return STRUCTURED.contains(format);
    }

    /// @param name A field's name: a held value's elements and names (`message/Event/Action`, `event>action`) by
    ///             their last.
    /// @return True if it is named like a field that tells kinds of event apart (`type`, `action`, `event_id`,
    /// `msg_id`...).
    public static boolean namedLikeKinds(final String name) {
        final int step = Math.max(name.lastIndexOf('/'), name.lastIndexOf(SourceTypeReading.HELD));
        return ToolText.search(Shapes.NAMING, name.substring(step + 1));
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

    /// @param given  Kind fields as the user gave them ([#parse]).
    /// @param fields The records' fields.
    /// @return The given fields as the records spell them (matched ignoring case), [#ONE_KIND] kept; the ones the
    /// records don't have left out. A field inside a held value written as a source type reading writes it
    /// (`event>logger_name`, [SourceTypeReading]) is matched to the name the draft reads it by: a JSON key as
    /// `event.logger_name`, an XML element as `event/logger_name`.
    public static List<String> asRecordsSpellThem(final List<String> given, final Set<String> fields) {
        if (oneKind(given)) {
            return given;
        }
        final List<String> out = new ArrayList<>();
        for (final String name : given) {
            final List<String> spellings = heldSpellings(name);
            fields.stream()
                    .filter(field -> spellings.stream().anyMatch(field::equalsIgnoreCase))
                    .findFirst()
                    .filter(field -> !out.contains(field))
                    .ifPresent(out::add);
        }
        return out;
    }

    // A name as given, and, for a held value's name (`event>a.b`), the draft's names for it: `event.a.b` for JSON and
    // `event/a/b` for XML.
    private static List<String> heldSpellings(final String name) {
        if (!name.contains(SourceTypeReading.HELD)) {
            return List.of(name);
        }
        return List.of(name, name.replace(SourceTypeReading.HELD, "."), name.replace(SourceTypeReading.HELD, "/")
                .replace('.', '/'));
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
            final List<String> spellings = heldSpellings(unquoted(part));
            fields.stream()
                    .filter(field -> spellings.stream().anyMatch(field::equalsIgnoreCase))
                    .findFirst()
                    .ifPresent(named::add);
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
        return distinct.stream().allMatch(value -> (value.length() <= MAX_LENGTH * 2 || qualifiedName(value))
                                                   && !ADDRESS.matcher(value).find()
                                                   && !TIME.matcher(value).find())
               && messagesNotKinds("", distinct).isEmpty();
    }

    // Every field whose values could tell kinds apart, as the records' fields come: not an XML element's name
    // anywhere below (`message//Action`, its path from the root is offered), not named as a time or as where a
    // record was handled, and its values passing (as a field named like a kind field's, or another's).
    private static List<Candidate> passing(final Map<String, List<String>> values) {
        final List<Candidate> candidates = new ArrayList<>();
        values.forEach((field, held) -> {
            if (!field.contains("//") && !namedAsTime(field) && !namedAsHandledWhere(field)
                && (namedLikeKinds(field)
                    ? passesNamed(held)
                    : passes(field, held))) {
                candidates.add(candidate(field, held));
            }
        });
        return candidates;
    }

    // Whether a candidate has a value in most of the records (more than half).
    private static boolean inMost(final Candidate candidate, final int records) {
        return candidate.records() * 2L > records;
    }

    // Whether a field's values are a kind field's: at least two values, short codes or phrases, not addresses or
    // times, repeating (or few), and not one a record; and the field has no home of its own (a host, a user).
    private static boolean passes(final String field, final List<String> held) {
        final Set<String> distinct = new LinkedHashSet<>(held);
        if (distinct.size() < 2 || distinct.size() > MAX_VALUES || held.size() < MIN_RECORDS
            || distinct.size() * 2L > held.size()) {
            return false;
        }
        if (FieldHomes.HOMES.stream().anyMatch(home -> FieldHomes.matches(home.regex(), field))
            || epochTimes(distinct)) {
            return false;
        }
        return distinct.stream().allMatch(value -> (value.length() <= MAX_LENGTH || qualifiedName(value))
                                                   && WHITESPACE.split(value).length <= MAX_WORDS
                                                   && !ADDRESS.matcher(value).find()
                                                   && !TIME.matcher(value).find());
    }

    // A number in scientific notation as the whole number it is; any other text as it is.
    private static String wholeNumber(final String text) {
        if (!SCIENTIFIC.matcher(text).matches()) {
            return text;
        }
        try {
            final BigDecimal number = new BigDecimal(text).stripTrailingZeros();
            return number.scale() <= 0
                    ? number.toPlainString()
                    : text;
        } catch (final NumberFormatException e) {
            return text;
        }
    }

    // A qualified name's last part (`SOAPService` of `com.pega...soap.SOAPService`); any other value as it is.
    private static String lastPartOfQualified(final String value) {
        if (!qualifiedName(value)) {
            return value;
        }
        final String[] parts = QUALIFIED_SEPARATOR.split(value);
        return parts[parts.length - 1];
    }

    /// @param value A field's value, as an example for the user.
    /// @return It short: a qualified name by its first two parts and its last (`com.pega…SOAPService`), any other
    /// value its first 60 characters.
    public static String shortExample(final String value) {
        if (value == null) {
            return "";
        }
        if (!qualifiedName(value)) {
            return ToolText.head(value, 60);
        }
        final Matcher separator = QUALIFIED_SEPARATOR.matcher(value);
        int ends = -1;
        for (int i = 0; i < 2 && separator.find(); i++) {
            ends = separator.start();
        }
        final String last = lastPartOfQualified(value);
        return ends < 0 || ends + last.length() >= value.length() - 1
                ? value
                : value.substring(0, ends) + "…" + last;
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
    /// @param certain    True when the choice needs no one to confirm it: a field named like a kind field passes and
    ///                   has a value in most records, or no field could be one.
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
            // A qualified name by its last part, so the form stays short: the field and its kinds keep the whole.
            final List<String> some = values.subList(0, Math.min(VALUES_SAID, values.size())).stream()
                    .map(KindFields::lastPartOfQualified)
                    .toList();
            return field + " (" + String.join(", ", some) + (values.size() > VALUES_SAID
                    ? ", +" + (values.size() - VALUES_SAID) + " more"
                    : "") + ")";
        }
    }

    /// Why a field can't tell the kinds of event apart, and whether the user may overrule it.
    ///
    /// @param said    Why, said for the agent and the user.
    /// @param shape   True if it is only its values' shape (whole messages, addresses or times, too many values),
    ///                which a user naming the field may overrule; false for a hard reason (the records don't have
    ///                it, it holds no value, or each record has a value of its own).
    /// @param concern The concern in a few words, for the user's form, e.g. `its values look long`; null when hard.
    /// @param example One of its values that shows it; null when hard.
    public record Concern(String said, boolean shape, String concern, String example) {

        static Concern hard(final String said) {
            return new Concern(said, false, null, null);
        }

        static Concern shape(final String said, final String concern, final String example) {
            return new Concern(said, true, concern, example);
        }

        /// @param field The field, as the user is shown it.
        /// @return The concern put to the user, e.g. `event.logger_name: its values look long (e.g.
        /// com.pega…SOAPService). Tell the kinds apart by it anyway?`.
        public String asked(final String field) {
            return field + ": " + concern + " (e.g. " + shortExample(example) + "). Tell the kinds apart by it "
                   + "anyway?";
        }
    }
}
