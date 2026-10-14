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

package stroom.ai.impl;

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Checks the forms MCP servers ask users to fill in (MCP elicitation), and the users' answers.
///
/// Only flat forms are supported, as MCP allows: text (optionally an email address, URI, date or date-time),
/// numbers, whole numbers, true/false, a choice of one value, and (as a list of values) a choice of several.
/// Anything else, and very large forms, are declined without being shown.
final class ElicitationSchema {

    static final int MAX_FIELDS = 20;
    static final int MAX_SCHEMA_CHARS = 16_000;
    static final int MAX_MESSAGE_CHARS = 2_000;
    static final int MAX_TEXT_CHARS = 10_000;

    private static final Set<String> STRING_FORMATS = Set.of("email", "uri", "date", "date-time");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern DATE_TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?"
                                                             + "(Z|[+-]\\d{2}:\\d{2})?");

    private ElicitationSchema() {
        // Static only.
    }

    /// @param message What the server says.
    /// @param schema  The form.
    /// @return Why the form can't be shown, or null if it can.
    static String check(final String message, final Map<String, Object> schema) {
        if (NullSafe.string(message).length() > MAX_MESSAGE_CHARS) {
            return "its message is too long";
        }
        if (schema == null) {
            return "it has no form";
        }
        if (JsonUtil.writeValueAsString(schema, false).length() > MAX_SCHEMA_CHARS) {
            return "its form is too large";
        }
        if (!"object".equals(schema.get("type"))) {
            return "its form is not an object";
        }
        final Map<String, Object> properties = properties(schema);
        if (properties == null) {
            return "its form has no fields";
        }
        if (properties.size() > MAX_FIELDS) {
            return "its form has more than " + MAX_FIELDS + " fields";
        }
        for (final Map.Entry<String, Object> entry : properties.entrySet()) {
            final String error = checkField(entry.getValue());
            if (error != null) {
                return "its field '" + entry.getKey() + "' " + error;
            }
        }
        return null;
    }

    /// Converts the user's answer to what the server expects, checking it against the form.
    ///
    /// @param schema The form.
    /// @param answer Each field's value as the form gave it: text, a number, "true" or "false", or for a choice of
    ///               several values, a JSON list of them. Blank fields are left out.
    /// @return The answer to send to the server.
    /// @throws IllegalArgumentException If the answer does not fit the form.
    static Map<String, Object> content(final Map<String, Object> schema, final Map<String, String> answer) {
        final Map<String, Object> properties = Objects.requireNonNullElse(properties(schema), Map.of());
        final List<String> required = strings(schema.get("required"));
        for (final String name : NullSafe.map(answer).keySet()) {
            if (!properties.containsKey(name)) {
                throw new IllegalArgumentException("The form has no field '" + name + "'.");
            }
        }
        final Map<String, Object> content = new LinkedHashMap<>();
        properties.forEach((name, definition) -> {
            final String text = NullSafe.map(answer).get(name);
            if (NullSafe.isBlankString(text)) {
                if (required.contains(name)) {
                    throw new IllegalArgumentException("Fill in '" + label(name, definition) + "'.");
                }
                return;
            }
            content.put(name, convert(name, asMap(definition), text.strip()));
        });
        return content;
    }

    private static String checkField(final Object definitionObject) {
        final Map<String, Object> definition = asMap(definitionObject);
        if (definition == null) {
            return "is not described";
        }
        final Object type = definition.get("type");
        if ("string".equals(type)) {
            final Object format = definition.get("format");
            if (format != null && !STRING_FORMATS.contains(format)) {
                return "has the unsupported format '" + format + "'";
            }
            if (definition.containsKey("oneOf") && options(definition).isEmpty()) {
                return "has no choices";
            }
            return null;
        } else if ("number".equals(type) || "integer".equals(type) || "boolean".equals(type)) {
            return null;
        } else if ("array".equals(type)) {
            final Map<String, Object> items = asMap(definition.get("items"));
            return items != null && !options(items).isEmpty()
                    ? null
                    : "is a list that is not a choice of values";
        }
        return "has the unsupported type '" + type + "'";
    }

    private static Object convert(final String name, final Map<String, Object> definition, final String text) {
        final String label = label(name, definition);
        final Object type = definition.get("type");
        if ("number".equals(type) || "integer".equals(type)) {
            final double number;
            try {
                number = "integer".equals(type)
                        ? Long.parseLong(text)
                        : Double.parseDouble(text);
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("'" + label + "' must be a " + ("integer".equals(type)
                        ? "whole number"
                        : "number") + ".");
            }
            checkRange(label, definition, number);
            return "integer".equals(type)
                    ? (Object) Long.parseLong(text)
                    : (Object) number;
        } else if ("boolean".equals(type)) {
            if (!"true".equals(text) && !"false".equals(text)) {
                throw new IllegalArgumentException("'" + label + "' must be true or false.");
            }
            return Boolean.parseBoolean(text);
        } else if ("array".equals(type)) {
            final List<String> chosen;
            try {
                chosen = strings(JsonUtil.readValue(text, List.class));
            } catch (final RuntimeException e) {
                throw new IllegalArgumentException("'" + label + "' must be a list of choices.");
            }
            final List<String> allowed = options(asMap(definition.get("items")));
            for (final String value : chosen) {
                if (!allowed.contains(value)) {
                    throw new IllegalArgumentException("'" + value + "' is not one of the choices for '" + label
                                                       + "'.");
                }
            }
            checkCount(label, definition, chosen.size());
            return chosen;
        }
        return checkText(label, definition, text);
    }

    private static String checkText(final String label, final Map<String, Object> definition, final String text) {
        final List<String> allowed = options(definition);
        if (!allowed.isEmpty() && !allowed.contains(text)) {
            throw new IllegalArgumentException("'" + text + "' is not one of the choices for '" + label + "'.");
        }
        final int maxLength = Math.min(intValue(definition.get("maxLength"), MAX_TEXT_CHARS), MAX_TEXT_CHARS);
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("'" + label + "' may be at most " + maxLength + " characters.");
        }
        if (text.length() < intValue(definition.get("minLength"), 0)) {
            throw new IllegalArgumentException("'" + label + "' must be at least "
                                               + intValue(definition.get("minLength"), 0) + " characters.");
        }
        final Object format = definition.get("format");
        if ("email".equals(format) && !text.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("'" + label + "' must be an email address.");
        } else if ("date".equals(format) && !DATE.matcher(text).matches()) {
            throw new IllegalArgumentException("'" + label + "' must be a date, e.g. 2026-10-15.");
        } else if ("date-time".equals(format) && !DATE_TIME.matcher(text).matches()) {
            throw new IllegalArgumentException("'" + label + "' must be a date and time, e.g. "
                                               + "2026-10-15T09:30:00Z.");
        } else if ("uri".equals(format) && !text.matches("[a-zA-Z][a-zA-Z0-9+.-]*:\\S+")) {
            throw new IllegalArgumentException("'" + label + "' must be a URI.");
        }
        return text;
    }

    private static void checkRange(final String label, final Map<String, Object> definition, final double number) {
        final Object minimum = definition.get("minimum");
        final Object maximum = definition.get("maximum");
        if (minimum instanceof final Number min && number < min.doubleValue()) {
            throw new IllegalArgumentException("'" + label + "' must be at least " + min + ".");
        }
        if (maximum instanceof final Number max && number > max.doubleValue()) {
            throw new IllegalArgumentException("'" + label + "' must be at most " + max + ".");
        }
    }

    private static void checkCount(final String label, final Map<String, Object> definition, final int count) {
        if (count < intValue(definition.get("minItems"), 0)) {
            throw new IllegalArgumentException("Choose at least " + intValue(definition.get("minItems"), 0)
                                               + " for '" + label + "'.");
        }
        if (count > intValue(definition.get("maxItems"), Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("Choose at most " + intValue(definition.get("maxItems"), 0)
                                               + " for '" + label + "'.");
        }
    }

    /// @return The values a field may take, from its `enum` or `oneOf`/`anyOf` list of `const` values, or an
    /// empty list if it may take any.
    static List<String> options(final Map<String, Object> definition) {
        if (definition == null) {
            return List.of();
        }
        if (definition.get("enum") instanceof final List<?> values) {
            return strings(values);
        }
        final Object choices = definition.get("oneOf") != null
                ? definition.get("oneOf")
                : definition.get("anyOf");
        final List<String> options = new ArrayList<>();
        if (choices instanceof final List<?> list) {
            for (final Object choice : list) {
                final Map<String, Object> map = asMap(choice);
                if (map != null && map.get("const") != null) {
                    options.add(String.valueOf(map.get("const")));
                }
            }
        }
        return options;
    }

    private static String label(final String name, final Map<String, Object> definition) {
        return definition != null && definition.get("title") instanceof final String title && !title.isBlank()
                ? title
                : name;
    }

    private static String label(final String name, final Object definition) {
        return label(name, asMap(definition));
    }

    private static Map<String, Object> properties(final Map<String, Object> schema) {
        return asMap(schema.get("properties"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object object) {
        return object instanceof Map<?, ?>
                ? (Map<String, Object>) object
                : null;
    }

    private static List<String> strings(final Object object) {
        final List<String> strings = new ArrayList<>();
        if (object instanceof final List<?> list) {
            for (final Object item : list) {
                if (item != null) {
                    strings.add(String.valueOf(item));
                }
            }
        }
        return strings;
    }

    private static int intValue(final Object object, final int fallback) {
        return object instanceof final Number number
                ? number.intValue()
                : fallback;
    }
}
