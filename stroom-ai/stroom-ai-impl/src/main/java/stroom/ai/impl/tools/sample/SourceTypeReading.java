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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// How a source type is read from a record: a **reading**, a path to a value that may step into structured data
/// held in string fields. Written as the registry keeps it (its `field`), one text:
///
/// * `sourcetype`, `event.Workload`: a field of the record (dotted names step into its objects, at any depth);
///   `meta:<Attribute>` a part's meta attribute ([SourceTypeMeta]). A reading of a field alone reads as it always
///   has, so a registry that names a field still reads.
/// * `event>logger_name`: `>` steps into the structured data a string field holds ([HeldText]: JSON, XML, a syslog
///   header or key=value pairs, its format told per record), then `.` into its objects or elements:
///   `event>payload.logger_name`. A held value inside a held value is stepped into again
///   (`event>payload>logger_name`), at most three deep. A step's names read whichever format the record holds (a
///   JSON key, an XML element below the root, a key=value key, a syslog field: `host`, `app`, `message`), so the same
///   name in two formats is one reading. A step may name one format only, `xml:`, `json:`, `kv:` or `syslog:`
///   (`event>xml:Header.@logger`); XML's `@name` is an attribute (last), `#root` the root element's name. `#format`
///   is the held value's format itself (`json`, `xml`, `syslog`, `kv`, `text`): a last resort, only on the user's
///   word.
/// * `a | b`: readings tried in order, the first with a value giving it (a reading for each format, where the formats
///   name it differently: `event>logger_name | event>xml:@logger`). A part's meta attribute is never one of them: it
///   is read on its own (found in review, 2026-11-18: a source type group's XSLT can't read one among others).
///
/// * `system + event>logger_name`: a **combination**, readings joined by ` + `, each a part read as above, where
///   only the parts together tell a source (`system` Teams or Skype, and inside `event`, `logger_name` System or
///   Application). At most [#MAX_PARTS] parts. Its value is a **key**, the parts' values joined by
///   [#KEY_SEPARATOR] (`Teams ‖ System`), a part with no value empty (`Teams ‖ `), and no value at all where no part
///   has one; the registry's rows match a key part by part ([SourceTypeRows]). A part may not be a part's meta
///   attribute.
///
/// A value is stripped of XML's white space, and the empty value and nil values (`-`, `null`, `none`, `n/a`) are no
/// value. A held value of plain text is never read: its records have no value (the registry's `(no value)` row).
public final class SourceTypeReading {

    /// What steps into a held value.
    public static final String HELD = ">";
    /// What separates readings tried in order.
    public static final String OR = " | ";
    /// The most held values stepped into, one inside another.
    public static final int MAX_HELD = 3;
    /// The most readings tried in order.
    public static final int MAX_ALTERNATIVES = 6;
    /// The step reading a held value's format itself.
    public static final String FORMAT_STEP = "#format";
    /// The step reading a held XML document's root element's name.
    public static final String ROOT_STEP = "#root";
    /// The formats a step may name, as `<format>:`.
    public static final List<String> FORMATS = HeldText.STRUCTURED;
    /// What joins the parts of a combination of readings.
    public static final String AND = " + ";
    /// The most parts of a combination.
    public static final int MAX_PARTS = 4;
    /// What joins a combination's part values in its key: `Teams ‖ System`.
    public static final String KEY_SEPARATOR = " ‖ ";
    // What a key is split on (its parts then stripped), so a key stripped at its ends still splits alike.
    private static final String KEY_SPLIT = "‖";
    private static final Pattern SPACE_AROUND = Pattern.compile(HeldText.SPACE_AROUND);
    // A '+' standing alone between white space: what joins a combination's parts (a text padded with a space each
    // side, so a part left empty at either end is found too).
    private static final Pattern AND_SPLIT = Pattern.compile("\\s\\+\\s");

    private static final Map<String, SourceTypeReading> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHED = 1000;

    private final String spec;
    private final List<Path> paths;
    // A combination's parts; none for one reading.
    private final List<SourceTypeReading> parts;

    private SourceTypeReading(final String spec, final List<Path> paths) {
        this(spec, paths, List.of());
    }

    private SourceTypeReading(final String spec, final List<Path> paths, final List<SourceTypeReading> parts) {
        this.spec = spec;
        this.paths = List.copyOf(paths);
        this.parts = List.copyOf(parts);
    }

    /// @param field A source type field as the registry keeps it, or null.
    /// @return True if it steps into a held value, tries several readings or combines readings; false for a field
    /// alone.
    public static boolean isReading(final String field) {
        return field != null && (field.contains(HELD) || field.contains(OR.strip()) || isCombination(field));
    }

    /// @param field A source type field as the registry keeps it, or null.
    /// @return True if it is a combination of readings (`system + event>logger_name`).
    public static boolean isCombination(final String field) {
        return field != null && AND_SPLIT.matcher(" " + field + " ").find();
    }

    /// @param text A combination, or a combination row's parts, as written.
    /// @return Its parts, split on each `+` standing alone, each stripped (an empty part kept, empty).
    public static List<String> splitParts(final String text) {
        final List<String> out = new ArrayList<>();
        for (final String part : AND_SPLIT.split(" " + text + " ", -1)) {
            out.add(part.strip());
        }
        return out;
    }

    /// @param field A source type field as the registry keeps it, or null.
    /// @return How many parts its values have: a combination's, else one.
    public static int partsOf(final String field) {
        if (!isCombination(field)) {
            return 1;
        }
        return splitParts(field).size();
    }

    /// A combination's key: its parts' values joined by [#KEY_SEPARATOR], a part with no value empty.
    ///
    /// @param values Each part's value, null for none.
    /// @return The key; null where no part has a value.
    public static String key(final List<String> values) {
        if (values.stream().allMatch(value -> value == null || value.isEmpty())) {
            return null;
        }
        return String.join(KEY_SEPARATOR, values.stream()
                .map(value -> value == null
                        ? ""
                        : value)
                .toList());
    }

    /// A combination's key split into its parts' values, each stripped ("" for a part with no value), as a source
    /// type group splits it (`tokenize()` on `‖`).
    ///
    /// @param key A key, or null.
    /// @return Its parts' values; none for null or the empty key.
    public static List<String> keyParts(final String key) {
        if (key == null || key.isEmpty()) {
            return List.of();
        }
        final List<String> out = new ArrayList<>();
        for (final String part : key.split(KEY_SPLIT, -1)) {
            out.add(SPACE_AROUND.matcher(part).replaceAll(""));
        }
        return out;
    }

    /// Reads a reading as written.
    ///
    /// @param spec The reading, as [SourceTypeReading] writes it.
    /// @return The reading.
    /// @throws IllegalArgumentException with what is wrong with it.
    public static SourceTypeReading of(final String spec) {
        final List<String> problems = new ArrayList<>();
        final SourceTypeReading reading = parse(spec, problems);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return reading;
    }

    /// @param spec A reading, as written.
    /// @return What is wrong with it, said for the agent and the user; empty when it is fine.
    public static Optional<String> problem(final String spec) {
        final List<String> problems = new ArrayList<>();
        parse(spec, problems);
        return problems.isEmpty()
                ? Optional.empty()
                : Optional.of(String.join("; ", problems));
    }

    /// @param spec A reading, as written.
    /// @return It, read once and kept; null when it isn't one.
    public static SourceTypeReading cached(final String spec) {
        if (spec == null) {
            return null;
        }
        final SourceTypeReading kept = CACHE.get(spec);
        if (kept != null) {
            return kept;
        }
        final List<String> problems = new ArrayList<>();
        final SourceTypeReading reading = parse(spec, problems);
        if (!problems.isEmpty()) {
            return null;
        }
        if (CACHE.size() > MAX_CACHED) {
            CACHE.clear();
        }
        CACHE.put(spec, reading);
        return reading;
    }

    /// @param spec A reading, or a field.
    /// @return The last name it reads, for judging what the value is (`logger_name` of `event>payload.logger_name`);
    /// the first reading's, where several are tried.
    public static String lastName(final String spec) {
        String last = spec == null
                ? ""
                : spec;
        if (isCombination(last)) {
            last = splitParts(last).getFirst();
        }
        final int or = last.indexOf(OR.strip());
        if (or >= 0) {
            last = last.substring(0, or).strip();
        }
        last = last.substring(last.lastIndexOf(HELD) + 1);
        final int colon = last.indexOf(':');
        if (colon > 0 && FORMATS.contains(last.substring(0, colon).toLowerCase(Locale.ROOT))) {
            last = last.substring(colon + 1);
        }
        return last.substring(last.lastIndexOf('.') + 1).replace("@", "");
    }

    /// @param spec    A reading the user gave.
    /// @param records The records, parsed.
    /// @return It with each reading's field spelt as the records spell it (matched ignoring case); empty when a
    /// field is none of theirs. The reading as given where there are no records to check it against.
    public static Optional<String> asRecordsSpellIt(final String spec, final List<Map<String, Object>> records) {
        final SourceTypeReading reading = of(spec);
        if (reading.isTuple()) {
            final List<String> spelt = new ArrayList<>();
            for (final SourceTypeReading part : reading.parts()) {
                final Optional<String> one = asRecordsSpellIt(part.spec(), records);
                if (one.isEmpty()) {
                    return Optional.empty();
                }
                spelt.add(one.get());
            }
            return Optional.of(String.join(AND, spelt));
        }
        if (records.isEmpty()) {
            return Optional.of(reading.spec());
        }
        final Set<String> names = new LinkedHashSet<>();
        records.forEach(record -> names.addAll(record.keySet()));
        final List<String> spelt = new ArrayList<>();
        for (final Path path : reading.paths()) {
            final String field = names.contains(path.field())
                    ? path.field()
                    : names.stream().filter(name -> name.equalsIgnoreCase(path.field())).findFirst()
                            .or(() -> deepField(path.field(), names))
                            .orElse(null);
            if (field == null) {
                return Optional.empty();
            }
            spelt.add(path.withField(field).spec());
        }
        return Optional.of(String.join(OR, spelt));
    }

    /// @param record A record, parsed (flattened to dotted names), or null.
    /// @return The value the reading reads (the first of its readings with one), normalised; null for none.
    public String valueOf(final Map<String, Object> record) {
        if (record == null) {
            return null;
        }
        if (isTuple()) {
            return key(parts.stream().map(part -> part.valueOf(record)).toList());
        }
        for (final Path path : paths) {
            final String value = HeldText.normalised(path.read(record));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /// @param record A record, parsed, or null.
    /// @return The format of the held value each reading first steps into, by the held field's path: what a
    /// record's held value was (for saying why it has no value).
    public Map<String, String> heldFormats(final Map<String, Object> record) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (record == null) {
            return out;
        }
        if (isTuple()) {
            parts.forEach(part -> part.heldFormats(record).forEach(out::putIfAbsent));
            return out;
        }
        for (final Path path : paths) {
            if (!path.held().isEmpty()) {
                final String text = Path.top(record, path.field());
                out.putIfAbsent(path.field(), text == null
                        ? "missing"
                        : HeldText.parse(text).format());
            }
        }
        return out;
    }

    /// @return The reading as written.
    public String spec() {
        return spec;
    }

    /// @return Its readings, tried in order; a combination's, every part's in turn.
    public List<Path> paths() {
        return paths;
    }

    /// @return True if it is a combination of readings, its value a key of its parts' values.
    public boolean isTuple() {
        return !parts.isEmpty();
    }

    /// @return A combination's parts, in order; this reading alone where it is one.
    public List<SourceTypeReading> parts() {
        return parts.isEmpty()
                ? List.of(this)
                : parts;
    }

    /// @return True if any of its readings steps into a held value.
    public boolean held() {
        return paths.stream().anyMatch(path -> !path.held().isEmpty());
    }

    /// @return True if it reads the held value's format itself somewhere ([#FORMAT_STEP]): the user's choice only.
    public boolean byFormat() {
        return paths.stream().anyMatch(path -> path.held().stream()
                .anyMatch(step -> FORMAT_STEP.equals(step.special())));
    }

    /// @return How it is said to people, e.g. `logger_name inside the held field event (its JSON, XML, syslog
    /// header or key=value pairs, told per record)`.
    public String said() {
        if (isTuple()) {
            return "the combination of " + parts.stream().map(SourceTypeReading::said)
                    .collect(Collectors.joining(" with "));
        }
        final List<String> out = new ArrayList<>();
        for (final Path path : paths) {
            if (path.held().isEmpty()) {
                out.add("the field " + path.field());
                continue;
            }
            final StringBuilder said = new StringBuilder();
            String inside = "the held field " + path.field();
            for (int i = 0; i < path.held().size(); i++) {
                final Step step = path.held().get(i);
                final String what = FORMAT_STEP.equals(step.special())
                        ? "the format"
                        : ROOT_STEP.equals(step.special())
                                ? "the root element's name"
                                : step.raw();
                final String of = (step.format() == null
                        ? "its JSON, XML, syslog header or key=value pairs, told per record"
                        : "when it holds " + step.format());
                if (i == path.held().size() - 1) {
                    said.append(what).append(" inside ").append(inside).append(" (").append(of).append(')');
                } else {
                    inside = what + " inside " + inside;
                }
            }
            out.add(said.toString());
        }
        return String.join(", else ", out);
    }

    @Override
    public String toString() {
        return spec;
    }

    // Reads a reading, its problems collected.
    private static SourceTypeReading parse(final String given, final List<String> problems) {
        if (given == null || given.isBlank()) {
            problems.add("a source type reading names a field");
            return new SourceTypeReading("", List.of());
        }
        final String text = given.strip();
        if (isCombination(text)) {
            return combination(text, problems);
        }
        if (!isReading(text)) {
            return new SourceTypeReading(text, List.of(new Path(text, List.of())));
        }
        final List<String> alternatives = new ArrayList<>();
        for (final String part : text.split("\\|", -1)) {
            alternatives.add(part.strip());
        }
        if (alternatives.size() > MAX_ALTERNATIVES) {
            problems.add("at most " + MAX_ALTERNATIVES + " readings are tried in order, not " + alternatives.size());
        }
        final List<Path> paths = new ArrayList<>();
        for (final String alternative : alternatives) {
            if (alternative.isEmpty()) {
                problems.add("an empty reading between '|'s: give each reading a field");
                continue;
            }
            final String field = alternative.split(HELD, 2)[0].strip();
            if (alternatives.size() > 1 && SourceTypeMeta.isMeta(field)) {
                problems.add("'" + field + "': a stream meta attribute is never one of several readings tried in "
                             + "order (it is one value for a part of a stream, read on its own as '" + field
                             + "')");
                continue;
            }
            final Path path = path(alternative, problems);
            if (path != null) {
                paths.add(path);
            }
        }
        final List<String> specs = paths.stream().map(Path::spec).toList();
        return new SourceTypeReading(String.join(OR, specs), paths);
    }

    // Reads a combination: its parts, each a reading of its own.
    private static SourceTypeReading combination(final String text, final List<String> problems) {
        final List<SourceTypeReading> parts = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        final List<String> given = splitParts(text);
        if (given.size() > MAX_PARTS) {
            problems.add("a combination has at most " + MAX_PARTS + " parts, not " + given.size());
        }
        for (final String part : given) {
            if (part.isEmpty()) {
                problems.add("an empty part between ' + 's: give each part a field");
                continue;
            }
            if (SourceTypeMeta.isMeta(part) && !part.contains(OR.strip())) {
                problems.add("'" + part + "': a stream meta attribute is never a part of a combination (it is one "
                             + "value a part of a stream, read on its own)");
                continue;
            }
            final SourceTypeReading reading = parse(part, problems);
            if (!seen.add(reading.spec())) {
                problems.add("'" + reading.spec() + "' is a part twice");
            }
            parts.add(reading);
        }
        final List<Path> paths = new ArrayList<>();
        parts.forEach(part -> paths.addAll(part.paths()));
        return new SourceTypeReading(String.join(AND, parts.stream().map(SourceTypeReading::spec).toList()), paths,
                parts);
    }

    // Reads one reading: a field, then steps into held values.
    private static Path path(final String text, final List<String> problems) {
        final List<String> parts = new ArrayList<>();
        for (final String part : text.split(HELD, -1)) {
            parts.add(part.strip());
        }
        final String field = parts.getFirst();
        if (field.isEmpty()) {
            problems.add("'" + text + "' starts with no field: a reading starts with a field of the record, e.g. "
                         + "event>logger_name");
            return null;
        }
        if (SourceTypeMeta.isMeta(field) && parts.size() > 1) {
            problems.add("'" + text + "': a stream meta attribute is read whole, never stepped into");
            return null;
        }
        if (parts.size() - 1 > MAX_HELD) {
            problems.add("'" + text + "' steps into " + (parts.size() - 1) + " held values, one inside another: "
                         + "at most " + MAX_HELD);
            return null;
        }
        final List<Step> steps = new ArrayList<>();
        for (int i = 1; i < parts.size(); i++) {
            final Step step = Step.of(parts.get(i));
            final Optional<String> problem = step.problem(i == parts.size() - 1);
            if (problem.isPresent()) {
                problems.add("'" + text + "': " + problem.get());
                return null;
            }
            steps.add(step);
        }
        return new Path(field, steps);
    }

    // A dotted field the records have only as an object holding it (flattened to four levels, so deeper names are
    // inside an object's value): the field as given, when a record's object holds a key path to it.
    private static Optional<String> deepField(final String field, final Set<String> names) {
        return names.stream()
                .filter(name -> field.length() > name.length() + 1 && field.startsWith(name + "."))
                .findFirst()
                .map(name -> field);
    }


    // --------------------------------------------------------------------------------


    /// One reading: a field of the record, then the steps into held values, each into the value before it.
    ///
    /// @param field The field (dotted), or a part's meta attribute (`meta:<Attribute>`).
    /// @param held  The steps into held values; none for the field's own value.
    public record Path(String field, List<Step> held) {

        /// @param field The field.
        /// @param held  The steps.
        public Path {
            held = List.copyOf(held);
        }

        /// @return It as written: `event>payload.logger_name`.
        public String spec() {
            final StringBuilder out = new StringBuilder(field);
            held.forEach(step -> out.append(HELD).append(step.spec()));
            return out.toString();
        }

        /// @param other A field.
        /// @return The same steps from another field.
        public Path withField(final String other) {
            return new Path(other, held);
        }

        /// @param record A record, parsed.
        /// @return The value it reads, not yet normalised; null for none.
        public String read(final Map<String, Object> record) {
            String value = top(record, field);
            for (final Step step : held) {
                if (value == null) {
                    return null;
                }
                value = HeldText.read(HeldText.parse(value), step);
            }
            return value;
        }

        /// @param record A record, parsed (flattened to dotted names, four levels deep).
        /// @param field  A field, dotted.
        /// @return Its value as text: the record's flattened field, or the object of a shorter name's value
        /// walked by the rest of the names (an object deeper than the flattening); null for none or not a
        /// scalar.
        @SuppressWarnings("unchecked")
        static String top(final Map<String, Object> record, final String field) {
            if (record.containsKey(field)) {
                return HeldText.scalar(record.get(field));
            }
            int dot = field.lastIndexOf('.');
            while (dot > 0) {
                if (record.get(field.substring(0, dot)) instanceof final Map<?, ?> object) {
                    Object at = object;
                    for (final String name : field.substring(dot + 1).split("\\.", -1)) {
                        if (!(at instanceof final Map<?, ?> map)) {
                            return null;
                        }
                        at = ((Map<String, Object>) map).get(name);
                    }
                    return HeldText.scalar(at);
                }
                dot = field.lastIndexOf('.', dot - 1);
            }
            return null;
        }
    }

    /// A step into a held value.
    ///
    /// @param format    The one format it reads ([#FORMATS]), or null for whichever the record holds.
    /// @param raw       Its text after any format: a key=value key or a syslog field whole; the names.
    /// @param names     Its names (split on `.`): a JSON object's keys, an XML document's elements below the root.
    /// @param attribute An XML attribute (`@name`, last) read from the elements the names reach, or null.
    /// @param special   [#FORMAT_STEP], [#ROOT_STEP], or null.
    public record Step(String format, String raw, List<String> names, String attribute, String special) {

        /// @param format    The format.
        /// @param raw       Its text.
        /// @param names     Its names.
        /// @param attribute An attribute, or null.
        /// @param special   A special step, or null.
        public Step {
            names = List.copyOf(names);
        }

        /// @param text A step as written: `logger_name`, `payload.logger_name`, `xml:Header.@id`, `#root`.
        /// @return The step (checked by [#problem]).
        public static Step of(final String text) {
            String raw = text.strip();
            String format = null;
            final int colon = raw.indexOf(':');
            if (colon > 0 && FORMATS.contains(raw.substring(0, colon).toLowerCase(Locale.ROOT))) {
                format = raw.substring(0, colon).toLowerCase(Locale.ROOT);
                raw = raw.substring(colon + 1).strip();
            }
            if (FORMAT_STEP.equals(raw) || ROOT_STEP.equals(raw)) {
                return new Step(format, raw, List.of(), null, raw);
            }
            final List<String> names = new ArrayList<>(List.of(raw.split("\\.", -1)));
            String attribute = null;
            if (!names.isEmpty() && names.getLast().startsWith("@")) {
                attribute = names.removeLast().substring(1);
            }
            return new Step(format, raw, names, attribute, null);
        }

        /// @return It as written.
        public String spec() {
            return format == null
                    ? raw
                    : format + ":" + raw;
        }

        /// @return Its names as an XML document's elements are matched: by local name, any prefix left out.
        public List<String> localNames() {
            return names.stream().map(name -> name.substring(name.indexOf(':') + 1)).toList();
        }

        /// @param last True if it is the reading's last step.
        /// @return What is wrong with it; empty when it is fine.
        public Optional<String> problem(final boolean last) {
            if (raw.isEmpty()) {
                return Optional.of("an empty step after '>': name what to read in the held value");
            }
            if (special != null) {
                if (!last) {
                    return Optional.of(special + " is a name, not a held value: nothing steps into it");
                }
                if (ROOT_STEP.equals(special) && format != null && !HeldText.XML.equals(format)) {
                    return Optional.of(ROOT_STEP + " is an XML document's root element: only xml: has one");
                }
                return Optional.empty();
            }
            if (raw.startsWith("#")) {
                return Optional.of("'" + raw + "' is no step: " + FORMAT_STEP + " and " + ROOT_STEP + " are");
            }
            if (names.stream().anyMatch(String::isBlank) || attribute != null && attribute.isBlank()) {
                return Optional.of("'" + raw + "' has an empty name between '.'s");
            }
            if (names.stream().anyMatch(name -> name.startsWith("@"))) {
                return Optional.of("'" + raw + "': an XML attribute (@name) is the step's last name");
            }
            if (attribute != null && format != null && !HeldText.XML.equals(format)) {
                return Optional.of("'" + raw + "': only XML has attributes (@name)");
            }
            if (HeldText.KEY_VALUE.equals(format) && HeldText.keyRegex(raw) == null) {
                return Optional.of("'" + raw + "' can't be a key of key=value pairs (letters, digits, _ . -)");
            }
            if (HeldText.SYSLOG.equals(format) && !HeldText.SYSLOG_FIELDS.contains(raw)) {
                return Optional.of("a syslog header's fields are " + String.join(", ", HeldText.SYSLOG_FIELDS)
                                   + ", not '" + raw + "'");
            }
            return Optional.empty();
        }
    }
}
