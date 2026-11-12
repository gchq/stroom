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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Where a group's items come from (its `for_each`): give exactly one of split, array or zip.
///
/// A source field is read from the enclosing group's item (the record, for a group not inside another), unless
/// `item` names an outer group's item or `scope` is `record`.
///
/// @param split The field whose text is split on `on`: each piece, trimmed, an item (empty pieces dropped).
/// @param on    With split (or zip): the separator, a literal string (not a regex), e.g. `,`.
/// @param array The field holding the items: a JSON array (of objects or of values), or an element repeated in XML
///              (or a Data Splitter data element of that name).
/// @param zip   Several lists paired by position: item name -> the field holding the list (split on `on` when it is
///              given, else its values); each item has one value from each list, read by its name.
/// @param item  The name (`as`) of an outer group whose item the field is read from, or null.
/// @param scope `record` to read the field from the record rather than an item, or null.
public record ItemSource(String split,
                         String on,
                         String array,
                         Map<String, String> zip,
                         String item,
                         String scope) {

    /// The kinds of source.
    public static final String SPLIT = "split";
    /// Items from an array.
    public static final String ARRAY = "array";
    /// Items from parallel lists.
    public static final String ZIP = "zip";
    /// What an item's name (`as`, a zip name) may be.
    static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");

    /// Reads one from JSON-like values: lax types, the model's own checks, every error collected.
    ///
    /// @param reader The reader, collecting the errors.
    /// @param input  The value given.
    /// @param loc    Where it is.
    /// @return It, or null (with the errors noted) if it doesn't validate.
    static ItemSource read(final ModelReader reader, final Object input, final List<Object> loc) {
        final ModelReader.Fields fields = reader.fields(input, loc, "ItemSource");
        if (fields == null) {
            return null;
        }
        final ItemSource read = new ItemSource(fields.optionalString("split"), fields.optionalString("on"),
                fields.optionalString("array"), fields.optionalStringMap("zip"), fields.optionalString("item"),
                fields.optionalLiteral("scope", List.of("record")));
        if (!fields.ok()) {
            return null;
        }
        final String problem = read.problem();
        if (problem != null) {
            fields.valueError(problem);
            return null;
        }
        return read;
    }

    // The model's own check.
    private String problem() {
        final List<String> given = new ArrayList<>();
        if (split != null) {
            given.add(SPLIT);
        }
        if (array != null) {
            given.add(ARRAY);
        }
        if (zip != null) {
            given.add(ZIP);
        }
        if (given.size() != 1) {
            return "for_each: give exactly one of split (a field's text split on 'on'), array (a field holding the "
                   + "items) or zip (lists paired by position) (" + (given.isEmpty()
                    ? "none given"
                    : "given " + String.join(" and ", given)) + "), e.g. {\"split\": \"recipients\", \"on\": \",\"}";
        }
        if (split != null && (ToolText.isBlank(split) || on == null || on.isEmpty())) {
            return "for_each: split names the field whose text is split, and on the separator (a literal, e.g. "
                   + "\",\"), e.g. {\"split\": \"recipients\", \"on\": \",\"}";
        }
        if (array != null && (ToolText.isBlank(array) || on != null)) {
            return "for_each: array names the field holding the items (a JSON array, or a repeated element); on "
                   + "goes with split or zip";
        }
        if (zip != null) {
            if (zip.size() < 2) {
                return "for_each: zip pairs two or more lists by position, each named, e.g. {\"zip\": {\"recipient\": "
                       + "\"recipients\", \"status\": \"recipient_status\"}, \"on\": \",\"}";
            }
            for (final Map.Entry<String, String> list : zip.entrySet()) {
                if (!NAME.matcher(list.getKey()).matches() || ToolText.isBlank(list.getValue())) {
                    return "for_each: zip's names are plain names (letters, digits, _ or -), each the field holding "
                           + "its list; not " + ToolText.repr(list.getKey());
                }
            }
            if (on != null && on.isEmpty()) {
                return "for_each: on, with zip, is the separator each list's text is split on";
            }
        }
        if (item != null && scope != null) {
            return "for_each: item (an outer group's item) and scope 'record' are two places to read from; give one";
        }
        return null;
    }

    /// @return `split`, `array` or `zip`.
    public String kind() {
        if (split != null) {
            return SPLIT;
        }
        return array != null
                ? ARRAY
                : ZIP;
    }

    /// @return The fields it reads: split's or array's, or each of zip's lists.
    public List<String> fields() {
        if (zip != null) {
            return List.copyOf(zip.values());
        }
        return List.of(split != null
                ? split
                : array);
    }

    /// @return True if it reads the record, not an item.
    public boolean readsRecord() {
        return "record".equals(scope);
    }

    /// @return Where the items come from, for a reader, e.g. `` each of `recipients` split on ',' ``.
    public String describe() {
        return switch (kind()) {
            case SPLIT -> "each of `" + split + "` split on '" + on + "'";
            case ARRAY -> "each item of `" + array + "`";
            default -> "each position of " + zip.entrySet().stream()
                    .map(list -> "`" + list.getValue() + "` (" + list.getKey() + ")")
                    .reduce((a, b) -> a + ", " + b).orElse("") + (on == null
                    ? ""
                    : " split on '" + on + "'");
        } + (item != null
                ? " of " + item
                : readsRecord()
                        ? " of the record"
                        : "");
    }

    /// @param mode How to write it.
    /// @return It as JSON, keys in the model's field order, only those set.
    public Map<String, Object> toJson(final DumpMode mode) {
        return mode.builder()
                .putIfSet(SPLIT, split)
                .putIfSet(ARRAY, array)
                .putIfSet(ZIP, zip == null
                        ? null
                        : new LinkedHashMap<>(zip))
                .putIfSet("on", on)
                .putIfSet("item", item)
                .putIfSet("scope", scope)
                .build();
    }
}
