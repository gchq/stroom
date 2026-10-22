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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.generation.ModelReader;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A discovery index: raw records (JSON, delimited text or XML) indexed as they are, mapped dynamically by
/// Elasticsearch. Ported from the Stroom MCP server's `Discovery` model; its JSON is the server's, field for field.
///
/// @param input                 What the raw data is, as the discovery pipeline template parses it: `json`
///                              (JSONParser, the default), `delimited` (a Data Splitter on a DSParser: each column a
///                              field named by the header), `xml` (XMLParser or XMLFragmentParser).
/// @param record                xml: the element that is one record, by name, e.g. `logon` (its namespace does not
///                              matter).
/// @param timestampField        `timestamp_field`: the source field holding the event time, as the user names it: a
///                              JSON key (dotted for a nested one, e.g. `event.created`), a column name, or an XML
///                              child element (`when`, `header.time`) or attribute (`@time`). Indexed as @timestamp.
/// @param timestampChosenByUser `timestamp_chosen_by_user`: true only when the user picked timestamp_field among the
///                              candidates a needs_guidance reply listed (the records hold times in several fields):
///                              not asked again.
/// @param timestampFormat       `timestamp_format`: its Java date pattern when it is not ISO 8601 or epoch
///                              milliseconds, e.g. `dd/MM/yyyy HH:mm:ss`.
/// @param meta                  Stream meta to add to each document, index field -> meta attribute (describe_stream
///                              lists them), e.g. `{'stroom.feed': 'Feed'}`.
/// @param drop                  Top-level source fields (keys, columns, child elements or attributes) to leave out.
/// @param unpackJson            `unpack_json`: parse a string holding a JSON object into a sibling `<field>_json`
///                              object; the string is kept (true by default).
/// @param ignoreAbove           `ignore_above`: strings are keywords; longer ones are not indexed (1024 by default).
/// @param totalFieldsLimit      `total_fields_limit`: Elasticsearch's limit on mapped fields (2000 by default).
public record Discovery(String input,
                        String record,
                        String timestampField,
                        boolean timestampChosenByUser,
                        String timestampFormat,
                        Map<String, String> meta,
                        List<String> drop,
                        boolean unpackJson,
                        long ignoreAbove,
                        long totalFieldsLimit) {

    /// The kinds of raw data.
    public static final List<String> INPUTS = List.of("json", "delimited", "xml");

    /// Fills in the defaults for nulls.
    public Discovery {
        input = Objects.requireNonNullElse(input, "json");
        meta = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNullElse(meta, Map.of())));
        drop = List.copyOf(Objects.requireNonNullElse(drop, List.of()));
    }

    /// @param input          What the raw data is: `json`, `delimited` or `xml`.
    /// @param timestampField The source field holding the event time.
    /// @return A discovery with the other settings at their defaults.
    public static Discovery of(final String input, final String timestampField) {
        return new Discovery(input, null, timestampField, false, null, Map.of(), List.of(), true, 1024, 2000);
    }

    /// @param json A discovery as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws stroom.ai.impl.tools.generation.MappingValidationException if it doesn't validate.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Discovery fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("Discovery", read(reader, json, List.of()));
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static Discovery read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "Discovery");
        if (fields == null) {
            return null;
        }
        final Discovery discovery = new Discovery(
                fields.literal("input", "json", INPUTS),
                fields.optionalString("record"),
                fields.requiredString("timestamp_field"),
                fields.bool("timestamp_chosen_by_user", false),
                fields.optionalString("timestamp_format"),
                fields.stringMap("meta"),
                fields.strings("drop"),
                fields.bool("unpack_json", true),
                fields.integer("ignore_above", 1024, 1),
                fields.integer("total_fields_limit", 2000, 100));
        return fields.ok()
                ? discovery
                : null;
    }

    /// @return It as JSON, as the Python model's `model_dump` writes it (and as Jackson writes it).
    @JsonValue
    public Map<String, Object> toJson() {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("input", input);
        json.put("record", record);
        json.put("timestamp_field", timestampField);
        json.put("timestamp_chosen_by_user", timestampChosenByUser);
        json.put("timestamp_format", timestampFormat);
        json.put("meta", new LinkedHashMap<>(meta));
        json.put("drop", new ArrayList<>(drop));
        json.put("unpack_json", unpackJson);
        json.put("ignore_above", ignoreAbove);
        json.put("total_fields_limit", totalFieldsLimit);
        return json;
    }
}
