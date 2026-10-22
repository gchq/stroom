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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// A pipeline element's property as a tool is asked to set it (the Stroom MCP server's
/// `tools/pipeline_writes.py` `PropertyValue`): a document (`doc_uuid` and `doc_type`) or a plain value (text, a
/// number or true/false).
///
/// @param element The element's id, e.g. `translationFilter`.
/// @param name    The property's name, e.g. `xslt`.
/// @param docUuid For a document property: the document's UUID; else null.
/// @param docType For a document property: its type, e.g. `XSLT`; else null.
/// @param value   For a plain property: a [String], [Long] or [Boolean]; else null.
record PropertyValue(String element, String name, String docUuid, String docType, Object value) {

    /// How a property is given, for the descriptions and the refusal of one that isn't.
    static final String EXAMPLE = "[{\"element\": \"translationFilter\", \"name\": \"xslt\", \"doc_type\": \"XSLT\", "
                                  + "\"doc_uuid\": \"<its uuid>\"}, {\"element\": \"jsonParser\", \"name\": "
                                  + "\"addRootObject\", \"value\": false}]";
    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> DOC_TYPES = Map.of("xslt", "XSLT", "textConverter", "TextConverter");

    /// @param element An element's id.
    /// @param name    A property's name.
    /// @param value   Its plain value.
    /// @return The property with that value.
    static PropertyValue plain(final String element, final String name, final Object value) {
        return new PropertyValue(element, name, null, null, value);
    }

    /// @param element An element's id.
    /// @param name    A property's name.
    /// @param docUuid The document's UUID.
    /// @param docType Its type.
    /// @return The property naming that document.
    static PropertyValue document(final String element, final String name, final String docUuid,
                                  final String docType) {
        return new PropertyValue(element, name, docUuid, docType, null);
    }

    /// @param newValue Another plain value.
    /// @return This property with it.
    PropertyValue withValue(final Object newValue) {
        return new PropertyValue(element, name, docUuid, docType, newValue);
    }

    /// @return The property as the descriptions name it: `element.name`.
    String dotted() {
        return element + "." + name;
    }

    /// Reads the properties a call gives, in the shapes models send: a list, one property, or the list as JSON
    /// text (the MCP server's `ONE_OR_MORE`).
    ///
    /// @param given    The argument, as the call's JSON gives it, or null.
    /// @param argument The argument's name, for a refusal.
    /// @return The properties.
    /// @throws ToolRefusal if one isn't a property.
    static List<PropertyValue> listOf(final Object given, final String argument) {
        final List<Object> items = ToolArgs.oneOrMore(given);
        final List<PropertyValue> properties = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                properties.add(of(items.get(i)));
            } catch (final IllegalArgumentException e) {
                throw new ToolRefusal(argument + "[" + i + "]: " + e.getMessage());
            }
        }
        return properties;
    }

    /// Reads one property. Agents guess the shape from `translationFilter.xslt` as the descriptions name a
    /// property (the debug log of a VS Code session: "schema guessing"): `translationFilter.xslt=<uuid>`,
    /// `{"translationFilter.xslt": "<uuid>"}` and `{"element": "translationFilter.xslt", ...}` are read as the
    /// property they plainly are, and a UUID given as an XSLT's or text converter's value as that document.
    ///
    /// @param given A property as given.
    /// @return It.
    /// @throws IllegalArgumentException if it isn't one.
    static PropertyValue of(final Object given) {
        Object data = given;
        if (data instanceof final String text && text.split("=", 2)[0].contains(".")) {
            final String[] parts = text.split("=", 2);
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put(parts[0].strip(), parts.length > 1
                    ? parts[1].strip()
                    : "");
            data = map;
        }
        if (!(data instanceof final Map<?, ?> raw)) {
            throw notAProperty(data);
        }
        final Map<String, Object> asGiven = new LinkedHashMap<>();
        raw.forEach((key, value) -> asGiven.put(String.valueOf(key), value));
        Map<String, Object> map = asGiven;
        final Object dotted = firstTruthy(map.get("property"), map.get("element"));
        if (!map.containsKey("name") && dotted instanceof final String text && text.contains(".")) {
            final Map<String, Object> read = new LinkedHashMap<>(map);
            read.remove("property");
            read.put("element", text.substring(0, text.lastIndexOf('.')));
            read.put("name", text.substring(text.lastIndexOf('.') + 1));
            map = read;
        } else if (map.size() == 1 && map.keySet().iterator().next().contains(".")
                   && !map.containsKey("element") && !map.containsKey("name")) {
            final String key = map.keySet().iterator().next();
            final Map<String, Object> read = new LinkedHashMap<>();
            read.put("element", key.substring(0, key.lastIndexOf('.')));
            read.put("name", key.substring(key.lastIndexOf('.') + 1));
            read.put("value", map.get(key));
            map = read;
        }
        final Object value = map.get("value");
        if (map.get("doc_uuid") == null && value instanceof final String text && UUID.matcher(text).matches()
            && DOC_TYPES.containsKey(String.valueOf(map.get("name")))) {
            map.put("doc_uuid", text);
            map.put("doc_type", NullSafe.nonBlankStringElse(text(map.get("doc_type")),
                    DOC_TYPES.get(String.valueOf(map.get("name")))));
            map.put("value", null);
        }
        if (!map.containsKey("element") || !map.containsKey("name")) {
            throw notAProperty(map);
        }
        return new PropertyValue(text(map.get("element")), text(map.get("name")), text(map.get("doc_uuid")),
                text(map.get("doc_type")), plainValue(map.get("value")));
    }

    // A value as the server's model held it: text, a whole number or true/false.
    private static Object plainValue(final Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof final Number number) {
            final double asDouble = number.doubleValue();
            return asDouble == Math.rint(asDouble) && !Double.isInfinite(asDouble)
                    ? (Object) number.longValue()
                    : String.valueOf(number);
        }
        return PyJson.dumps(value);
    }

    private static String text(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }

    private static Object firstTruthy(final Object first, final Object second) {
        if (first != null && !"".equals(first)) {
            return first;
        }
        return second != null && !"".equals(second)
                ? second
                : "";
    }

    private static IllegalArgumentException notAProperty(final Object given) {
        final String shown = PyJson.dumps(given);
        return new IllegalArgumentException("a property is {element, name} with doc_uuid and doc_type (a "
                                            + "document) or value (a plain property), e.g. " + EXAMPLE
                                            + "; given " + shown.substring(0, Math.min(160, shown.length())));
    }
}
