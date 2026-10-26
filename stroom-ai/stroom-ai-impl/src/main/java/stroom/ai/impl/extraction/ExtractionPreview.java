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

package stroom.ai.impl.extraction;

import stroom.util.shared.NullSafe;

import java.io.StringReader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// What a draft search extraction gives a dashboard table's events, as the user is shown it before it is written:
/// a table of the values it extracts from sample events, and what to look at before using it.
///
/// - Refused: a column of the table that uses a field the extraction doesn't give. Turning extraction on fills
///   every value in the table from it, the indexed values included, so the column would be blank.
/// - Flagged: a field the data source has too (its extracted value replaces the indexed one in the table, while
///   queries still filter on the indexed value); a field found in few of the events; values that aren't of the
///   field's type; extracted values that differ from the indexed ones the table shows now; errors stepping.
public final class ExtractionPreview {

    /// The most characters of a value shown in the preview table.
    static final int MAX_VALUE_CHARS = 60;

    private static final Pattern FIELD_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SINGLE_FIELD = Pattern.compile("\\s*\\$\\{([^}]+)}\\s*");

    private ExtractionPreview() {
        // Static only.
    }

    /// Previews a draft extraction.
    ///
    /// @param fields           The fields it gives, besides StreamId and EventId.
    /// @param samples          The events it was tried on.
    /// @param columns          The table's columns: each one's expression, by column name.
    /// @param dataSourceFields The fields of the table's data source, or null if they can't be read.
    /// @return The preview.
    public static Preview preview(final List<ExtractionField> fields,
                                  final List<Sample> samples,
                                  final Map<String, String> columns,
                                  final List<String> dataSourceFields) {
        final List<String> problems = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final Set<String> given = new LinkedHashSet<>();
        ExtractionField.RESERVED.forEach(name -> given.add(name.toLowerCase(Locale.ROOT)));
        fields.forEach(field -> given.add(field.name().toLowerCase(Locale.ROOT)));

        // Columns whose fields the extraction wouldn't give would be blank.
        NullSafe.map(columns).forEach((column, expression) -> {
            for (final String field : fieldsOf(expression)) {
                if (!given.contains(field.toLowerCase(Locale.ROOT))) {
                    problems.add("Column '" + column + "' uses " + field + ", which the extraction doesn't give: "
                                 + "turning extraction on fills every value in the table from it, so the column "
                                 + "would be blank. Give " + field + " too, with the XPath of its value (e.g. from "
                                 + "the XSLT that indexes it, or the table's extraction XSLT now, with "
                                 + "read_document).");
                }
            }
        });

        // Fields the data source has too.
        if (dataSourceFields != null) {
            final List<String> indexed = fields.stream()
                    .map(ExtractionField::name)
                    .filter(name -> dataSourceFields.stream().anyMatch(name::equalsIgnoreCase))
                    .toList();
            if (!indexed.isEmpty()) {
                notes.add("The data source has " + quoted(indexed) + " too: in the table, the extracted values "
                          + "replace the indexed ones, while the query still filters on the indexed values. For a "
                          + "field the table shows now, that is as wanted if its values agree (compared below); "
                          + "else, name the field otherwise.");
            }
        }

        final List<Sample> stepped = samples.stream().filter(sample -> sample.extracted() != null).toList();
        if (stepped.isEmpty()) {
            problems.add("None of the events could be stepped through the extraction, so it can't be previewed."
                         + errorsNote(samples));
            return new Preview(table(fields, samples), problems, notes, 0);
        }
        for (final ExtractionField field : fields) {
            final long found = stepped.stream()
                    .filter(sample -> !NullSafe.string(sample.extracted().get(field.name())).isBlank())
                    .count();
            if (found == 0) {
                notes.add("'" + field.name() + "' was found in none of the " + stepped.size() + " event(s): check "
                          + "its XPath (" + field.xpath() + ") against an event, e.g. with fetch_events and "
                          + "xpath.");
            } else if (found < stepped.size()) {
                notes.add("'" + field.name() + "' was found in " + found + " of " + stepped.size()
                          + " event(s).");
            }
            typeNote(field, stepped).ifPresent(notes::add);
            comparisonNote(field, columns, stepped).ifPresent(notes::add);
        }
        final String errors = errorsNote(samples);
        if (!errors.isEmpty()) {
            notes.add(errors.strip());
        }
        return new Preview(table(fields, samples), problems, notes, stepped.size());
    }

    /// The fields an expression names, e.g. `UserId` in `upperCase(${UserId})`, but not an annotation's.
    ///
    /// @param expression A column's expression.
    /// @return The fields it names.
    static Set<String> fieldsOf(final String expression) {
        final Set<String> fields = new LinkedHashSet<>();
        final Matcher matcher = FIELD_REFERENCE.matcher(NullSafe.string(expression));
        while (matcher.find()) {
            final String field = matcher.group(1).strip();
            if (!field.startsWith("annotation:")) {
                fields.add(field);
            }
        }
        return fields;
    }

    /// Reads the values an extraction gave an event, from its XSLT's output when stepped: each `data` element's
    /// `name` and `value`, the first given for each name, as Stroom's search result output reads them.
    ///
    /// @param output The XSLT's output, `records:2` XML.
    /// @return The values, by name; none if there are none, or the output can't be read.
    public static Map<String, String> values(final String output) {
        final Map<String, String> values = new LinkedHashMap<>();
        if (NullSafe.isBlankString(output)) {
            return values;
        }
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            final XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(output));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT && "data".equals(reader.getLocalName())) {
                        final String name = NullSafe.get(reader.getAttributeValue(null, "name"), String::strip);
                        final String value = NullSafe.get(reader.getAttributeValue(null, "value"), String::strip);
                        if (!NullSafe.isBlankString(name) && !NullSafe.isBlankString(value)) {
                            values.putIfAbsent(name, value);
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException e) {
            return Map.of();
        }
        return values;
    }

    // Values that aren't of the field's type: numbers that aren't, dates Stroom can't read.
    private static Optional<String> typeNote(final ExtractionField field, final List<Sample> stepped) {
        if (ExtractionField.TEXT.equals(field.type())) {
            return Optional.empty();
        }
        final List<String> wrong = stepped.stream()
                .map(sample -> sample.extracted().get(field.name()))
                .filter(value -> !NullSafe.isBlankString(value))
                .filter(value -> ExtractionField.NUMBER.equals(field.type())
                        ? !isNumber(value)
                        : !isDate(value))
                .toList();
        if (wrong.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("'" + field.name() + "' is a " + field.type() + ", but " + wrong.size() + " of its "
                           + "values aren't, e.g. '" + shorten(wrong.getFirst()) + "'"
                           + (ExtractionField.DATE.equals(field.type())
                ? " (Stroom reads dates as ISO 8601, e.g. 2026-10-27T09:30:00.000Z, or milliseconds since 1970)"
                : "") + ": give it another type, or an XPath that gives one.");
    }

    // For a field a column shows as it is (`${field}`), whether the extracted values agree with the indexed ones
    // the table shows now.
    private static Optional<String> comparisonNote(final ExtractionField field,
                                                   final Map<String, String> columns,
                                                   final List<Sample> stepped) {
        final String column = NullSafe.map(columns).entrySet().stream()
                .filter(entry -> {
                    final Matcher matcher = SINGLE_FIELD.matcher(NullSafe.string(entry.getValue()));
                    return matcher.matches() && matcher.group(1).strip().equalsIgnoreCase(field.name());
                })
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (column == null || ExtractionField.DATE.equals(field.type())) {
            // A date is shown formatted, so isn't compared.
            return Optional.empty();
        }
        int compared = 0;
        final List<String> differ = new ArrayList<>();
        for (final Sample sample : stepped) {
            final String indexed = sample.columnValues().get(column);
            if (indexed == null) {
                continue;
            }
            compared++;
            final String extracted = NullSafe.string(sample.extracted().get(field.name()));
            if (!agree(indexed, extracted, field.type())) {
                differ.add("'" + shorten(indexed) + "' indexed, '" + shorten(extracted) + "' extracted");
            }
        }
        if (compared == 0) {
            return Optional.empty();
        }
        return Optional.of(differ.isEmpty()
                ? "'" + field.name() + "' agrees with the indexed values column '" + column + "' shows now, in "
                  + "all " + compared + " event(s)."
                : "'" + field.name() + "' differs from the indexed values column '" + column + "' shows now, in "
                  + differ.size() + " of " + compared + " event(s), e.g. " + differ.getFirst() + ": with "
                  + "extraction on, the column would show the extracted values. Check its XPath.");
    }

    private static boolean agree(final String indexed, final String extracted, final String type) {
        if (ExtractionField.NUMBER.equals(type) && isNumber(indexed) && isNumber(extracted)) {
            return Double.compare(Double.parseDouble(indexed.strip().replace(",", "")),
                    Double.parseDouble(extracted.strip())) == 0;
        }
        return indexed.strip().equals(extracted.strip());
    }

    private static boolean isNumber(final String value) {
        try {
            Double.parseDouble(value.strip().replace(",", ""));
            return true;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDate(final String value) {
        final String text = value.strip();
        if (text.chars().allMatch(Character::isDigit)) {
            return true;
        }
        try {
            Instant.parse(text);
            return true;
        } catch (final DateTimeParseException e) {
            try {
                OffsetDateTime.parse(text);
                return true;
            } catch (final DateTimeParseException e2) {
                return false;
            }
        }
    }

    private static String errorsNote(final List<Sample> samples) {
        final List<String> errors = samples.stream()
                .flatMap(sample -> NullSafe.list(sample.errors()).stream()
                        .map(error -> sample.streamId() + ":" + sample.eventId() + ": " + error))
                .distinct()
                .limit(5)
                .toList();
        return errors.isEmpty()
                ? ""
                : " Errors stepping:\n  - " + String.join("\n  - ", errors);
    }

    // A Markdown table of the values extracted from each event.
    private static String table(final List<ExtractionField> fields, final List<Sample> samples) {
        final StringBuilder sb = new StringBuilder("| StreamId | EventId |");
        fields.forEach(field -> sb.append(' ').append(cell(field.name())).append(" |"));
        sb.append("\n|---|---|");
        fields.forEach(field -> sb.append("---|"));
        for (final Sample sample : samples) {
            sb.append("\n| ").append(sample.streamId()).append(" | ").append(sample.eventId()).append(" |");
            for (final ExtractionField field : fields) {
                final String value = sample.extracted() == null
                        ? "(not stepped)"
                        : NullSafe.string(sample.extracted().get(field.name()));
                sb.append(' ').append(cell(shorten(value))).append(" |");
            }
        }
        return sb.toString();
    }

    private static String cell(final String text) {
        return NullSafe.string(text).replace("|", "\\|").replace('\n', ' ');
    }

    private static String shorten(final String text) {
        final String value = NullSafe.string(text);
        return value.length() > MAX_VALUE_CHARS
                ? value.substring(0, MAX_VALUE_CHARS - 1) + "…"
                : value;
    }

    private static String quoted(final List<String> names) {
        return String.join(", ", names.stream().map(name -> "'" + name + "'").toList());
    }


    // --------------------------------------------------------------------------------


    /// One event a draft extraction was tried on.
    ///
    /// @param streamId     Its stream.
    /// @param eventId      Its id in the stream, from 1.
    /// @param columnValues The values the table shows for it now, by column name.
    /// @param extracted    The values the extraction gave it, by field name; null if it couldn't be stepped.
    /// @param errors       Errors stepping it.
    public record Sample(long streamId,
                         long eventId,
                         Map<String, String> columnValues,
                         Map<String, String> extracted,
                         List<String> errors) {

        public Sample {
            columnValues = NullSafe.map(columnValues);
            errors = NullSafe.list(errors);
            Objects.requireNonNull(columnValues);
        }
    }

    /// A draft extraction's preview.
    ///
    /// @param table    A Markdown table of the values extracted from each event.
    /// @param problems Why it can't be used as it is; none if it can.
    /// @param notes    What to look at before using it.
    /// @param stepped  How many events it was tried on.
    public record Preview(String table, List<String> problems, List<String> notes, int stepped) {

    }
}
