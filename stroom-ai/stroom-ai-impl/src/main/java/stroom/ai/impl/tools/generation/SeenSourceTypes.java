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

import stroom.ai.impl.tools.sample.SourceTypeRows;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The source types a whole translation's survey saw (kept with its mapping as `seen_source_types`, set by
/// build_translation_xslt, never by hand): a feed translated as a normal source, no source type groups, on which the
/// survey confirmed a source type field. The translation logs (INFO), once a stream for each, a value of the field
/// that none of these matches, so the user is prompted to survey the feed again ([XsltGenerator#UNSEEN_SOURCE_TYPE]).
/// Values are read as the tools read them: stripped of XML's white space, and `-`, `null`, `none` and `n/a` no value.
///
/// @param field    The source type field.
/// @param values   The values seen, matched exactly: the survey's, or the registry's rows matching a value.
/// @param patterns Anchored patterns a value seen matches: the registry's rows matching by a pattern.
public record SeenSourceTypes(String field, List<String> values, List<String> patterns) {

    /// Fills in empty lists for null ones.
    public SeenSourceTypes {
        values = List.copyOf(Objects.requireNonNullElse(values, List.of()));
        patterns = List.copyOf(Objects.requireNonNullElse(patterns, List.of()));
    }

    /// @param field The source type field.
    /// @param rows  The registry's rows.
    /// @return The rows' values and patterns as those seen.
    public static SeenSourceTypes ofRows(final String field, final List<SourceTypeRows.Row> rows) {
        return new SeenSourceTypes(field,
                rows.stream().filter(row -> SourceTypeRows.VALUE.equals(row.match())).map(SourceTypeRows.Row::value)
                        .toList(),
                rows.stream().filter(row -> SourceTypeRows.PATTERN.equals(row.match()))
                        .map(SourceTypeRows.Row::value).toList());
    }

    /// @param value A value of the field, as the tools read it ("" for none).
    /// @return True if it is one seen: none, or one a value or pattern matches.
    public boolean seen(final String value) {
        return value == null || value.isEmpty() || values.contains(value)
               || patterns.stream().anyMatch(pattern -> new SourceTypeRows.Row("", SourceTypeRows.PATTERN, pattern)
                .matches(value));
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
                fields.strings("patterns"));
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
                .build();
    }
}
