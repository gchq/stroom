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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.ModelReader;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.sample.PyJson.JsonDecodeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The indexing tools' arguments, read as leniently as the Stroom MCP server's `utils/params.py` `ONE_OR_MORE` read
/// them (a single value is a list of one, a JSON or Python list given as text is parsed, a comma-separated text is
/// split), and its models (a field plan, planned fields, a discovery, shared templates) validated as pydantic
/// validated them, a refusal saying what is wrong.
final class IndexingArgs {

    private IndexingArgs() {
        // Static only.
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as given, or null.
    static Object raw(final ToolCall call, final String name) {
        return AbstractBuiltInTool.argsMap(call).get(name);
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as a list, as `ONE_OR_MORE` turns it into one; empty if absent.
    static List<Object> oneOrMore(final ToolCall call, final String name) {
        return oneOrMore(raw(call, name));
    }

    /// @param value A value given for a list.
    /// @return It as a list: a single value a list of one, a list literal parsed, a comma-separated text split.
    static List<Object> oneOrMore(final Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof final List<?> list) {
            return new ArrayList<>(list);
        }
        if (!(value instanceof final String given)) {
            return List.of(value);
        }
        final String text = given.strip();
        if (text.startsWith("[") && text.endsWith("]")) {
            final Object parsed = listLiteral(text);
            if (parsed instanceof final List<?> list) {
                return new ArrayList<>(list);
            }
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            try {
                return List.of(PyJson.loads(text));
            } catch (final JsonDecodeException e) {
                // Not JSON: read as text.
            }
        }
        if (text.contains(",") && !text.contains("{")) {
            final List<Object> parts = new ArrayList<>();
            for (final String part : text.split(",", -1)) {
                if (!part.strip().isEmpty()) {
                    parts.add(part.strip());
                }
            }
            return parts;
        }
        return text.isEmpty()
                ? List.of()
                : List.of(text);
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as strings ([#oneOrMore]).
    static List<String> strings(final ToolCall call, final String name) {
        return oneOrMore(call, name).stream()
                .filter(item -> item != null)
                .map(item -> item instanceof final String text
                        ? text
                        : PyJson.dumps(item))
                .toList();
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return True if it is true, or the text `true`.
    static boolean flag(final ToolCall call, final String name) {
        final Object value = raw(call, name);
        return Boolean.TRUE.equals(value)
               || (value instanceof final String text && "true".equalsIgnoreCase(text.strip()));
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as an object: one given, or one given as JSON text; null if absent or blank.
    /// @throws ToolRefusal for a value that isn't an object.
    static Object object(final ToolCall call, final String name) {
        final Object value = raw(call, name);
        if (value instanceof final String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return PyJson.loads(text);
            } catch (final JsonDecodeException e) {
                throw new ToolRefusal(name + " is text that is not JSON (" + e.msg() + " at character " + e.pos()
                                      + "): pass it as an object");
            }
        }
        return value;
    }

    /// @param call A call.
    /// @param name The plan's argument.
    /// @return The field plan given, or null.
    /// @throws ToolRefusal for one that isn't a valid plan.
    static FieldPlan plan(final ToolCall call, final String name) {
        final Object value = object(call, name);
        if (value == null) {
            return null;
        }
        try {
            return FieldPlan.parse(value);
        } catch (final MappingValidationException e) {
            throw new ToolRefusal(name + " isn't a valid field plan: " + e.getMessage());
        }
    }

    /// @param call A call.
    /// @param name The discovery's argument.
    /// @return The discovery given, or null.
    /// @throws ToolRefusal for one that isn't valid.
    @SuppressWarnings("unchecked")
    static Discovery discovery(final ToolCall call, final String name) {
        final Object value = object(call, name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new ToolRefusal(name + " is an object: {'input': ..., 'timestamp_field': ...}");
        }
        try {
            return Discovery.fromJson((Map<String, Object>) value);
        } catch (final MappingValidationException e) {
            throw new ToolRefusal(name + " isn't valid: " + e.getMessage());
        }
    }

    /// @param call A call.
    /// @param name The fields' argument.
    /// @return The planned fields given.
    /// @throws ToolRefusal for one that isn't valid.
    @SuppressWarnings("unchecked")
    static List<PlannedField> fields(final ToolCall call, final String name) {
        final List<PlannedField> fields = new ArrayList<>();
        for (final Object item : oneOrMore(call, name)) {
            if (!(item instanceof Map<?, ?>)) {
                throw new ToolRefusal(name + " are fields, each {'name': ..., 'type': ..., 'source': ...}, not "
                                      + PyJson.dumps(item));
            }
            try {
                fields.add(PlannedField.fromJson((Map<String, Object>) item));
            } catch (final MappingValidationException e) {
                throw new ToolRefusal(name + " isn't valid: " + e.getMessage());
            }
        }
        return fields;
    }

    /// @param call A call.
    /// @param name The shared templates' argument.
    /// @return The shared templates given.
    /// @throws ToolRefusal for one that isn't valid.
    static List<SharedTemplate> shared(final ToolCall call, final String name) {
        final List<SharedTemplate> shared = new ArrayList<>();
        for (final Object item : oneOrMore(call, name)) {
            final ModelReader reader = new ModelReader();
            try {
                shared.add(reader.orThrow("SharedTemplate", SharedTemplate.read(reader, item, List.of())));
            } catch (final MappingValidationException e) {
                throw new ToolRefusal(name + " isn't valid: " + e.getMessage());
            }
        }
        return shared;
    }

    // A JSON list, or a Python one (single quotes), or null.
    private static Object listLiteral(final String text) {
        try {
            return PyJson.loads(text);
        } catch (final JsonDecodeException e) {
            try {
                return PyJson.loads(text.replace('\'', '"'));
            } catch (final JsonDecodeException again) {
                return null;
            }
        }
    }
}
