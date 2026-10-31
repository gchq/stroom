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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.sample.ToolJson.JsonDecodeException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// The tools' arguments, read in the shapes models send them: a list given a single value, or as text (JSON, or a
/// Python literal), is read as the list it plainly is; a flag given as text; an object given as JSON text. A
/// refusal says what is wrong and where, in the same words for every tool.
public final class ToolArgs {

    // Python's literals that JSON spells otherwise.
    private static final Pattern PYTHON_WORDS = Pattern.compile("\\b(?:True|False|None)\\b");

    private ToolArgs() {
        // Static methods only.
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as given (a map, a list, text, ...), or null if it wasn't given.
    public static Object raw(final ToolCall call, final String name) {
        return AbstractBuiltInTool.argsMap(call).get(name);
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as a list, as [#oneOrMore(Object)] reads it; empty if absent.
    public static List<Object> oneOrMore(final ToolCall call, final String name) {
        return oneOrMore(raw(call, name));
    }

    /// A list argument as the call gives it: a list as it is; one value as a list of one; text holding a JSON
    /// (or Python) list as that list, a JSON object as a list of it, comma-separated text (with no object in it)
    /// split, and other text as a list of it.
    ///
    /// @param value The argument's value, or null.
    /// @return Its items; none for null or blank text.
    public static List<Object> oneOrMore(final Object value) {
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
            final Object parsed = literal(text);
            if (parsed instanceof final List<?> list) {
                return new ArrayList<>(list);
            }
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            final Object parsed = literal(text);
            if (parsed != null) {
                return List.of(parsed);
            }
        }
        if (text.contains(",") && !text.contains("{")) {
            final List<Object> parts = new ArrayList<>();
            for (final String part : text.split(",")) {
                if (!part.isBlank()) {
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
    /// @return Its items ([#oneOrMore(ToolCall, String)]) as strings: text as it is, anything else as JSON; nulls
    /// left out.
    public static List<String> strings(final ToolCall call, final String name) {
        return oneOrMore(call, name).stream()
                .filter(item -> item != null)
                .map(item -> item instanceof final String text
                        ? text
                        : ToolJson.dumps(item))
                .toList();
    }

    /// @param call A call.
    /// @param name A true or false argument.
    /// @return True if it is set ([#flag(Object)]).
    public static boolean flag(final ToolCall call, final String name) {
        return flag(raw(call, name));
    }

    /// @param value A flag's value: a boolean, or text such as `true`.
    /// @return True if it is set: text that is `true` (in any case), or any other value that is something
    /// ([JsonValues#truthy(Object)]).
    public static boolean flag(final Object value) {
        return value instanceof final String text
                ? Boolean.parseBoolean(text.strip())
                : JsonValues.truthy(value);
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return Its value as a JSON value: one given, or one given as JSON text; null if absent or blank.
    /// @throws ToolRefusal for text that isn't JSON.
    public static Object object(final ToolCall call, final String name) {
        final Object value = raw(call, name);
        if (value instanceof final String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return ToolJson.loads(text);
            } catch (final JsonDecodeException e) {
                throw notJson(name, where(e), "pass it as an object");
            }
        }
        return value;
    }

    /// @param call A call.
    /// @param name An argument's name.
    /// @return It as a JSON object: a map as given, or text that is one; null if it wasn't given.
    /// @throws ToolRefusal if it is neither.
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(final ToolCall call, final String name) {
        final Object value = raw(call, name);
        if (value == null) {
            return null;
        }
        if (value instanceof final Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof final String text && text.strip().startsWith("{")) {
            final Object parsed;
            try {
                parsed = ToolJson.loads(text);
            } catch (final JsonDecodeException e) {
                throw notJson(name, where(e), "pass it as an object");
            }
            if (parsed instanceof final Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        }
        throw new ToolRefusal(name + " is an object, not " + ToolText.repr(String.valueOf(value)));
    }

    /// @param name   The argument's name.
    /// @param why    What is wrong with the text, e.g. `Expecting value at character 3` ([#where]).
    /// @param advice What to pass instead, e.g. `pass it as an object`, or null.
    /// @return The refusal of text given for a JSON value that isn't JSON, worded as every tool words it:
    /// `{name} is text that is not JSON ({why}): {advice}`.
    public static ToolRefusal notJson(final String name, final String why, final String advice) {
        return new ToolRefusal(name + " is text that is not JSON (" + why + ")" + (advice == null
                ? ""
                : ": " + advice));
    }

    /// @param e Why a text isn't JSON.
    /// @return What is wrong and where, as every tool says it: `{e.msg} at character {e.pos}`.
    public static String where(final JsonDecodeException e) {
        return e.msg() + " at character " + e.pos();
    }

    /// @param text Text that may be a JSON value or a Python literal.
    /// @return The JSON value it holds; else, with no double quotes in it, the value of the Python literal it is
    /// (`['Feed']`, `{'V1': 'V2'}`, `True`), read as JSON with Python's quotes and words swapped; else null.
    public static Object literal(final String text) {
        try {
            return ToolJson.loads(text);
        } catch (final RuntimeException e) {
            if (text.contains("\"")) {
                return null;
            }
            try {
                return ToolJson.loads(PYTHON_WORDS.matcher(text.replace('\'', '"'))
                        .replaceAll(word -> asJson(word.group())));
            } catch (final RuntimeException notPython) {
                return null;
            }
        }
    }

    // A Python literal as JSON spells it.
    private static String asJson(final String word) {
        return switch (word) {
            case "True" -> "true";
            case "False" -> "false";
            default -> "null";
        };
    }
}
