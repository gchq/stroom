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

package stroom.ai.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/// Shows the text of ERROR chat messages. An error can be the JSON a model provider or MCP server answered with,
/// e.g. `{"error":"Bad Request: The endpoint is paused","code":"BAD_REQUEST"}`, which is shown as a heading, what
/// went wrong and the error's other details.
final class AiErrorMessages {

    // The fields that may say what went wrong, most telling first.
    private static final List<String> MESSAGE_KEYS = List.of(
            "message", "detail", "error_description", "description", "error", "msg", "reason");
    // The fields that may name the kind of error, to use as the heading if nothing better gives one.
    private static final List<String> KIND_KEYS = List.of("type", "code", "status");
    private static final String TITLE_KEY = "title";
    private static final String ERROR_KEY = "error";
    private static final String DEFAULT_HEADING = "Error";
    // A message that starts with a short phrase and a colon, e.g. "Bad Request: ...", is headed by the phrase.
    private static final int MAX_HEADING_LENGTH = 40;
    private static final String INDENT = "  ";

    private AiErrorMessages() {
        // Static only.
    }

    /// @param text An ERROR message's text.
    /// @return The text as markdown: JSON is shown as a heading and its details, and anything else as it is.
    static String markdown(final String text) {
        final String trimmed = NullSafe.trim(text);
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return text;
        }
        final Object tree;
        try {
            tree = toTree(JSONParser.parseStrict(trimmed));
        } catch (final RuntimeException e) {
            // Not JSON after all.
            return text;
        }
        final String markdown = format(tree);
        return markdown != null
                ? markdown
                : text;
    }

    /// @param tree A JSON error read as maps (objects), lists (arrays), strings (strings, numbers and booleans)
    ///             and nulls.
    /// @return The error as markdown, or null if it is not an object or a list of them.
    static String format(final Object tree) {
        if (tree instanceof final Map<?, ?> map) {
            return formatObject(asFields(map));
        }
        if (tree instanceof final List<?> list && !list.isEmpty()) {
            final List<String> errors = new ArrayList<>();
            for (final Object item : list) {
                final String error = format(item);
                if (error == null) {
                    return null;
                }
                errors.add(error);
            }
            return String.join("\n\n", errors);
        }
        return null;
    }

    private static String formatObject(final Map<String, Object> object) {
        final Map<String, Object> fields = unwrap(object);
        String heading = takeString(fields, List.of(TITLE_KEY));
        String message = takeString(fields, MESSAGE_KEYS);
        if (heading == null && message != null) {
            final int colon = message.indexOf(": ");
            if (colon > 0 && colon <= MAX_HEADING_LENGTH && message.substring(0, colon).indexOf('.') < 0) {
                heading = message.substring(0, colon);
                message = message.substring(colon + 2).trim();
            }
        }
        if (heading == null) {
            final String kind = takeString(fields, KIND_KEYS);
            heading = kind != null
                    ? humanise(kind)
                    : DEFAULT_HEADING;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("#### ").append(heading).append("\n\n");
        if (NullSafe.isNonBlankString(message)) {
            sb.append(message).append("\n\n");
        }
        appendFields(sb, fields, "");
        return sb.toString().trim();
    }

    // The details of an error are often wrapped, e.g. {"type":"error","error":{"type":"overloaded_error",...}}.
    private static Map<String, Object> unwrap(final Map<String, Object> object) {
        if (!(object.get(ERROR_KEY) instanceof final Map<?, ?> inner)) {
            return new LinkedHashMap<>(object);
        }
        final Map<String, Object> fields = asFields(inner);
        for (final Entry<String, Object> entry : object.entrySet()) {
            final boolean wrapperType = "type".equals(entry.getKey()) && ERROR_KEY.equals(entry.getValue());
            if (!ERROR_KEY.equals(entry.getKey()) && !wrapperType) {
                fields.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return fields;
    }

    // Removes and returns the first field of those named that holds some text.
    private static String takeString(final Map<String, Object> fields, final List<String> keys) {
        for (final String key : keys) {
            if (fields.get(key) instanceof final String value && NullSafe.isNonBlankString(value)) {
                fields.remove(key);
                return value.trim();
            }
        }
        return null;
    }

    private static void appendFields(final StringBuilder sb, final Map<String, Object> fields, final String indent) {
        for (final Entry<String, Object> entry : fields.entrySet()) {
            final Object value = entry.getValue();
            if (value != null) {
                sb.append(indent).append("- **").append(humanise(entry.getKey())).append(":**");
                appendValue(sb, value, indent);
            }
        }
    }

    private static void appendValue(final StringBuilder sb, final Object value, final String indent) {
        if (value instanceof final Map<?, ?> map) {
            sb.append("\n");
            appendFields(sb, asFields(map), indent + INDENT);
        } else if (value instanceof final List<?> list && allStrings(list)) {
            sb.append(" ").append(String.join(", ", strings(list))).append("\n");
        } else if (value instanceof final List<?> list) {
            sb.append("\n");
            for (final Object item : list) {
                if (item != null) {
                    sb.append(indent).append(INDENT).append("-");
                    appendValue(sb, item, indent + INDENT);
                }
            }
        } else {
            sb.append(" ").append(value).append("\n");
        }
    }

    /// @param name A field name or code, e.g. `error_description`, `requestId` or `BAD_REQUEST`.
    /// @return The name as words, e.g. "Error description", "Request id" or "Bad request".
    static String humanise(final String name) {
        final StringBuilder sb = new StringBuilder();
        char previous = ' ';
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c == '_' || c == '-') {
                sb.append(' ');
            } else {
                if (Character.isUpperCase(c) && Character.isLowerCase(previous)) {
                    sb.append(' ');
                }
                sb.append(Character.toLowerCase(c));
            }
            previous = c;
        }
        final String words = sb.toString().trim();
        return words.isEmpty()
                ? name
                : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static boolean allStrings(final List<?> list) {
        for (final Object item : list) {
            if (!(item instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> strings(final List<?> list) {
        final List<String> strings = new ArrayList<>();
        for (final Object item : list) {
            strings.add((String) item);
        }
        return strings;
    }

    private static Map<String, Object> asFields(final Map<?, ?> map) {
        final Map<String, Object> fields = new LinkedHashMap<>();
        map.forEach((key, value) -> fields.put(String.valueOf(key), value));
        return fields;
    }

    private static Object toTree(final JSONValue value) {
        if (value == null || value.isNull() != null) {
            return null;
        }
        final JSONObject object = value.isObject();
        if (object != null) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (final String key : object.keySet()) {
                map.put(key, toTree(object.get(key)));
            }
            return map;
        }
        final JSONArray array = value.isArray();
        if (array != null) {
            final List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                list.add(toTree(array.get(i)));
            }
            return list;
        }
        final JSONString string = value.isString();
        return string != null
                ? string.stringValue()
                : value.toString();
    }
}
