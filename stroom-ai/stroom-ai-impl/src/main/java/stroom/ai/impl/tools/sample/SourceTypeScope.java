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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/// A build's scope over an aggregated feed: the source types it covers, told by the source type field's value and the
/// registry's rows (tried in order, the first match winning, [SourceTypeRows#first]). Kept in the survey state's
/// `source_types.scope` (`build`, `source_types`, `rows`, `by`, `at`), as the kind fields are. The tools read the
/// feed through it: a record is in the scope when its value's first matching row names a source type of the scope; a
/// record with no value, or a value no row matches (a `new` source type), is outside it.
///
/// Records are judged one by one, on the one field's value of the record as parsed, never by a pattern over the
/// whole text: a text is split into its records first ([RecordSplitter]), and those outside the scope left out.
public final class SourceTypeScope {

    /// The scope's key for the rows the scope was chosen by, as the registry had them.
    public static final String ROWS = "rows";
    /// The scope's key for its source types, by name.
    public static final String SOURCE_TYPES = "source_types";
    /// The scope's key for the build that chose it.
    public static final String BUILD = "build";
    /// What a stream read with no scope is signed by: every source type.
    public static final String ALL = "*";
    /// What a record no row matches (or with no value) is counted under.
    public static final String UNMATCHED = "(no source type)";

    // The formats whose records can be left out of a text and the rest written back as that format.
    private static final Set<String> FILTERED = Set.of(SampleProfiler.JSON_LINES, SampleProfiler.JSON_ARRAY,
            SampleProfiler.JSON_OBJECT, SampleProfiler.KEY_VALUE_FORMAT, SampleProfiler.DELIMITED);
    // More records than any sample's text holds: a text is split whole.
    private static final int SPLIT_ALL = 10_000_000;
    private static final int MAX_VALUES = 20;

    private final String build;
    private final String field;
    private final List<SourceTypeRows.Row> rows;
    private final Set<String> names;

    /// @param build The build whose scope it is, or null.
    /// @param field The source type field, dotted.
    /// @param rows  The registry's rows, in the order they are tried.
    /// @param names The source types in the scope, by name; null for every one (no scope chosen).
    public SourceTypeScope(final String build,
                           final String field,
                           final List<SourceTypeRows.Row> rows,
                           final Collection<String> names) {
        this.build = build;
        this.field = field;
        this.rows = List.copyOf(rows);
        this.names = names == null
                ? null
                : Set.copyOf(new LinkedHashSet<>(names));
    }

    /// The scope a survey's source types keep, for a build.
    ///
    /// @param sources  The survey state's `source_types`.
    /// @param build    The build reading through it, or null for whichever build chose it.
    /// @param fallback The registry's rows, used when the scope keeps none (one chosen before they were kept); with
    ///                 none, each source type matches its own name exactly.
    /// @return The scope; empty when no source type field or scope is kept, or the scope is another build's (a survey
    /// carried on from one promoted beside the feed keeps the scope of the build that promoted it).
    public static Optional<SourceTypeScope> of(final Map<String, Object> sources,
                                               final String build,
                                               final List<SourceTypeRows.Row> fallback) {
        if (sources == null) {
            return Optional.empty();
        }
        final String field = sources.get(SourceTypeProfiles.FIELD) instanceof final String text && !text.isBlank()
                ? text
                : null;
        final Map<String, Object> scope = JsonValues.mapOrEmpty(sources.get(SourceTypeProfiles.SCOPE));
        if (field == null || scope.isEmpty()) {
            return Optional.empty();
        }
        final String chosenBy = JsonValues.text(scope.get(BUILD));
        if (build != null && !build.equals(chosenBy)) {
            return Optional.empty();
        }
        final List<String> chosen = JsonValues.strings(scope.get(SOURCE_TYPES));
        List<SourceTypeRows.Row> rows = rowsOf(scope.get(ROWS));
        if (rows.isEmpty()) {
            rows = fallback == null
                    ? List.of()
                    : fallback;
        }
        if (rows.isEmpty()) {
            rows = chosen.stream().map(name -> new SourceTypeRows.Row(name, SourceTypeRows.VALUE, name)).toList();
        }
        return Optional.of(new SourceTypeScope(chosenBy, field, rows, chosen));
    }

    /// @param surveyState A survey state, or null.
    /// @param build       The build reading through it, or null.
    /// @param fallback    The registry's rows, for a scope that keeps none.
    /// @return The scope its source types keep, as [#of(Map, String, List)].
    public static Optional<SourceTypeScope> ofSurvey(final Map<String, Object> surveyState,
                                                     final String build,
                                                     final List<SourceTypeRows.Row> fallback) {
        return surveyState == null
                ? Optional.empty()
                : of(JsonValues.mapOrEmpty(surveyState.get(SourceTypeProfiles.STATE_KEY)), build, fallback);
    }

    /// @param rows Rows.
    /// @return Them as the scope keeps them: `name`, `match`, `value` each.
    public static List<Map<String, Object>> kept(final List<SourceTypeRows.Row> rows) {
        final List<Map<String, Object>> out = new ArrayList<>();
        rows.forEach(row -> out.add(SampleProfiler.map("name", row.name(), "match", row.match(), "value",
                row.value())));
        return out;
    }

    /// @param kept Rows as the scope (or the registry) keeps them.
    /// @return The rows, in order.
    public static List<SourceTypeRows.Row> rowsOf(final Object kept) {
        final List<SourceTypeRows.Row> out = new ArrayList<>();
        JsonValues.maps(kept).forEach(row -> out.add(new SourceTypeRows.Row(JsonValues.text(row.get("name")),
                JsonValues.text(row.get("match")), JsonValues.text(row.get("value")))));
        return out;
    }

    /// @return The build that chose the scope, or null.
    public String build() {
        return build;
    }

    /// @return The source type field.
    public String field() {
        return field;
    }

    /// @return The registry's rows the scope is judged by.
    public List<SourceTypeRows.Row> rows() {
        return rows;
    }

    /// @return The source types in the scope; null for every one.
    public Set<String> names() {
        return names;
    }

    /// @return The source types in the scope, as said to the user: `PegaDB, PegaSecurityEvent`.
    public String said() {
        return names == null
                ? "every source type"
                : String.join(", ", names.stream().sorted(ToolText::compare).toList());
    }

    /// @param parsed A record, parsed (flattened to dotted names), or null.
    /// @return Its source type field's value, or null when it has none.
    public String value(final Map<String, Object> parsed) {
        return SourceTypeFields.valueOf(parsed, field);
    }

    /// @param parsed A record, parsed, or null.
    /// @return The source type its value's first matching row names; empty for a record with no value, or a value
    /// no row matches.
    public Optional<String> sourceType(final Map<String, Object> parsed) {
        return SourceTypeRows.first(rows, value(parsed)).map(SourceTypeRows.Row::name);
    }

    /// @param parsed A record, parsed, or null.
    /// @return True if it is in the scope: with no scope chosen, every record; else one whose source type is one of
    /// the scope's.
    public boolean inScope(final Map<String, Object> parsed) {
        if (names == null) {
            return true;
        }
        return sourceType(parsed).filter(names::contains).isPresent();
    }

    /// The records of a stream still to read for the scope, where the stream was read before for some source types
    /// (its signature): those in the scope that it wasn't read for.
    ///
    /// @param signed The source types the stream was read for ([#ALL] for every one); empty when it wasn't read.
    /// @return Whether a record is to be read now.
    public Predicate<Map<String, Object>> notReadFor(final Collection<String> signed) {
        if (signed.contains(ALL)) {
            return parsed -> false;
        }
        return parsed -> {
            if (!inScope(parsed)) {
                return false;
            }
            final Optional<String> type = sourceType(parsed);
            return type.isEmpty() || !signed.contains(type.get());
        };
    }

    /// @param signed The source types a stream was read for ([#ALL] for every one).
    /// @return True if reading it for them read every record of this scope: nothing is left to read in it.
    public boolean covers(final Collection<String> signed) {
        return signed.contains(ALL) || names != null && signed.containsAll(names);
    }

    /// @return What a stream read through this scope is signed by: its source types, or [#ALL].
    public List<String> signature() {
        return names == null
                ? List.of(ALL)
                : names.stream().sorted(ToolText::compare).toList();
    }

    /// Whether a shape the survey kept is one of the scope's: by the source type field's values its records had
    /// (`source_values`), else by its examples' records parsed; a shape whose source can't be told is kept.
    ///
    /// @param shape A shape, as the survey state keeps it.
    /// @return True if its records are (some of them) the scope's.
    public boolean shapeInScope(final Map<?, ?> shape) {
        if (names == null) {
            return true;
        }
        final List<String> values = JsonValues.strings(shape.get(Shapes.SOURCE_VALUES));
        if (!values.isEmpty()) {
            return values.stream().anyMatch(value -> SourceTypeRows.first(rows, value)
                    .map(SourceTypeRows.Row::name)
                    .filter(names::contains)
                    .isPresent());
        }
        boolean told = false;
        for (final Map<String, Object> example : JsonValues.maps(shape.get("examples"))) {
            final Map<String, Object> parsed = parsedRecord(JsonValues.text(example.get("text")));
            if (parsed == null) {
                continue;
            }
            told = true;
            if (inScope(parsed)) {
                return true;
            }
        }
        return !told;
    }

    /// A text's records outside the scope left out, the rest written back in the text's format (a header line kept);
    /// for a format whose records can't be written back so (XML, text), the text as it is.
    ///
    /// @param text A sample's text.
    /// @return The text of the scope's records, how many were kept, and those left out by source type.
    public Filtered filter(final String text) {
        final RecordSplitter.Chunk chunk = split(text);
        if (names == null || chunk == null || !FILTERED.contains(chunk.format())
            || (SampleProfiler.DELIMITED.equals(chunk.format()) && chunk.header() == null)) {
            return new Filtered(text, -1, Map.of(), false);
        }
        final List<String> kept = new ArrayList<>();
        final Map<String, Integer> passedOver = new TreeMap<>(ToolText::compare);
        for (int index = 0; index < chunk.records().size(); index++) {
            final Map<String, Object> parsed = chunk.parsedAt(index);
            if (inScope(parsed)) {
                kept.add(chunk.records().get(index));
            } else {
                passedOver.merge(sourceType(parsed).orElse(UNMATCHED), 1, Integer::sum);
            }
        }
        if (passedOver.isEmpty()) {
            return new Filtered(text, kept.size(), Map.of(), true);
        }
        return new Filtered(written(chunk, kept, text), kept.size(), passedOver, true);
    }

    /// The scope's share of the records the survey read: the records of its source types (by the rows) over all.
    ///
    /// @param sources The survey state's `source_types`.
    /// @return `source_types`, `records_read` (the scope's), `of_records_read` and `share_percent` (null when the
    /// survey has counted none), and `rests_on`.
    public Map<String, Object> share(final Map<String, Object> sources) {
        final Map<String, Long> counts = SourceTypeProfiles.valueCounts(sources == null
                ? Map.of()
                : sources);
        final long total = sources == null || !(sources.get("records") instanceof final Number n)
                ? counts.values().stream().mapToLong(Long::longValue).sum()
                : n.longValue();
        long inScope = 0;
        for (final Map.Entry<String, Long> entry : counts.entrySet()) {
            final boolean in = names == null || SourceTypeRows.first(rows, entry.getKey())
                    .map(SourceTypeRows.Row::name)
                    .filter(names::contains)
                    .isPresent();
            if (in) {
                inScope += entry.getValue();
            }
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put(SOURCE_TYPES, names == null
                ? List.of(ALL)
                : names.stream().sorted(ToolText::compare).toList());
        out.put("records_read", inScope);
        out.put("of_records_read", total);
        out.put("share_percent", total == 0
                ? null
                : ToolText.round(100.0 * inScope / total, 1));
        out.put("rests_on", total == 0
                ? "the survey has counted no records by source type yet"
                : "the " + total + " records the survey counted by source type: an estimate");
        return out;
    }

    /// @param sources The survey state's `source_types`.
    /// @return The scope and its share, in a sentence: `build 'b' covers PegaDB (about 40% of the records read)`.
    public String shareSaid(final Map<String, Object> sources) {
        final Object percent = share(sources).get("share_percent");
        return "build '" + build + "' covers " + said() + (percent == null
                ? ""
                : " (about " + percent + "% of the feed's records read)");
    }

    // The text split whole into its records; null when it can't be.
    private static RecordSplitter.Chunk split(final String text) {
        RecordSplitter.Chunk chunk;
        try {
            chunk = RecordSplitter.split(text, SPLIT_ALL, false);
        } catch (final RuntimeException e) {
            try {
                // Cut short: its last record cut off.
                chunk = RecordSplitter.split(text, SPLIT_ALL, true);
            } catch (final RuntimeException again) {
                chunk = null;
            }
        }
        if (chunk == null || chunk.records().isEmpty()) {
            // Too few lines for the format to be told (one JSON line reads as a delimited header): JSON lines, where
            // every line is a JSON object.
            final RecordSplitter.Chunk lines = jsonLines(text);
            return lines != null
                    ? lines
                    : chunk;
        }
        return chunk;
    }

    // Every line of the text a JSON object: its records; null otherwise.
    private static RecordSplitter.Chunk jsonLines(final String text) {
        final List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            return null;
        }
        final List<Map<String, Object>> parsed = new ArrayList<>();
        for (final String line : lines) {
            final Map<String, Object> record = parsedRecord(line);
            if (record == null) {
                return null;
            }
            parsed.add(record);
        }
        return new RecordSplitter.Chunk(SampleProfiler.JSON_LINES, lines, parsed, null, null, List.of());
    }

    // The records kept, written back as the format they were read from.
    private static String written(final RecordSplitter.Chunk chunk, final List<String> kept, final String text) {
        if (SampleProfiler.JSON_ARRAY.equals(chunk.format())) {
            return "[" + String.join(",\n", kept) + "]\n";
        }
        if (SampleProfiler.JSON_OBJECT.equals(chunk.format())) {
            final Map.Entry<String, List<Object>> wrapped = SampleProfiler.wrappedRecords(ToolText.strip(text));
            final String key = wrapped == null
                    ? "Records"
                    : wrapped.getKey();
            return "{" + ToolJson.dumps(key) + ": [" + String.join(",\n", kept) + "]}\n";
        }
        final StringBuilder out = new StringBuilder();
        if (chunk.header() != null) {
            out.append(chunk.header()).append('\n');
        }
        kept.forEach(record -> out.append(record).append('\n'));
        return out.toString();
    }

    // An example record's fields, where it is JSON; null otherwise.
    private static Map<String, Object> parsedRecord(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            final Object json = SampleProfiler.loadsDecoded(ToolText.strip(text));
            return json instanceof Map
                    ? SampleProfiler.flatten(json)
                    : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /// @param values The values kept so far, or null.
    /// @param value  A value seen.
    /// @return The values with it added, at most twenty.
    public static List<String> withValue(final List<String> values, final String value) {
        final List<String> out = values == null
                ? new ArrayList<>()
                : values;
        if (value != null && !out.contains(value) && out.size() < MAX_VALUES) {
            out.add(value);
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// A text read through the scope.
    ///
    /// @param text       The scope's records, written back as the text's format (the text as it is when not
    ///                   filtered).
    /// @param kept       The records kept; -1 when the text wasn't split.
    /// @param passedOver The records left out, by source type ([#UNMATCHED] for those of none).
    /// @param filtered   True when the text was read record by record; false when its format can't be.
    public record Filtered(String text, int kept, Map<String, Integer> passedOver, boolean filtered) {

        /// @return How many records were left out.
        public int left() {
            return passedOver.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
