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

import stroom.ai.impl.tools.PyText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// One output value of a translation mapping: an event-logging path below Event, and where its value comes from.
/// Give exactly one of field, any_of, join, value, xpath or lookup (or meta, read as an xpath). Ported from the Stroom
/// MCP server's `FieldMapping` model.
///
/// @param path         Event-logging path below Event, e.g. `EventSource/User/Id`,
///                     `EventDetail/Authenticate/Outcome/Success`, or `.../Data` with data_name.
/// @param field        Input field: a Data Splitter data name, a JSON key (`user.name` for nested keys), an XML path
///                     relative to the record, or a name from extract.
/// @param anyOf        `any_of`: input fields tried in order; the first with a value is used.
/// @param value        A constant, e.g. `Logon`.
/// @param xpath        Advanced: an XPath expression relative to the record.
/// @param meta         A stream meta attribute, read with `stroom:meta()`; always null once validated, as it is
///                     turned into the xpath that reads it.
/// @param lookup       The value reference data holds for a key field.
/// @param dictionary   With field (the key): the value a Dictionary doc of this name gives it.
/// @param transform    Applied to the input value first: one of [#TRANSFORMS].
/// @param repeat       Write one element per value of the input instead of the first value.
/// @param scope        With for_each: `record` when the input is the record's, not the item's.
/// @param timeFormat   `time_format`: input time pattern (Java), or `epoch_ms` / `epoch_s` / `epoch_us` /
///                     `epoch_ns`.
/// @param timezone     Input time zone when the time has none, e.g. `+10:00` or `UTC`.
/// @param map          Input value -> output value, e.g. `{ok: true, fail: false}`.
/// @param defaultValue `default`: output when the field is empty (or, with map, matches no key).
/// @param dataName     `data_name`: for a path ending in Data, the Data element's Name.
/// @param dataParent   `data_parent`: with data_name, the Name of the Data element (or `/`-separated Names of the
///                     Data elements, outermost first) the Data is written in, e.g. `members` for
///                     `<Data Name="members"><Data Name="member" Value="..."/>...</Data>`: one container, shared by
///                     the entries naming it, and written only when one of them has a value. Not in the Stroom MCP
///                     server's model, which wrote every Data at its path's element.
/// @param join         Input fields whose values are joined, each one's first value that has text, in order, with
///                     separator between them, e.g. `["event", "action"]` for `User-Login`: what a TypeId telling
///                     kinds apart by two fields is written from. Not in the Stroom MCP server's model, which had no
///                     way to join fields (an xpath can't read an extraction's names).
/// @param separator    With join: what goes between the values (default `-`).
public record FieldMapping(String path,
                           String field,
                           List<String> anyOf,
                           String value,
                           String xpath,
                           String meta,
                           Lookup lookup,
                           String dictionary,
                           String transform,
                           boolean repeat,
                           String scope,
                           String timeFormat,
                           String timezone,
                           Map<String, String> map,
                           String defaultValue,
                           String dataName,
                           String dataParent,
                           List<String> join,
                           String separator) {

    /// The transforms an input may be given.
    public static final List<String> TRANSFORMS = List.of("lower", "upper", "trim", "strip_domain", "domain",
            "digits", "unescape_quotes", "strip_control");

    private static final Pattern SPILLED = Pattern.compile("[^\\w/@.-]", Pattern.UNICODE_CHARACTER_CLASS);

    /// @param json A field mapping as JSON-like values.
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate.
    public static FieldMapping fromJson(final Map<String, Object> json) {
        final ModelReader reader = new ModelReader();
        return reader.orThrow("FieldMapping", read(reader, json, List.of()));
    }

    /// @param path     A path ending in Data.
    /// @param dataName The Data element's Name.
    /// @param field    The input field.
    /// @return The entry carrying the field as that Data.
    public static FieldMapping data(final String path, final String dataName, final String field) {
        return new FieldMapping(path, field, null, null, null, null, null, null, null, false, null, null, null,
                null, null, dataName, null, null, null);
    }

    /// Reads one from JSON-like values, as pydantic validates it in the Stroom MCP server.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static FieldMapping read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "FieldMapping");
        if (fields == null) {
            return null;
        }
        final FieldMapping read = new FieldMapping(fields.requiredString("path"), fields.optionalString("field"),
                fields.optionalStrings("any_of"), fields.optionalString("value"), fields.optionalString("xpath"),
                fields.optionalString("meta"), fields.optionalModel("lookup", (value, at) -> Lookup.read(reader,
                        value, at)), fields.optionalString("dictionary"),
                fields.optionalLiteral("transform", TRANSFORMS), fields.bool("repeat", false),
                fields.optionalLiteral("scope", Condition.SCOPES), fields.optionalString("time_format"),
                fields.optionalString("timezone"), fields.optionalStringMap("map"),
                fields.optionalString("default"), fields.optionalString("data_name"),
                fields.optionalString("data_parent"), fields.optionalStrings("join"),
                fields.optionalString("separator"));
        if (!fields.ok()) {
            return null;
        }
        final String problem = read.problem();
        if (problem != null) {
            fields.valueError(problem);
            return null;
        }
        return read.meta == null
                ? read
                : read.withMetaRead();
    }

    /// The model's own check (one_source).
    ///
    /// @return Why the entry is wrong, or null.
    String problem() {
        if (meta != null) {
            // Stream meta is read as stroom:meta('Name'), which the generator, the documentation and rebuild_mapping
            // already handle as an xpath: kept in that one form (asked for by the user: values like MyHostName).
            final List<String> others = given();
            if (!others.isEmpty() || PyText.isBlank(meta) || meta.contains("'")) {
                return "'" + path + "': meta names one stream meta attribute (e.g. 'MyHostName'), and is the entry's "
                       + "only input" + (others.isEmpty()
                        ? ""
                        : " (given " + String.join(", ", others) + " too)");
            }
        }
        final Matcher spilled = SPILLED.matcher(path);
        if (spilled.find()) {
            // Seen: 'EventDetail/View/User/Id**,xpath:': the call's JSON ran the next key into the path's string, and
            // the reply said only "give exactly one of", about a field whose xpath was inside its path.
            final String before = path.substring(0, spilled.start());
            return "path " + PyText.repr(path) + " holds more than a path (from " + PyText.repr(spilled.group())
                   + " on): the next key ran into its string. Close the path's string and give the input as a key of "
                   + "its own, e.g. {\"path\": \"" + before + "\", \"xpath\": \"<the xpath>\"}";
        }
        final List<String> given = meta == null
                ? given()
                : List.of("xpath");
        if (given.size() != 1) {
            return "'" + path + "': give exactly one of field, any_of, join, value, xpath or lookup ("
                   + (given.isEmpty()
                    ? "none given"
                    : "given " + String.join(" and ", given)) + "), e.g. {\"path\": \"" + path
                   + "\", \"field\": \"<input field>\"}"
                   + (given.contains("field") && given.contains("any_of")
                    ? ": any_of alone lists the fields to try, first with a value wins"
                    : "");
        }
        final List<String> names = new ArrayList<>();
        if (field != null && !field.isEmpty()) {
            names.add(field);
        }
        if (anyOf != null) {
            names.addAll(anyOf);
        }
        if (join != null) {
            names.addAll(join);
        }
        for (final String name : names) {
            final String called = MappingFacts.functionCall(name);
            if (called != null) {
                return "'" + path + "': field " + PyText.repr(name) + " " + called;
            }
        }
        if (dictionary != null && field == null && anyOf == null) {
            return "'" + path + "': dictionary needs field (or any_of) as the key";
        }
        if (transform != null && value != null) {
            return "'" + path + "': transform applies to an input, not a constant";
        }
        if (value != null && PyText.isBlank(value)) {
            // Seen: Network/Permit/Source/Device/IPAddress given value '' for want of the address (inside a string
            // field, unextracted): an empty element in every event, which the schema refuses.
            return "'" + path + "': value '' writes an empty element, which the schema refuses: map the field that "
                   + "holds it (extract it first if it is inside another field's text), or leave the entry out";
        }
        if (dataParent != null && (dataName == null || !path.endsWith("Data")
                                   || Arrays.stream(dataParent.split("/", -1)).anyMatch(PyText::isBlank))) {
            return "'" + path + "': data_parent names the Data element(s) a Data entry is written in (e.g. "
                   + "'members', or 'a/b' for one inside another), so it goes with a path ending in Data and its "
                   + "data_name";
        }
        if (join != null && (join.size() < 2 || join.stream().anyMatch(PyText::isBlank))) {
            return "'" + path + "': join lists the fields whose values are joined, two or more, e.g. {\"path\": \""
                   + path + "\", \"join\": [\"event\", \"action\"]} for User-Login";
        }
        if (separator != null && join == null) {
            return "'" + path + "': separator goes with join, between the values it joins";
        }
        if (join != null && (repeat || isSet(timeFormat) || isSet(dictionary))) {
            return "'" + path + "': join gives one text from its fields: no repeat, time_format or dictionary with it";
        }
        if (repeat && (value != null || hasMap() || isSet(timeFormat) || lookup != null || isSet(dictionary))) {
            return "'" + path + "': repeat takes an input field, any_of or xpath, with transform at most";
        }
        return null;
    }

    private List<String> given() {
        final List<String> out = new ArrayList<>();
        if (field != null) {
            out.add("field");
        }
        if (anyOf != null) {
            out.add("any_of");
        }
        if (join != null) {
            out.add("join");
        }
        if (value != null) {
            out.add("value");
        }
        if (xpath != null) {
            out.add("xpath");
        }
        if (lookup != null) {
            out.add("lookup");
        }
        return out;
    }

    private FieldMapping withMetaRead() {
        return new FieldMapping(path, field, anyOf, value, "stroom:meta('" + PyText.strip(meta) + "')", null, lookup,
                dictionary, transform, repeat, scope, timeFormat, timezone, map, defaultValue, dataName, dataParent,
                join, separator);
    }

    /// @param defaultValue The output when the input is empty.
    /// @return This entry with that default.
    public FieldMapping withDefault(final String defaultValue) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator);
    }

    /// @param dataName The Data element's Name.
    /// @return This entry with that Name.
    public FieldMapping withDataName(final String dataName) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator);
    }

    /// @return The Data entry's Name with the Names of the Data elements it is written in, e.g. `members/member`:
    /// what tells it apart from the other Data entries at its path. Null for an entry that isn't a Data entry.
    public String dataKey() {
        return dataName == null || dataParent == null
                ? dataName
                : dataParent + "/" + dataName;
    }

    /// @param dataParent The Names of the Data elements it is written in, or null.
    /// @return This entry written in them.
    public FieldMapping withDataParent(final String dataParent) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator);
    }

    /// @return What join puts between its values.
    public String joinSeparator() {
        return separator == null
                ? "-"
                : separator;
    }

    /// @return True if the entry has a value map with at least one key (Python's truthiness of `map`).
    public boolean hasMap() {
        return map != null && !map.isEmpty();
    }

    /// @return The path without leading and trailing slashes.
    public String strippedPath() {
        return PyText.strip(path, "/");
    }

    private static boolean isSet(final String text) {
        return text != null && !text.isEmpty();
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the Python model's `model_dump` writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("path", path, null)
                .put("field", field, null)
                .put("any_of", anyOf, null)
                .put("value", value, null)
                .put("xpath", xpath, null)
                .put("meta", meta, null)
                .model("lookup", lookup, null, found -> found.toJson(mode))
                .put("dictionary", dictionary, null)
                .put("transform", transform, null)
                .put("repeat", repeat, false)
                .put("scope", scope, null)
                .put("time_format", timeFormat, null)
                .put("timezone", timezone, null)
                .put("map", map == null
                        ? null
                        : new LinkedHashMap<>(map), null)
                .put("default", defaultValue, null)
                .put("data_name", dataName, null)
                .putIfSet("data_parent", dataParent)
                .putIfSet("join", join)
                .putIfSet("separator", separator)
                .build();
    }
}
