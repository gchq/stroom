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

import stroom.ai.impl.tools.sample.NameValueLists;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// An entry's `pair` (decided by the user 2026-11-23): a JSON input's name/value list
/// ([stroom.ai.impl.tools.sample.NameValueLists]), each item a pair of a name and its value
/// (`[{"Name": "From", "Value": "..."}, ...]`), read by its pairs' names rather than by a hand-written xpath.
///
/// * With `name`, one pair's value: `{"path": "EventDetail/Create/Configuration/Name", "pair": {"list":
///   "event.Parameters", "name": "Name", "name_key": "Name", "value_key": "Value"}}`, read as the xpath
///   `*[@key='event']/*[@key='Parameters']/*[*[@key='Name'] = 'Name']/*[@key='Value']`.
/// * Without, at a path ending Data: a Data per pair, named by its pair's name, holding its value, but the pairs
///   whose names the event's other entries read with `pair` from the same list (each written once; a Data entry
///   reading one by name keeps it as Data too).
///
/// @param list     The list field (a JSON key path, e.g. `event.Parameters`).
/// @param name     The pair read, by its name; null for a Data per pair.
/// @param nameKey  `name_key`: the items' key holding each pair's name.
/// @param valueKey `value_key`: the items' key holding its value, or `*` where each item holds it under a key of its
///                 own (`{"name": "IS_ADMIN", "boolValue": true}`): its one key besides the name, a list's values
///                 joined with `, ` (one Data per pair, as for the other pairs, and one value a leaf takes).
public record ListPair(String list, String name, String nameKey, String valueKey) {

    /// The value key of a list whose items each hold their value under a key of its own (`value`, `boolValue`,
    /// `multiValue`...): the item's one key besides its name.
    public static final String ANY_VALUE_KEY = NameValueLists.ANY_VALUE_KEY;
    /// The keys a pair has.
    public static final List<String> KEYS = List.of("list", "name", "name_key", "value_key");

    /// Reads one from JSON-like values.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't read.
    static ListPair read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ListPair");
        if (fields == null) {
            return null;
        }
        final ListPair pair = new ListPair(fields.requiredString("list"), fields.optionalString("name"),
                fields.optionalString("name_key"), fields.optionalString("value_key"));
        if (!fields.ok()) {
            return null;
        }
        final List<String> other = fields.input().keySet().stream().filter(key -> !KEYS.contains(key)).toList();
        if (!other.isEmpty() || NullSafe.isBlankString(pair.list) || pair.name != null && pair.name.isEmpty()) {
            fields.valueError("pair is {\"list\": \"<the list field>\", \"name\": \"<the pair's name>\", "
                              + "\"name_key\": \"<the items' key holding the name>\", \"value_key\": \"<the key "
                              + "holding its value>\"}" + (other.isEmpty()
                    ? ""
                    : ": not " + String.join(", ", other)));
            return null;
        }
        if (NullSafe.isBlankString(pair.nameKey) || NullSafe.isBlankString(pair.valueKey)
            || pair.nameKey.equals(pair.valueKey)) {
            fields.valueError("pair names the two keys of the list's items, name_key (holding each pair's name) "
                              + "and value_key (holding its value), e.g. {\"list\": \"" + pair.list + "\", "
                              + (pair.name == null
                    ? ""
                    : "\"name\": \"" + pair.name + "\", ") + "\"name_key\": \"Name\", \"value_key\": \"Value\"}: "
                              + "the draft and the catalogue give them from the sample");
            return null;
        }
        return pair;
    }

    /// @return True if it writes a Data per pair (no name given).
    public boolean perPair() {
        return name == null;
    }

    /// @return The list's items, as the JSON input's parser writes them: `*[@key='event']/*[@key='Parameters']/*`.
    public String itemsXpath() {
        return XPathText.fieldText("json", list) + "/*";
    }

    /// @return The xpath reading the pair of its name's value (where each item holds its value under a key of its
    /// own, that key's value, a list's values joined with `, `).
    public String valueXpath() {
        final String pair = XPathText.fieldText("json", list) + "/*[*[@key=" + XPathText.literal(nameKey) + "] = "
                            + XPathText.literal(name) + "]";
        return anyValueKey()
                ? "string-join(" + pair + "/" + valueStep() + "/descendant-or-self::*[not(*)], ', ')"
                : pair + "/" + valueStep();
    }

    /// @return True if each item holds its value under a key of its own ([#ANY_VALUE_KEY]).
    public boolean anyValueKey() {
        return ANY_VALUE_KEY.equals(valueKey);
    }

    // The step from an item to its value: its value key, or its one key besides the name.
    private String valueStep() {
        return anyValueKey()
                ? "*[not(@key=" + XPathText.literal(nameKey) + ")]"
                : "*[@key=" + XPathText.literal(valueKey) + "]";
    }

    /// @param except The names other entries read, each written there only.
    /// @return The xpath of the items a Data per pair writes: those with a name and a value, but the names given.
    public String perPairXpath(final Collection<String> except) {
        final String name = "*[@key=" + XPathText.literal(nameKey) + "]";
        final String value = valueStep();
        final String left = except.isEmpty()
                ? ""
                : "[not(" + name + " = (" + except.stream().map(XPathText::literal).collect(Collectors.joining(", "))
                  + "))]";
        return itemsXpath() + "[normalize-space(" + name + ") and normalize-space(" + value + ")]" + left;
    }

    /// @return The xpath of an item's name, relative to the item.
    public String nameOfItem() {
        return "string(*[@key=" + XPathText.literal(nameKey) + "])";
    }

    /// @return The xpath of an item's value, relative to the item: a list's values joined with `, `.
    public String valueOfItem() {
        return anyValueKey()
                ? "string-join(" + valueStep() + "/descendant-or-self::*[not(*)], ', ')"
                : "string(" + valueStep() + ")";
    }

    /// @param mode How to write it.
    /// @return It as JSON.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("list", list, null)
                .putIfSet("name", name)
                .put("name_key", nameKey, null)
                .put("value_key", valueKey, null)
                .build();
    }
}
