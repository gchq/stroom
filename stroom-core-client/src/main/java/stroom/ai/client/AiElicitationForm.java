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

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;

import java.util.ArrayList;
import java.util.List;

/// Reads the form in an ELICITATION message: the fields an MCP server has asked the user to fill in.
final class AiElicitationForm {

    private AiElicitationForm() {
        // Static only.
    }

    /// @param json An ELICITATION message.
    /// @return The form's fields, in order.
    static List<Field> fields(final String json) {
        final List<Field> fields = new ArrayList<>();
        try {
            final JSONObject message = JSONParser.parseStrict(json).isObject();
            final JSONObject schema = object(message.get("schema"));
            final JSONObject properties = schema != null
                    ? object(schema.get("properties"))
                    : null;
            if (properties == null) {
                return fields;
            }
            final List<String> required = strings(schema.get("required"));
            for (final String name : properties.keySet()) {
                final JSONObject definition = object(properties.get(name));
                if (definition == null) {
                    continue;
                }
                final String type = text(definition, "type");
                final boolean multi = "array".equals(type);
                final JSONObject optionSource = multi
                        ? object(definition.get("items"))
                        : definition;
                final List<String> values = new ArrayList<>();
                final List<String> labels = new ArrayList<>();
                options(optionSource, values, labels);
                // Suggested answers to a question the user may also answer in their own words.
                final boolean custom = values.isEmpty() && definition.get("examples") != null;
                if (custom) {
                    values.addAll(strings(definition.get("examples")));
                    labels.addAll(values);
                }
                // What the server suggests, e.g. a proposed name: a list for a choice of several.
                final List<String> defaults = definition.get("default") != null
                                              && definition.get("default").isArray() != null
                        ? strings(definition.get("default"))
                        : text(definition, "default") != null
                                ? List.of(text(definition, "default"))
                                : List.of();
                fields.add(new Field(name,
                        text(definition, "title") != null
                                ? text(definition, "title")
                                : name,
                        text(definition, "description"),
                        type,
                        text(definition, "format"),
                        required.contains(name),
                        multi,
                        values,
                        labels,
                        defaults,
                        custom));
            }
        } catch (final RuntimeException e) {
            // Not a form that can be shown.
            fields.clear();
        }
        return fields;
    }

    private static void options(final JSONObject definition, final List<String> values, final List<String> labels) {
        if (definition == null) {
            return;
        }
        final JSONArray enumValues = definition.get("enum") != null
                ? definition.get("enum").isArray()
                : null;
        if (enumValues != null) {
            for (final String value : strings(enumValues)) {
                values.add(value);
                labels.add(value);
            }
            return;
        }
        final JSONValue choices = definition.get("oneOf") != null
                ? definition.get("oneOf")
                : definition.get("anyOf");
        final JSONArray array = choices != null
                ? choices.isArray()
                : null;
        for (int i = 0; array != null && i < array.size(); i++) {
            final JSONObject choice = object(array.get(i));
            final String value = choice != null
                    ? text(choice, "const")
                    : null;
            if (value != null) {
                values.add(value);
                labels.add(text(choice, "title") != null
                        ? text(choice, "title")
                        : value);
            }
        }
    }

    private static JSONObject object(final JSONValue value) {
        return value != null
                ? value.isObject()
                : null;
    }

    private static String text(final JSONObject object, final String name) {
        final JSONValue value = object.get(name);
        if (value == null || value.isNull() != null) {
            return null;
        }
        return value.isString() != null
                ? value.isString().stringValue()
                : value.toString();
    }

    private static List<String> strings(final JSONValue value) {
        final List<String> strings = new ArrayList<>();
        final JSONArray array = value != null
                ? value.isArray()
                : null;
        for (int i = 0; array != null && i < array.size(); i++) {
            strings.add(array.get(i).isString() != null
                    ? array.get(i).isString().stringValue()
                    : array.get(i).toString());
        }
        return strings;
    }


    // --------------------------------------------------------------------------------


    /// A field of a form.
    static final class Field {

        private final String name;
        private final String title;
        private final String description;
        private final String type;
        private final String format;
        private final boolean required;
        private final boolean multi;
        private final List<String> values;
        private final List<String> labels;
        private final List<String> defaults;
        private final boolean custom;

        private Field(final String name,
                      final String title,
                      final String description,
                      final String type,
                      final String format,
                      final boolean required,
                      final boolean multi,
                      final List<String> values,
                      final List<String> labels,
                      final List<String> defaults,
                      final boolean custom) {
            this.name = name;
            this.title = title;
            this.description = description;
            this.type = type;
            this.format = format;
            this.required = required;
            this.multi = multi;
            this.values = values;
            this.labels = labels;
            this.defaults = defaults;
            this.custom = custom;
        }

        /// @return True if the values are only suggestions, and the user may give any answer.
        boolean isCustom() {
            return custom;
        }

        String getName() {
            return name;
        }

        String getTitle() {
            return title;
        }

        String getDescription() {
            return description;
        }

        String getType() {
            return type;
        }

        String getFormat() {
            return format;
        }

        boolean isRequired() {
            return required;
        }

        /// @return True if the user may choose several values.
        boolean isMulti() {
            return multi;
        }

        /// @return The values the user may choose from, or an empty list if they may enter anything.
        List<String> getValues() {
            return values;
        }

        /// @return A label for each value.
        List<String> getLabels() {
            return labels;
        }

        /// @return The value the server suggests (several for a choice of several), or an empty list if none.
        List<String> getDefaults() {
            return defaults;
        }

        /// @return The value the server suggests, or null if none.
        String getDefault() {
            return defaults.isEmpty()
                    ? null
                    : defaults.get(0);
        }
    }
}
