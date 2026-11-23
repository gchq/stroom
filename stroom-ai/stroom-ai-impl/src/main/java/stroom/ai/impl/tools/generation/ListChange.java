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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// An entry's `change` (decided by the user 2026-11-23): a JSON input's old/new property-change list
/// ([stroom.ai.impl.tools.sample.NameValueLists#changes(java.util.Collection)]), each item a property's name with its
/// old and new values (`[{"Name": "DisplayName", "OldValue": "Sam T", "NewValue": "Sam Taylor"}, ...]`), written as an
/// Update's Before and After snapshots. Which value an entry reads is said by its path: below `Before` the old value,
/// below `After` the new (`EventDetail/Update/Before/User/Name`, `EventDetail/Update/After/User/Name`).
///
/// * With `name`, one property's value: `{"path": "EventDetail/Update/After/User/Name", "change": {"list":
///   "event.ModifiedProperties", "name": "DisplayName", "name_key": "Name", "old_key": "OldValue", "new_key":
///   "NewValue"}}`.
/// * Without, at a path ending Data (`EventDetail/Update/Before/User/Data`): a Data per property holding a value on
///   that side, named by the property, but the properties the event's other entries read with `change` from the same
///   list (each written once, in its typed element). A property added (no old value) is written in After only, one
///   removed (no new value) in Before only; the After side's object is written whatever the list holds, as the schema
///   requires After.
///
/// A value held as JSON text of a string or an array of one scalar is unwrapped to it (`"[\"true\"]"` is `true`,
/// `"\"Sam T\""` is `Sam T`); a longer array or an object stays as its JSON text; a JSON array of scalars (as the
/// parser writes it) is joined with `, `, as a name/value list's pair's is. In a group's fields (`for_each` over
/// targets each holding a list, `targetResources[].modifiedProperties`) the list is a key of the item.
///
/// @param list    The list field (a JSON key path, e.g. `event.ModifiedProperties`; a key of the item in a group).
/// @param name    The property read, by its name; null for a Data per property.
/// @param nameKey `name_key`: the items' key holding each property's name.
/// @param oldKey  `old_key`: the key holding its value before the change.
/// @param newKey  `new_key`: the key holding its value after.
/// @param before  True where the entry is below Before (reads old values), false below After; null until the entry
///                is placed (never written to JSON).
public record ListChange(String list, String name, String nameKey, String oldKey, String newKey, Boolean before) {

    /// The keys a change has.
    public static final List<String> KEYS = List.of("list", "name", "name_key", "old_key", "new_key");
    /// The snapshot of the object before the change.
    public static final String BEFORE = "Before";
    /// The snapshot of the object after the change.
    public static final String AFTER = "After";

    /// Reads one from JSON-like values.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't read.
    static ListChange read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ListChange");
        if (fields == null) {
            return null;
        }
        final ListChange change = new ListChange(fields.requiredString("list"), fields.optionalString("name"),
                fields.optionalString("name_key"), fields.optionalString("old_key"),
                fields.optionalString("new_key"), null);
        if (!fields.ok()) {
            return null;
        }
        final List<String> other = fields.input().keySet().stream().filter(key -> !KEYS.contains(key)).toList();
        if (!other.isEmpty() || NullSafe.isBlankString(change.list) || change.name != null && change.name.isEmpty()) {
            fields.valueError("change is {\"list\": \"<the list field>\", \"name\": \"<the property's name>\", "
                              + "\"name_key\": \"<the items' key holding the name>\", \"old_key\": \"<the key "
                              + "holding the old value>\", \"new_key\": \"<the key holding the new value>\"}"
                              + (other.isEmpty()
                    ? ""
                    : ": not " + String.join(", ", other)));
            return null;
        }
        final Set<String> keys = new HashSet<>();
        for (final String key : List.of(NullSafe.string(change.nameKey), NullSafe.string(change.oldKey),
                NullSafe.string(change.newKey))) {
            if (key.isBlank() || !keys.add(key)) {
                final String named = change.name == null
                        ? ""
                        : "\"name\": \"" + change.name + "\", ";
                fields.valueError("change names the three keys of the list's items, name_key (holding each "
                                  + "property's name), old_key (its value before) and new_key (its value after), "
                                  + "each a different key, e.g. {\"list\": \"" + change.list + "\", " + named
                                  + "\"name_key\": \"Name\", \"old_key\": \"OldValue\", \"new_key\": \"NewValue\"}: "
                                  + "the draft and the catalogue give them from the sample");
                return null;
            }
        }
        return change;
    }

    /// @param path The entry's whole path (a group's field's from its group's).
    /// @return True if it is below Before, false below After, null below neither (the nearer of the two counts).
    public static Boolean sideOf(final String path) {
        final List<String> steps = List.of(path.split("/"));
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (BEFORE.equals(steps.get(i))) {
                return true;
            }
            if (AFTER.equals(steps.get(i))) {
                return false;
            }
        }
        return null;
    }

    /// @param before True for the Before side.
    /// @return It, placed on that side.
    public ListChange onSide(final boolean before) {
        return new ListChange(list, name, nameKey, oldKey, newKey, before);
    }

    /// @return True if it writes a Data per property (no name given).
    public boolean perProperty() {
        return name == null;
    }

    /// @return True if it is placed in After, whose object is written whatever the list holds.
    public boolean after() {
        return Boolean.FALSE.equals(before);
    }

    /// @return The items' key of the value its side reads: the old value's in Before, the new's in After.
    public String sideKey() {
        return Boolean.TRUE.equals(before)
                ? oldKey
                : newKey;
    }

    /// @return The list's items, relative to the record (or a group's item): `*[@key='event']/*[@key='X']/*`.
    public String itemsXpath() {
        return XPathText.fieldText("json", list) + "/*";
    }

    /// @return The xpath of the property of its name's value on its side, unwrapped ([ListChange]).
    public String valueXpath() {
        return valueOf(itemsXpath() + "[*[@key=" + XPathText.literal(nameKey) + "] = " + XPathText.literal(name)
                       + "]");
    }

    /// @param except The names other entries read, each written there only.
    /// @return The xpath of the items a Data per property writes: those with a name and a value on its side, but the
    /// names given.
    public String perPropertyXpath(final Collection<String> except) {
        final String named = "*[@key=" + XPathText.literal(nameKey) + "]";
        final String left = except.isEmpty()
                ? ""
                : "[not(" + named + " = (" + except.stream().map(XPathText::literal)
                        .collect(Collectors.joining(", ")) + "))]";
        return itemsXpath() + "[normalize-space(" + named + ") and normalize-space(" + valueOfItem() + ")]" + left;
    }

    /// @return The xpath of an item's name, relative to the item.
    public String nameOfItem() {
        return "string(*[@key=" + XPathText.literal(nameKey) + "])";
    }

    /// @return The xpath of an item's value on its side, relative to the item, unwrapped: one string.
    public String valueOfItem() {
        return "string-join(" + valueOf(".") + ", '')";
    }

    // The value on its side of the items given: a JSON array (as parsed) of scalars joined, JSON text of one value
    // unwrapped, any other text as it is.
    private String valueOf(final String items) {
        return "(" + items + "/*[@key=" + XPathText.literal(sideKey()) + "]) ! (if (*) then "
               + "string-join(descendant::*[not(*)], ', ') else if (matches(string(.), "
               + XPathText.literal(NameValueLists.JSON_ONE_VALUE_REGEX) + ")) then string(mcp:json-to-xml(string(.))) "
               + "else string(.))";
    }

    /// @param mode How to write it.
    /// @return It as JSON (its side is its entry's path's, not written).
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .put("list", list, null)
                .putIfSet("name", name)
                .put("name_key", nameKey, null)
                .put("old_key", oldKey, null)
                .put("new_key", newKey, null)
                .build();
    }
}
