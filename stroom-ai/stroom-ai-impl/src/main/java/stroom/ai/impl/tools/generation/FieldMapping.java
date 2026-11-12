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

import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// One output value of a translation mapping: an event-logging path below Event, and where its value comes from.
/// Give exactly one of field, any_of, join, value, xpath or lookup (or meta, read as an xpath).
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
///                     `epoch_ns`, or `windows_filetime` (100 ns ticks since 1601, as Active Directory's
///                     lastLogon).
/// @param timezone     Input time zone when the time has none, e.g. `+10:00` or `UTC`.
/// @param map          Input value -> output value, e.g. `{ok: true, fail: false}`.
/// @param defaultValue `default`: output when the field is empty (or, with map, matches no key).
/// @param dataName     `data_name`: for a path ending in Data, the Data element's Name.
/// @param dataParent   `data_parent`: with data_name, the Name of the Data element (or `/`-separated Names of the
///                     Data elements, outermost first) the Data is written in, e.g. `members` for
///                     `<Data Name="members"><Data Name="member" Value="..."/>...</Data>`: one container, shared by
///                     the entries naming it, and written only when one of them has a value. Without it, a Data
///                     is written at its path's element.
/// @param join         Input fields whose values are joined, each one's first value that has text, in order, with
///                     separator between them, e.g. `["event", "action"]` for `User-Login`: what a TypeId telling
///                     kinds apart by two fields is written from. The only way to join fields, as an xpath can't
///                     read an extraction's names.
/// @param separator    With join: what goes between the values (default `-`).
/// @param rollover     With time_format: the time is a time of day on a date that doesn't move on by itself (a header's
///                     date joined with each line's time): a record's time earlier than the one before it on the same
///                     date is the next day, a day added for each such step back, held across the stream with
///                     `stroom:put` (Stroom's own completion of a dateless time moves it back a month).
/// @param timeFallback `time_fallback`: with time_format, a stream meta time (`ReceivedTime`, `CreatedTime`) written
///                     for a record whose time doesn't parse, with a warning naming the record, in place of an empty
///                     time the schema refuses; only as the user agreed, as it is an approximation.
/// @param metaFallback `meta_fallback`: stream meta attributes (e.g. `RemoteAddress`) tried in order, the first with
///                     a value written, for a record whose input gives none (blank, or one of nil_values): asked for
///                     by the user, for a required element (EventSource/Device) whose only source holds a stand-in
///                     for none, which left out would leave the element empty, which the schema refuses. Null for
///                     none.
/// @param zoneSource   `timezone` given as an object: the field or stream meta attribute each record's zone is read
///                     from, with a map and a default (see [ZoneSource]); null for a fixed `timezone` or none.
/// @param group        A repeated group (`for_each`, `as` and `fields`, see [FieldGroup]): the element at path written
///                     once per item, each holding its fields. A group entry has no input of its own. Null for an
///                     entry that isn't one.
/// @param item         In a group's fields: the item read. `""` (given as `item: true`) for the group's own item,
///                     else the name (`as`) of an outer group's; with field, a key of that item, alone the item
///                     itself. Null for an entry reading a key of its own group's item (or, outside groups, any
///                     entry).
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
                           String separator,
                           boolean rollover,
                           String timeFallback,
                           ZoneSource zoneSource,
                           List<String> metaFallback,
                           FieldGroup group,
                           String item) {

    /// The transforms an input may be given.
    public static final List<String> TRANSFORMS = List.of("lower", "upper", "trim", "strip_domain", "domain",
            "digits", "unescape_quotes", "strip_control");

    // What a group entry can't have: an input or conversion of its own.
    private static final List<String> GROUP_REFUSES = List.of("field", "any_of", "join", "value", "xpath", "meta",
            "lookup", "dictionary", "transform", "repeat", "scope", "time_format", "timezone", "map", "default",
            "data_parent", "separator", "rollover", "time_fallback", "meta_fallback", "item");
    // An Event's own children: a group's field path starting with one is a path from Event, not from the group.
    private static final Set<String> EVENT_SECTIONS = Set.of("EventTime", "EventSource", "EventDetail", "EventChain",
            "Meta", "Followers");
    // A stream meta attribute's name, as stroom:meta reads it.
    private static final Pattern META_NAME = Pattern.compile("[A-Za-z][\\w-]*");
    private static final Pattern SPILLED = Pattern.compile("[^\\w/@.-]", Pattern.UNICODE_CHARACTER_CLASS);

    /// @param json A field mapping as JSON-like values.
    /// @return It validated, with the model's own checks.
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
                null, null, dataName, null, null, null, false, null, null, null, null, null);
    }

    /// Reads one from JSON-like values: lax types, defaults and the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static FieldMapping read(final ModelReader reader, final Object input, final List<Object> loc) {
        return read(reader, input, loc, List.of());
    }

    // One in the groups given (outermost first, each with its full path), or in none.
    private static FieldMapping read(final ModelReader reader,
                                     final Object input,
                                     final List<Object> loc,
                                     final List<Enclosing> enclosing) {
        final ModelReader.Fields fields = reader.fields(input, loc, "FieldMapping");
        if (fields == null) {
            return null;
        }
        if (fields.input().get("choose_from") instanceof final List<?> choices) {
            // A time the draft put to the agent, not yet chosen (asked for by the user: seen, the draft's first kept
            // without a word).
            fields.valueError("'" + fields.input().get("path") + "' still has choose_from " + ToolText.repr(
                    choices.stream().map(String::valueOf).toList()) + ": the draft couldn't tell which is the event's "
                              + "time (its time_candidates say what tells them apart). Choose one from that and what "
                              + "you know of the source, set field to it, remove choose_from, and say which you chose "
                              + "and why; ask the user if unsure");
            return null;
        }
        if (fields.has("for_each")) {
            return readGroup(reader, fields, enclosing);
        }
        final String item = readItem(fields);
        final FieldMapping read = new FieldMapping(fields.requiredString("path"), fields.optionalString("field"),
                fields.optionalStrings("any_of"), fields.optionalString("value"), fields.optionalString("xpath"),
                fields.optionalString("meta"), fields.optionalModel("lookup", (value, at) -> Lookup.read(reader,
                        value, at)), fields.optionalString("dictionary"),
                fields.optionalLiteral("transform", TRANSFORMS), fields.bool("repeat", false),
                fields.optionalLiteral("scope", Condition.SCOPES), fields.optionalString("time_format"),
                fields.input().get("timezone") instanceof Map
                        ? null
                        : fields.optionalString("timezone"), fields.optionalStringMap("map"),
                fields.optionalString("default"), fields.optionalString("data_name"),
                fields.optionalString("data_parent"), fields.optionalStrings("join"),
                fields.optionalString("separator"), fields.bool("rollover", false),
                fields.optionalString("time_fallback"), fields.input().get("timezone") instanceof Map
                        ? fields.optionalModel("timezone", (value, at) -> ZoneSource.read(reader, value, at))
                        : null, fields.optionalStrings("meta_fallback"), null, item);
        if (!fields.ok()) {
            return null;
        }
        String problem = read.problem();
        if (problem == null) {
            problem = read.groupProblem(enclosing);
        }
        if (problem != null) {
            fields.valueError(problem);
            return null;
        }
        final FieldMapping placed = enclosing.isEmpty()
                ? read
                : read.withPath(enclosing.getLast().relative(read.strippedPath()));
        return placed.meta == null
                ? placed
                : placed.withMetaRead();
    }

    // item: true (the group's own item), a name (an outer group's), or none.
    private static String readItem(final ModelReader.Fields fields) {
        final Object given = fields.input().get("item");
        if (given == null || Boolean.FALSE.equals(given)) {
            return null;
        }
        if (Boolean.TRUE.equals(given)) {
            return "";
        }
        if (given instanceof final String name && ItemSource.NAME.matcher(name).matches()) {
            return name;
        }
        fields.valueError("item is true (the group's own item) or the name (as) of an outer group, not "
                          + ToolText.repr(given));
        return null;
    }

    // A group entry: path (data_name for a Data group), for_each, as and fields; no input of its own.
    private static FieldMapping readGroup(final ModelReader reader,
                                          final ModelReader.Fields fields,
                                          final List<Enclosing> enclosing) {
        final String path = fields.requiredString("path");
        final String dataName = fields.optionalString("data_name");
        final ItemSource source = fields.optionalModel("for_each", (value, at) -> ItemSource.read(reader, value,
                at));
        final String as = fields.optionalString("as");
        if (!fields.ok() || source == null) {
            return null;
        }
        final String where = "'" + path + "'";
        final List<String> extra = GROUP_REFUSES.stream().filter(fields::has).toList();
        if (!extra.isEmpty()) {
            fields.valueError(where + ": a group (for_each) writes its element once per item, each holding its "
                              + "fields; it has no input of its own, so no " + String.join(", ", extra) + " (those "
                              + "go on its fields)");
            return null;
        }
        if (enclosing.size() >= FieldGroup.MAX_DEPTH) {
            fields.valueError(where + ": groups nest " + FieldGroup.MAX_DEPTH + " deep at most; this one is deeper. "
                              + "Map the deeper levels as Data, or leave them out");
            return null;
        }
        if (as != null && (!ItemSource.NAME.matcher(as).matches()
                           || enclosing.stream().anyMatch(outer -> as.equals(outer.group().itemName())))) {
            fields.valueError(where + ": as names the group's item, for the fields of groups inside it: a plain name "
                              + "(letters, digits, _ or -) no group it is in has, not " + ToolText.repr(as));
            return null;
        }
        final String sourceProblem = itemProblem(source.item(), source.readsRecord(), source.fields(),
                source.item() == null, enclosing);
        if (sourceProblem != null) {
            fields.valueError(where + ": for_each " + sourceProblem);
            return null;
        }
        final String stripped = ToolText.strip(path, "/");
        final String relative = enclosing.isEmpty()
                ? stripped
                : enclosing.getLast().relative(stripped);
        final String full = enclosing.isEmpty()
                ? stripped
                : enclosing.getLast().path() + "/" + relative;
        final boolean dataPath = "Data".equals(relative) || relative.endsWith("/Data");
        if ((dataName != null || dataPath) && !(dataName != null && dataPath)) {
            fields.valueError(where + ": a Data group's path ends in Data, with data_name the Name of the Data written "
                              + "for each item, e.g. {\"path\": \"EventDetail/Send/Data\", \"data_name\": "
                              + "\"recipient\", \"for_each\": {...}, \"fields\": [{\"path\": \"Data\", \"data_name\": "
                              + "\"id\", \"item\": true}]}");
            return null;
        }
        if (!enclosing.isEmpty() && enclosing.getLast().data() && !"Data".equals(relative)) {
            fields.valueError(where + ": a Data group holds Data entries only (path Data, with data_name)");
            return null;
        }
        final FieldGroup header = new FieldGroup(source, as, List.of());
        final List<Enclosing> inner = new ArrayList<>(enclosing);
        inner.add(new Enclosing(full, header, dataPath));
        final List<FieldMapping> entries = fields.requiredModels("fields", (value, at) -> read(reader, value, at,
                List.copyOf(inner)), 1);
        if (!fields.ok()) {
            return null;
        }
        return new FieldMapping(enclosing.isEmpty()
                ? path
                : relative, null, null, null, null, null, null, null, null, false, null, null, null, null, null,
                dataName, null, null, null, false, null, null, null, header.withFields(entries), null);
    }

    // Whether an input of a group's field (or of a group's for_each) reads what it may: item names a group it is in,
    // no key of a text item is read, and a zip item's keys are its names. Null if it does.
    private static String itemProblem(final String item,
                                      final boolean record,
                                      final List<String> keys,
                                      final boolean own,
                                      final List<Enclosing> enclosing) {
        if (enclosing.isEmpty()) {
            return item == null
                    ? null
                    : "item reads a group's item, so it goes in a group's fields";
        }
        if (record) {
            return null;
        }
        final Enclosing from;
        if (item == null || item.isEmpty()) {
            from = enclosing.getLast();
        } else {
            from = enclosing.stream().filter(outer -> item.equals(outer.group().itemName())).findFirst().orElse(null);
            if (from == null) {
                return "item " + ToolText.repr(item) + " names no group it is in (their items: "
                       + ToolText.list(enclosing.stream().map(outer -> outer.group().itemName()).toList()) + ")";
            }
        }
        final ItemSource source = from.group().forEach();
        final String name = from.group().itemName();
        final List<String> named = keys.stream().filter(key -> key != null && !key.isEmpty()).toList();
        if (named.isEmpty()) {
            return ItemSource.ZIP.equals(source.kind())
                    ? "reads " + name + " whole, but a zip's item is one value from each list, read by its name: "
                      + "field " + ToolText.list(List.copyOf(source.zip().keySet()))
                    : null;
        }
        if (ItemSource.SPLIT.equals(source.kind())) {
            return "the items of " + name + " are pieces of text (a split), with no keys: read the piece with item: "
                   + (own
                    ? "true"
                    : "\"" + name + "\"") + ", not field " + ToolText.list(named);
        }
        if (ItemSource.ZIP.equals(source.kind())) {
            final List<String> unknown = named.stream().filter(key -> !source.zip().containsKey(key)).toList();
            if (!unknown.isEmpty()) {
                return "a zip's item has the names " + ToolText.list(List.copyOf(source.zip().keySet()))
                       + ", one value each: not " + ToolText.list(unknown);
            }
        }
        return null;
    }

    // The checks of an entry by the groups it is in (and of item, outside them).
    private String groupProblem(final List<Enclosing> enclosing) {
        final String where = "'" + path + "'";
        if (enclosing.isEmpty()) {
            return item == null
                    ? null
                    : where + ": item reads a group's item, so it goes in a group's fields (for_each)";
        }
        if (repeat) {
            return where + ": a group's field is written once per item already; repeat goes with a field outside "
                   + "groups (for a list inside an item, make it a group of its own)";
        }
        final String stripped = strippedPath();
        final Enclosing group = enclosing.getLast();
        final String first = ToolText.split(stripped, "/").getFirst();
        if (!stripped.startsWith(group.path() + "/") && EVENT_SECTIONS.contains(first)) {
            return where + " is outside the group's element " + group.path() + ": a group's fields are paths below "
                   + "it, relative to it (e.g. 'Id' for " + group.path() + "/Id)";
        }
        if (group.data() && (dataName == null || !"Data".equals(group.relative(stripped)))) {
            return where + ": a Data group holds Data entries only: {\"path\": \"Data\", \"data_name\": \"<its "
                   + "Name>\", ...}";
        }
        final List<String> keys = new ArrayList<>();
        if (field != null) {
            keys.add(field);
        }
        if (anyOf != null) {
            keys.addAll(anyOf);
        }
        if (join != null) {
            keys.addAll(join);
        }
        if (lookup != null && lookup.field() != null) {
            keys.add(lookup.field());
        }
        if (keys.isEmpty() && item == null) {
            return null;     // a constant, or an xpath of its own
        }
        final String problem = itemProblem(item, "record".equals(scope), keys, item == null || item.isEmpty(),
                enclosing);
        return problem == null
                ? null
                : where + ": " + problem;
    }

    /// The model's own check (one_source).
    ///
    /// @return Why the entry is wrong, or null.
    String problem() {
        if (meta != null) {
            // Stream meta is read as stroom:meta('Name'), which the generator, the documentation and rebuild_mapping
            // already handle as an xpath: kept in that one form (asked for by the user: values like MyHostName).
            final List<String> others = given();
            if (!others.isEmpty() || ToolText.isBlank(meta) || meta.contains("'")) {
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
            return "path " + ToolText.repr(path) + " holds more than a path (from " + ToolText.repr(spilled.group())
                   + " on): the next key ran into its string. Close the path's string and give the input as a key of "
                   + "its own, e.g. {\"path\": \"" + before + "\", \"xpath\": \"<the xpath>\"}";
        }
        final List<String> given = meta == null
                ? given()
                : List.of("xpath");
        if (item != null && (given.isEmpty() || given.equals(List.of("field")))) {
            // The item itself, or a key of it: one source.
            return problemBeyondSource();
        }
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
        return problemBeyondSource();
    }

    // The checks after the one source is found.
    private String problemBeyondSource() {
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
                return "'" + path + "': field " + ToolText.repr(name) + " " + called;
            }
        }
        if (dictionary != null && field == null && anyOf == null) {
            return "'" + path + "': dictionary needs field (or any_of) as the key";
        }
        if (transform != null && value != null) {
            return "'" + path + "': transform applies to an input, not a constant";
        }
        if (value != null && ToolText.isBlank(value)) {
            // Seen: Network/Permit/Source/Device/IPAddress given value '' for want of the address (inside a string
            // field, unextracted): an empty element in every event, which the schema refuses.
            return "'" + path + "': value '' writes an empty element, which the schema refuses: map the field that "
                   + "holds it (extract it first if it is inside another field's text), or leave the entry out";
        }
        if (dataParent != null && (dataName == null || !path.endsWith("Data")
                                   || Arrays.stream(dataParent.split("/", -1)).anyMatch(ToolText::isBlank))) {
            return "'" + path + "': data_parent names the Data element(s) a Data entry is written in (e.g. "
                   + "'members', or 'a/b' for one inside another), so it goes with a path ending in Data and its "
                   + "data_name";
        }
        if (join != null && (join.size() < 2 || join.stream().anyMatch(ToolText::isBlank))) {
            return "'" + path + "': join lists the fields whose values are joined, two or more, e.g. {\"path\": \""
                   + path + "\", \"join\": [\"event\", \"action\"]} for User-Login";
        }
        if (separator != null && join == null) {
            return "'" + path + "': separator goes with join, between the values it joins";
        }
        if (join != null && (repeat || isSet(timeFormat) || isSet(dictionary))) {
            return "'" + path + "': join gives one text from its fields: no repeat, time_format or dictionary with it";
        }
        if (rollover && (!isSet(timeFormat) || XPathText.EPOCH_FORMATS.contains(timeFormat) || repeat)) {
            return "'" + path + "': rollover goes with a time_format pattern (a date joined with a time of day), not "
                   + "an epoch, and one value";
        }
        if (timeFallback != null && (!isSet(timeFormat) || ToolText.isBlank(timeFallback) || timeFallback.contains("'")
                                     || rollover || repeat)) {
            return "'" + path + "': time_fallback names a stream meta time (e.g. 'ReceivedTime') written when the "
                   + "time doesn't parse, so it goes with time_format, one value and no rollover";
        }
        if (metaFallback != null && (metaFallback.isEmpty() || value != null || repeat || isSet(timeFormat) || hasMap()
                                     || metaFallback.stream().anyMatch(name -> !ToolText.fullmatch(META_NAME, name)))) {
            return "'" + path + "': meta_fallback names stream meta attributes (e.g. ['RemoteAddress']) tried in "
                   + "order for a record whose input gives no value, so it goes with an input (not a value), one "
                   + "value, no map and no time_format (time_fallback is a time's)";
        }
        if (zoneSource != null && (!isSet(timeFormat) || XPathText.EPOCH_FORMATS.contains(timeFormat) || repeat)) {
            return "'" + path + "': timezone from a field or meta attribute goes with a time_format pattern (not an "
                   + "epoch, which has no zone to read) and one value";
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
        return new FieldMapping(path, field, anyOf, value, "stroom:meta('" + ToolText.strip(meta) + "')", null, lookup,
                dictionary, transform, repeat, scope, timeFormat, timezone, map, defaultValue, dataName, dataParent,
                join, separator, rollover, timeFallback, zoneSource, metaFallback, group, item);
    }

    /// @param defaultValue The output when the input is empty.
    /// @return This entry with that default.
    public FieldMapping withDefault(final String defaultValue) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator, rollover, timeFallback,
                zoneSource, metaFallback, group, item);
    }

    /// @param dataName The Data element's Name.
    /// @return This entry with that Name.
    public FieldMapping withDataName(final String dataName) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator, rollover, timeFallback,
                zoneSource, metaFallback, group, item);
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
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator, rollover, timeFallback,
                zoneSource, metaFallback, group, item);
    }

    /// @param path The path.
    /// @return This entry at that path.
    public FieldMapping withPath(final String path) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator, rollover, timeFallback,
                zoneSource, metaFallback, group, item);
    }

    /// @param fields A group entry's fields.
    /// @return This group entry with those fields.
    public FieldMapping withGroupFields(final List<FieldMapping> fields) {
        return new FieldMapping(path, field, anyOf, value, xpath, meta, lookup, dictionary, transform, repeat, scope,
                timeFormat, timezone, map, defaultValue, dataName, dataParent, join, separator, rollover, timeFallback,
                zoneSource, metaFallback, group.withFields(fields), item);
    }

    /// @return True if it is a group (for_each): its element written once per item.
    public boolean isGroup() {
        return group != null;
    }

    /// @return What join puts between its values.
    public String joinSeparator() {
        return separator == null
                ? "-"
                : separator;
    }

    /// @return True if the entry has a value map with at least one key.
    public boolean hasMap() {
        return map != null && !map.isEmpty();
    }

    /// @return The path without leading and trailing slashes.
    public String strippedPath() {
        return ToolText.strip(path, "/");
    }

    private static boolean isSet(final String text) {
        return text != null && !text.isEmpty();
    }

    /// @param mode How to write it.
    /// @return It as JSON, as the mode says, keys in the model's field order.
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
                .put("timezone", zoneSource == null
                        ? timezone
                        : zoneSource.toJson(mode), null)
                .put("map", map == null
                        ? null
                        : new LinkedHashMap<>(map), null)
                .put("default", defaultValue, null)
                .put("data_name", dataName, null)
                .putIfSet("data_parent", dataParent)
                .putIfSet("join", join)
                .putIfSet("separator", separator)
                .putIfSet("rollover", rollover
                        ? Boolean.TRUE
                        : null)
                .putIfSet("time_fallback", timeFallback)
                .putIfSet("meta_fallback", metaFallback)
                .putIfSet("item", item == null
                        ? null
                        : item.isEmpty()
                                ? (Object) Boolean.TRUE
                                : item)
                .putIfSet("for_each", group == null
                        ? null
                        : group.forEach().toJson(mode))
                .putIfSet("as", group == null
                        ? null
                        : group.as())
                .putIfSet("fields", group == null
                        ? null
                        : group.fields().stream().map(entry -> entry.toJson(mode)).toList())
                .build();
    }


    // --------------------------------------------------------------------------------


    // A group a field being read is in: its element's full path, the group (fields not yet read), and whether it is a
    // Data group.
    private record Enclosing(String path, FieldGroup group, boolean data) {

        // A field's path relative to the group: as given, or with the group's path taken off when given from Event.
        String relative(final String given) {
            return given.startsWith(path + "/")
                    ? given.substring(path.length() + 1)
                    : given;
        }
    }
}
