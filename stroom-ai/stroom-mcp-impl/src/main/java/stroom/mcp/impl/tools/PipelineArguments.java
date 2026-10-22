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

import stroom.ai.impl.tools.sample.PyJson;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// The pipeline tools' arguments, read in the shapes models send them (the Stroom MCP server's `utils/params.py`
/// `ONE_OR_MORE`): a list given a single value, or as text (JSON, or a Python literal), is read as the list it
/// plainly is.
final class PipelineArguments {

    // Python's literals that JSON spells otherwise.
    private static final Pattern PYTHON_WORDS = Pattern.compile("\\b(?:True|False|None)\\b");

    private PipelineArguments() {
        // Static methods only.
    }

    /// A list argument as the call gives it: a list as it is; one value as a list of one; text holding a JSON
    /// list as that list, a JSON object as a list of it, comma-separated text (with no object in it) split, and
    /// other text as a list of it.
    ///
    /// @param value The argument's value, or null.
    /// @return Its items; none for null or blank text.
    static List<Object> oneOrMore(final Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof final List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof final String given) {
            final String text = given.strip();
            if (text.startsWith("[") && text.endsWith("]")) {
                final Object parsed = parsed(text);
                if (parsed instanceof final List<?> list) {
                    return new ArrayList<>(list);
                }
            }
            if (text.startsWith("{") && text.endsWith("}")) {
                final Object parsed = parsed(text);
                if (parsed != null) {
                    return List.of(parsed);
                }
            }
            if (text.contains(",") && !text.contains("{")) {
                return List.of(text.split(",")).stream()
                        .map(String::strip)
                        .filter(part -> !part.isEmpty())
                        .map(part -> (Object) part)
                        .toList();
            }
            return text.isEmpty()
                    ? List.of()
                    : List.of(text);
        }
        return List.of(value);
    }

    /// @param value A flag's value: a boolean, or text such as `true`.
    /// @return True if it is set.
    static boolean flag(final Object value) {
        return value instanceof final String text
                ? Boolean.parseBoolean(text.strip())
                : BuildPlan.truthy(value);
    }

    /// @param value An argument mapping text to text, e.g. `{"V1.2": "V1.3"}`, or that as JSON text.
    /// @param name  The argument's name, for a refusal.
    /// @return The mapping, in the order given; empty for none.
    /// @throws ToolRefusal if it isn't one.
    static Map<String, String> textMap(final Object value, final String name) {
        if (value == null || (value instanceof final String text && NullSafe.isBlankString(text))) {
            return Map.of();
        }
        final Object given = value instanceof final String text
                ? parsed(text.strip())
                : value;
        if (!(given instanceof final Map<?, ?> map)) {
            throw new ToolRefusal(name + " is an object of text replacements, e.g. {\"V1.2\": \"V1.3\"}");
        }
        final Map<String, String> read = new LinkedHashMap<>();
        map.forEach((from, to) -> read.put(String.valueOf(from), String.valueOf(to)));
        return read;
    }

    // A Python literal as JSON spells it.
    private static String asJson(final String word) {
        return switch (word) {
            case "True" -> "true";
            case "False" -> "false";
            default -> "null";
        };
    }

    // JSON text read; else, with no double quotes in it, as a Python literal (['Feed'], {'V1': 'V2'}, True) is
    // written; else null.
    private static Object parsed(final String text) {
        try {
            return PyJson.loads(text);
        } catch (final RuntimeException e) {
            if (text.contains("\"")) {
                return null;
            }
            try {
                return PyJson.loads(PYTHON_WORDS.matcher(text.replace('\'', '"'))
                        .replaceAll(word -> asJson(word.group())));
            } catch (final RuntimeException notPython) {
                return null;
            }
        }
    }
}
