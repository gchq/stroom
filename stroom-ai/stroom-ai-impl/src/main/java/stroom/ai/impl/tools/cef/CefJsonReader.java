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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.MappingValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

// Reads the CEF plan's models from JSON-like values as pydantic validates them in the Stroom MCP server (lax mode),
// collecting each error with pydantic's location, type and message.
final class CefJsonReader {

    private static final Set<String> TRUE_WORDS = Set.of("true", "t", "yes", "y", "on", "1");
    private static final Set<String> FALSE_WORDS = Set.of("false", "f", "no", "n", "off", "0");

    private final List<MappingError> errors = new ArrayList<>();

    /// The result, or the errors as pydantic raises them.
    ///
    /// @param model  The model read, e.g. `CefPlan`.
    /// @param result What was read.
    /// @param <T>    What was read.
    /// @return The result.
    /// @throws MappingValidationException if anything read was wrong.
    <T> T orThrow(final String model, final T result) {
        if (!errors.isEmpty()) {
            throw new MappingValidationException(model, errors);
        }
        return result;
    }

    /// Notes an error.
    ///
    /// @param loc     Where it is.
    /// @param type    pydantic's error type.
    /// @param message pydantic's message.
    /// @param input   The value given there.
    void error(final List<Object> loc, final String type, final String message, final Object input) {
        errors.add(new MappingError(List.copyOf(loc), type, message, input));
    }

    /// Notes a model's own check failing.
    ///
    /// @param loc     Where it is.
    /// @param message The model's own check's words.
    /// @param input   The value given there.
    void valueError(final List<Object> loc, final String message, final Object input) {
        error(loc, "value_error", "Value error, " + message, input);
    }

    /// Notes a nested model's errors, below its place.
    ///
    /// @param loc  Where the nested model is.
    /// @param more Its errors.
    void addAll(final List<Object> loc, final List<MappingError> more) {
        for (final MappingError error : more) {
            final List<Object> at = new ArrayList<>(loc);
            at.addAll(error.loc());
            errors.add(new MappingError(at, error.type(), error.message(), error.input()));
        }
    }

    /// @return How many errors are noted.
    int errorCount() {
        return errors.size();
    }

    /// @param input The value given.
    /// @param loc   Where it is.
    /// @param model The model's name.
    /// @return Its fields, or null (with an error) when it isn't a map.
    @SuppressWarnings("unchecked")
    Map<String, Object> model(final Object input, final List<Object> loc, final String model) {
        if (input instanceof final Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        error(loc, "model_type", "Input should be a valid dictionary or instance of " + model, input);
        return null;
    }

    /// @param data A model's fields.
    /// @param loc  Where the model is.
    /// @param key  The field.
    /// @return Its text, or null (with an error) if it is missing or not text.
    String requiredString(final Map<String, Object> data, final List<Object> loc, final String key) {
        if (!data.containsKey(key)) {
            error(at(loc, key), "missing", "Field required", data);
            return null;
        }
        return string(data.get(key), at(loc, key));
    }

    /// @param data A model's fields.
    /// @param loc  Where the model is.
    /// @param key  The field.
    /// @return Its text, or null if it is missing or null (with an error if it is not text).
    String optionalString(final Map<String, Object> data, final List<Object> loc, final String key) {
        final Object value = data.get(key);
        return value == null
                ? null
                : string(value, at(loc, key));
    }

    /// @param value A value.
    /// @param loc   Where it is.
    /// @return It, or null (with an error) if it is not text.
    String string(final Object value, final List<Object> loc) {
        if (value instanceof final String text) {
            return text;
        }
        error(loc, "string_type", "Input should be a valid string", value);
        return null;
    }

    /// @param data         A model's fields.
    /// @param loc          Where the model is.
    /// @param key          The field.
    /// @param defaultValue Its default.
    /// @param choices      The values it takes.
    /// @return Its value, or the default (with an error if it is not one of the choices).
    String literal(final Map<String, Object> data, final List<Object> loc, final String key,
                   final String defaultValue, final List<String> choices) {
        if (!data.containsKey(key)) {
            return defaultValue;
        }
        final Object value = data.get(key);
        if (value instanceof final String text && choices.contains(text)) {
            return text;
        }
        error(at(loc, key), "literal_error", "Input should be " + expected(choices), value);
        return defaultValue;
    }

    /// @param data         A model's fields.
    /// @param loc          Where the model is.
    /// @param key          The field.
    /// @param defaultValue Its default.
    /// @return Its value as pydantic reads a boolean (0, 1, yes, off, ...), or the default (with an error).
    boolean bool(final Map<String, Object> data, final List<Object> loc, final String key,
                 final boolean defaultValue) {
        if (!data.containsKey(key)) {
            return defaultValue;
        }
        final Object value = data.get(key);
        switch (value) {
            case final Boolean bool -> {
                return bool;
            }
            case final String text -> {
                final String word = text.toLowerCase(Locale.ROOT);
                if (TRUE_WORDS.contains(word) || FALSE_WORDS.contains(word)) {
                    return TRUE_WORDS.contains(word);
                }
                error(at(loc, key), "bool_parsing", "Input should be a valid boolean, unable to interpret input",
                        value);
            }
            case final Number number -> {
                final BigDecimal decimal = new BigDecimal(number.toString());
                if (decimal.compareTo(BigDecimal.ZERO) == 0 || decimal.compareTo(BigDecimal.ONE) == 0) {
                    return decimal.compareTo(BigDecimal.ONE) == 0;
                }
                error(at(loc, key), "bool_parsing", "Input should be a valid boolean, unable to interpret input",
                        value);
            }
            case null, default -> error(at(loc, key), "bool_type", "Input should be a valid boolean", value);
        }
        return defaultValue;
    }

    /// @param value A value.
    /// @param loc   Where it is.
    /// @return It as text by text (empty for null; with errors for what isn't).
    Map<String, String> stringMap(final Object value, final List<Object> loc) {
        final Map<String, String> out = new LinkedHashMap<>();
        if (value == null) {
            return out;
        }
        if (!(value instanceof final Map<?, ?> map)) {
            error(loc, "dict_type", "Input should be a valid dictionary", value);
            return out;
        }
        map.forEach((k, v) -> {
            final String text = string(v, at(loc, String.valueOf(k)));
            if (text != null) {
                out.put(String.valueOf(k), text);
            }
        });
        return out;
    }

    /// @param value A value.
    /// @param loc   Where it is.
    /// @param item  How each item is read (null if it doesn't validate).
    /// @param <T>   What the items are.
    /// @return The items read (empty for null; with an error if it isn't a list).
    <T> List<T> list(final Object value, final List<Object> loc,
                     final BiFunction<Object, List<Object>, T> item) {
        final List<T> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        if (!(value instanceof final List<?> items)) {
            error(loc, "list_type", "Input should be a valid list", value);
            return out;
        }
        for (int i = 0; i < items.size(); i++) {
            final T read = item.apply(items.get(i), at(loc, i));
            if (read != null) {
                out.add(read);
            }
        }
        return out;
    }

    /// @param loc  A location.
    /// @param part A key or position below it.
    /// @return The location below.
    static List<Object> at(final List<Object> loc, final Object part) {
        final List<Object> out = new ArrayList<>(loc);
        out.add(part);
        return out;
    }

    // pydantic's list of the literal values: 'a', 'b' or 'c'.
    private static String expected(final List<String> choices) {
        final List<String> quoted = choices.stream().map(choice -> "'" + choice + "'").toList();
        if (quoted.size() == 1) {
            return quoted.getFirst();
        }
        return String.join(", ", quoted.subList(0, quoted.size() - 1)) + " or " + quoted.getLast();
    }
}
