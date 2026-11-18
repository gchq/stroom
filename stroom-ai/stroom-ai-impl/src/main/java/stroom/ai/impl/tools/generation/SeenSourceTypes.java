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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.sample.SourceTypeRows;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The source types a whole translation's survey saw (kept with its mapping as `seen_source_types`, set by
/// build_translation_xslt, never by hand): a feed translated as a normal source, no source type groups, on which the
/// survey confirmed a source type field. The translation logs (INFO), once a stream for each, a value of the field
/// that none of these matches, so the user is prompted to survey the feed again ([XsltGenerator#UNSEEN_SOURCE_TYPE]).
/// Values are read as the tools read them: stripped of XML's white space, and `-`, `null`, `none` and `n/a` no value.
/// A field that is a part's meta attribute (`meta:<Attribute>`) is read once a document, by `stroom:meta()`. A
/// combination of readings ([SourceTypeReading#isCombination]) is read as its key, matched by the values (keys) seen
/// and by the registry's combination rows part by part; a key with a part of no value is never a new source type.
///
/// @param field     The source type field.
/// @param values    The values seen, matched exactly: the survey's, or the registry's rows matching a value.
/// @param patterns  Anchored patterns a value seen matches: the registry's rows matching by a pattern.
/// @param members   Items a value seen holds, read as a list: the registry's list-member rows.
/// @param separator What the list-member rows split a value on; null for none (or the default, `,`).
/// @param tuples    A combination's rows' parts, as the registry keeps them (`= Teams + *`).
public record SeenSourceTypes(String field,
                              List<String> values,
                              List<String> patterns,
                              List<String> members,
                              String separator,
                              List<String> tuples) {

    /// Fills in empty lists for null ones.
    public SeenSourceTypes {
        values = List.copyOf(Objects.requireNonNullElse(values, List.of()));
        patterns = List.copyOf(Objects.requireNonNullElse(patterns, List.of()));
        members = List.copyOf(Objects.requireNonNullElse(members, List.of()));
        tuples = List.copyOf(Objects.requireNonNullElse(tuples, List.of()));
    }

    /// The values, patterns and list items seen, no combination's rows.
    ///
    /// @param field     The source type field.
    /// @param values    The values seen, matched exactly.
    /// @param patterns  Anchored patterns a value seen matches.
    /// @param members   Items a value seen holds, read as a list.
    /// @param separator What the list-member rows split a value on; null for none.
    public SeenSourceTypes(final String field,
                           final List<String> values,
                           final List<String> patterns,
                           final List<String> members,
                           final String separator) {
        this(field, values, patterns, members, separator, List.of());
    }

    /// The values and patterns seen, no list's items.
    ///
    /// @param field    The source type field.
    /// @param values   The values seen, matched exactly.
    /// @param patterns Anchored patterns a value seen matches.
    public SeenSourceTypes(final String field, final List<String> values, final List<String> patterns) {
        this(field, values, patterns, List.of(), null);
    }

    /// @param field The source type field.
    /// @param rows  The registry's rows.
    /// @return The rows' values, patterns and list items as those seen.
    public static SeenSourceTypes ofRows(final String field, final List<SourceTypeRows.Row> rows) {
        final List<SourceTypeRows.Row> members = rows.stream()
                .filter(row -> SourceTypeRows.MEMBER.equals(row.match()))
                .toList();
        final List<SourceTypeRows.Row> tuples = rows.stream()
                .filter(row -> SourceTypeRows.TUPLE.equals(row.match()))
                .toList();
        if (!tuples.isEmpty()) {
            return new SeenSourceTypes(field, List.of(), List.of(), List.of(), tuples.getFirst().separator(),
                    tuples.stream().map(SourceTypeRows.Row::value).toList());
        }
        return new SeenSourceTypes(field,
                rows.stream().filter(row -> SourceTypeRows.VALUE.equals(row.match())).map(SourceTypeRows.Row::value)
                        .toList(),
                rows.stream().filter(row -> SourceTypeRows.PATTERN.equals(row.match()))
                        .map(SourceTypeRows.Row::value).toList(),
                members.stream().map(SourceTypeRows.Row::value).toList(),
                members.isEmpty()
                        ? null
                        : members.getFirst().separatorOrDefault());
    }

    /// @return What the list-member rows split a value on.
    public String separatorOrDefault() {
        return separator == null || separator.isEmpty()
                ? SourceTypeRows.DEFAULT_SEPARATOR
                : separator;
    }

    /// @param value A value of the field, as the tools read it ("" for none).
    /// @return True if it is one seen: none, or one a value, pattern or list item matches.
    public boolean seen(final String value) {
        if (SourceTypeReading.isCombination(field) && value != null
            && SourceTypeReading.keyParts(value).contains("")) {
            return true;
        }
        return value == null || value.isEmpty() || values.contains(value)
               || tuples.stream().anyMatch(tuple -> new SourceTypeRows.Row("", SourceTypeRows.TUPLE, tuple, separator)
                .matches(value))
               || patterns.stream().anyMatch(pattern -> new SourceTypeRows.Row("", SourceTypeRows.PATTERN, pattern)
                .matches(value))
               || SourceTypeRows.items(value, separatorOrDefault()).stream().anyMatch(members::contains);
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static SeenSourceTypes read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "SeenSourceTypes");
        if (fields == null) {
            return null;
        }
        final SeenSourceTypes seen = new SeenSourceTypes(fields.requiredString("field"), fields.strings("values"),
                fields.strings("patterns"), fields.strings("members"), fields.optionalString("separator"),
                fields.strings("tuples"));
        return fields.ok()
                ? seen
                : null;
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("field", field, null)
                .put("values", values, List.of())
                .put("patterns", patterns, List.of())
                .put("members", members, List.of())
                .put("separator", separator, null)
                .put("tuples", tuples, List.of())
                .build();
    }
}
