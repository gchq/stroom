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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Reads JSON-like values (maps, lists, strings, numbers, booleans, null) into the mapping's models as pydantic's
/// lax mode reads them in the Stroom MCP server, collecting every [MappingError] with pydantic's wording: a string
/// must be a string; a boolean may be given as 0 or 1 or a word such as `yes` or `off`; an integer as a whole
/// number, or a string or float holding one; keys a model doesn't have are ignored.
final class ModelReader {

    private static final Set<String> TRUE_WORDS = Set.of("1", "on", "t", "true", "y", "yes");
    private static final Set<String> FALSE_WORDS = Set.of("0", "off", "f", "false", "n", "no");

    private final List<MappingError> errors = new ArrayList<>();

    /// @return The errors found so far.
    List<MappingError> errors() {
        return errors;
    }

    /// @param model  The model read, for the exception's message.
    /// @param result What was read.
    /// @param <T>    The model.
    /// @return The result.
    /// @throws MappingValidationException if anything didn't validate.
    <T> T orThrow(final String model, final T result) {
        if (!errors.isEmpty() || result == null) {
            throw new MappingValidationException(model, errors);
        }
        return result;
    }

    /// Notes an error.
    ///
    /// @param loc     Where it is.
    /// @param type    pydantic's error type.
    /// @param message pydantic's message.
    /// @param input   The value given.
    void error(final List<Object> loc, final String type, final String message, final Object input) {
        errors.add(new MappingError(List.copyOf(loc), type, message, input));
    }

    /// A model's own check failing, as a ValueError raised in a pydantic validator.
    void valueError(final List<Object> loc, final String message, final Object input) {
        error(loc, "value_error", "Value error, " + message, input);
    }

    /// @param input The value given for a model.
    /// @param loc   Where.
    /// @param model The model's name.
    /// @return The value as a map of its fields, or null (with an error) if it isn't one.
    @SuppressWarnings("unchecked")
    Fields fields(final Object input, final List<Object> loc, final String model) {
        if (input instanceof final Map<?, ?> map) {
            return new Fields((Map<String, Object>) map, loc);
        }
        error(loc, "model_type", "Input should be a valid dictionary or instance of " + model, input);
        return null;
    }

    /// @param loc  A place.
    /// @param part A key or list position in it.
    /// @return The place below it.
    static List<Object> at(final List<Object> loc, final Object part) {
        final List<Object> out = new ArrayList<>(loc);
        out.add(part);
        return out;
    }

    // --- values ---

    /// @param value A value.
    /// @param loc   Where it is.
    /// @return It as a string, or null (with an error) if it isn't one.
    String string(final Object value, final List<Object> loc) {
        if (value instanceof final String string) {
            return string;
        }
        error(loc, "string_type", "Input should be a valid string", value);
        return null;
    }

    /// @param value A value.
    /// @param loc   Where it is.
    /// @return It as a boolean (as pydantic reads 0, 1, yes, off, ...), or null (with an error).
    Boolean bool(final Object value, final List<Object> loc) {
        switch (value) {
            case final Boolean bool -> {
                return bool;
            }
            case final String string -> {
                final String word = string.toLowerCase();
                if (TRUE_WORDS.contains(word)) {
                    return true;
                }
                if (FALSE_WORDS.contains(word)) {
                    return false;
                }
                error(loc, "bool_parsing", "Input should be a valid boolean, unable to interpret input", value);
                return null;
            }
            case final Double number -> {
                if (number == 0 || number == 1) {
                    return number == 1;
                }
                error(loc, "bool_type", "Input should be a valid boolean", value);
                return null;
            }
            case final Number number -> {
                final BigInteger whole = new BigDecimal(number.toString()).toBigInteger();
                if (whole.equals(BigInteger.ZERO) || whole.equals(BigInteger.ONE)) {
                    return whole.equals(BigInteger.ONE);
                }
                error(loc, "bool_parsing", "Input should be a valid boolean, unable to interpret input", value);
                return null;
            }
            case null, default -> {
                error(loc, "bool_type", "Input should be a valid boolean", value);
                return null;
            }
        }
    }

    /// @param value A value.
    /// @param loc   Where it is.
    /// @param least The least it may be.
    /// @return It as a whole number, or null (with an error).
    Long integer(final Object value, final List<Object> loc, final int least) {
        final Long number = integer(value, loc);
        if (number != null && number < least) {
            error(loc, "greater_than_equal", "Input should be greater than or equal to " + least, value);
            return null;
        }
        return number;
    }

    private Long integer(final Object value, final List<Object> loc) {
        switch (value) {
            case final Boolean bool -> {
                return bool
                        ? 1L
                        : 0L;
            }
            case final Integer number -> {
                return number.longValue();
            }
            case final Long number -> {
                return number;
            }
            case final BigInteger number -> {
                if (number.bitLength() < 64) {
                    return number.longValue();
                }
                return tooBig(value, loc);
            }
            case final Double number -> {
                if (number.isNaN() || number.isInfinite() || number != Math.rint(number)) {
                    error(loc, "int_from_float", "Input should be a valid integer, got a number with a fractional "
                                                 + "part", value);
                    return null;
                }
                if (Math.abs(number) < 9.2e18) {
                    return number.longValue();
                }
                return tooBig(value, loc);
            }
            case final String string -> {
                try {
                    return new BigDecimal(string.strip()).toBigIntegerExact().longValueExact();
                } catch (final NumberFormatException | ArithmeticException e) {
                    error(loc, "int_parsing", "Input should be a valid integer, unable to parse string as an integer",
                            value);
                    return null;
                }
            }
            case null, default -> {
                error(loc, "int_type", "Input should be a valid integer", value);
                return null;
            }
        }
    }

    private Long tooBig(final Object value, final List<Object> loc) {
        error(loc, "int_parsing_size", "Unable to parse input string as an integer, exceeded maximum size", value);
        return null;
    }

    /// @param value   A value.
    /// @param loc     Where it is.
    /// @param choices The values it may be.
    /// @return It, or null (with an error) if it isn't one of them.
    String literal(final Object value, final List<Object> loc, final List<String> choices) {
        if (value instanceof final String string && choices.contains(string)) {
            return string;
        }
        error(loc, "literal_error", "Input should be " + expected(choices), value);
        return null;
    }

    // pydantic's list of the literal values: 'a', 'b' or 'c'.
    private static String expected(final List<String> choices) {
        final List<String> quoted = choices.stream().map(choice -> "'" + choice + "'").toList();
        if (quoted.size() == 1) {
            return quoted.getFirst();
        }
        return String.join(", ", quoted.subList(0, quoted.size() - 1)) + " or " + quoted.getLast();
    }

    /// @param value     A value.
    /// @param loc       Where it is.
    /// @param items     How each item is read.
    /// @param minLength The fewest items it may have.
    /// @param <T>       What the items are.
    /// @return It as a list of items read, or null (with errors).
    @SuppressWarnings("unchecked")
    <T> List<T> list(final Object value,
                     final List<Object> loc,
                     final ItemReader<T> items,
                     final int minLength) {
        if (!(value instanceof List)) {
            error(loc, "list_type", "Input should be a valid list", value);
            return null;
        }
        final List<Object> given = (List<Object>) value;
        final List<T> out = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < given.size(); i++) {
            final T item = items.read(given.get(i), at(loc, i));
            if (item == null) {
                ok = false;
            } else {
                out.add(item);
            }
        }
        if (!ok) {
            return null;
        }
        if (out.size() < minLength) {
            error(loc, "too_short", "List should have at least " + minLength + " item" + (minLength == 1
                    ? ""
                    : "s") + " after validation, not " + out.size(), value);
            return null;
        }
        return Collections.unmodifiableList(out);
    }

    /// @param value A value.
    /// @param loc   Where it is.
    /// @return It as a map of strings, or null (with errors).
    Map<String, String> stringMap(final Object value, final List<Object> loc) {
        if (!(value instanceof final Map<?, ?> given)) {
            error(loc, "dict_type", "Input should be a valid dictionary", value);
            return null;
        }
        final Map<String, String> out = new LinkedHashMap<>();
        boolean ok = true;
        for (final Map.Entry<?, ?> entry : given.entrySet()) {
            final String key = String.valueOf(entry.getKey());
            final String item = string(entry.getValue(), at(loc, key));
            if (item == null) {
                ok = false;
            } else {
                out.put(key, item);
            }
        }
        return ok
                ? Collections.unmodifiableMap(out)
                : null;
    }


    // --------------------------------------------------------------------------------


    /// Reads one item of a list.
    ///
    /// @param <T> What it reads.
    @FunctionalInterface
    interface ItemReader<T> {

        /// @param value The item given.
        /// @param loc   Where it is.
        /// @return It read, or null (with errors) if it doesn't validate.
        T read(Object value, List<Object> loc);
    }


    // --------------------------------------------------------------------------------


    /// One model's fields, read in turn: a key not given takes its default, or is a `missing` error if it has none.
    final class Fields {

        private final Map<String, Object> data;
        private final List<Object> loc;
        private final int before;

        private Fields(final Map<String, Object> data, final List<Object> loc) {
            this.data = data;
            this.loc = loc;
            this.before = errors.size();
        }

        /// @return The input, as given.
        Map<String, Object> input() {
            return data;
        }

        /// @return Where the model is.
        List<Object> loc() {
            return loc;
        }

        /// @return True if no field had an error, so the model's own checks run.
        boolean ok() {
            return errors.size() == before;
        }

        /// @param key A field.
        /// @return True if it is given.
        boolean has(final String key) {
            return data.containsKey(key);
        }

        private boolean missing(final String key) {
            if (data.containsKey(key)) {
                return false;
            }
            error(at(loc, key), "missing", "Field required", data);
            return true;
        }

        /// @param key A required string field.
        /// @return Its value, or null (with an error).
        String requiredString(final String key) {
            return missing(key)
                    ? null
                    : ModelReader.this.string(data.get(key), at(loc, key));
        }

        /// @param key An optional string field.
        /// @return Its value, or null.
        String optionalString(final String key) {
            final Object value = data.get(key);
            return value == null
                    ? null
                    : ModelReader.this.string(value, at(loc, key));
        }

        /// @param key          A string field.
        /// @param defaultValue Its default.
        /// @return Its value.
        String string(final String key, final String defaultValue) {
            return data.containsKey(key)
                    ? ModelReader.this.string(data.get(key), at(loc, key))
                    : defaultValue;
        }

        /// @param key     A required field of fixed values.
        /// @param choices The values.
        /// @return Its value, or null (with an error).
        String requiredLiteral(final String key, final List<String> choices) {
            return missing(key)
                    ? null
                    : ModelReader.this.literal(data.get(key), at(loc, key), choices);
        }

        /// @param key          A field of fixed values.
        /// @param defaultValue Its default.
        /// @param choices      The values.
        /// @return Its value.
        String literal(final String key, final String defaultValue, final List<String> choices) {
            return data.containsKey(key)
                    ? ModelReader.this.literal(data.get(key), at(loc, key), choices)
                    : defaultValue;
        }

        /// @param key     An optional field of fixed values.
        /// @param choices The values.
        /// @return Its value, or null.
        String optionalLiteral(final String key, final List<String> choices) {
            final Object value = data.get(key);
            return value == null
                    ? null
                    : ModelReader.this.literal(value, at(loc, key), choices);
        }

        /// @param key          A boolean field.
        /// @param defaultValue Its default.
        /// @return Its value.
        boolean bool(final String key, final boolean defaultValue) {
            if (!data.containsKey(key)) {
                return defaultValue;
            }
            final Boolean value = ModelReader.this.bool(data.get(key), at(loc, key));
            return value == null
                    ? defaultValue
                    : value;
        }

        /// @param key An optional boolean field.
        /// @return Its value, or null.
        Boolean optionalBool(final String key) {
            final Object value = data.get(key);
            return value == null
                    ? null
                    : ModelReader.this.bool(value, at(loc, key));
        }

        /// @param key          A whole number field.
        /// @param defaultValue Its default.
        /// @param least        The least it may be.
        /// @return Its value.
        long integer(final String key, final long defaultValue, final int least) {
            if (!data.containsKey(key)) {
                return defaultValue;
            }
            final Long value = ModelReader.this.integer(data.get(key), at(loc, key), least);
            return value == null
                    ? defaultValue
                    : value;
        }

        /// @param key A field holding a list of strings (empty by default).
        /// @return Its value.
        List<String> strings(final String key) {
            return strings(key, 0);
        }

        /// @param key       A field holding a list of strings (empty by default).
        /// @param minLength The fewest it may hold.
        /// @return Its value.
        List<String> strings(final String key, final int minLength) {
            if (!data.containsKey(key)) {
                return List.of();
            }
            return orEmpty(list(data.get(key), at(loc, key), ModelReader.this::string, minLength));
        }

        /// @param key       A required field holding a list of strings.
        /// @param minLength The fewest it may hold.
        /// @return Its value.
        List<String> requiredStrings(final String key, final int minLength) {
            return missing(key)
                    ? List.of()
                    : orEmpty(list(data.get(key), at(loc, key), ModelReader.this::string, minLength));
        }

        /// @param key An optional field holding a list of strings.
        /// @return Its value, or null.
        List<String> optionalStrings(final String key) {
            final Object value = data.get(key);
            return value == null
                    ? null
                    : list(value, at(loc, key), ModelReader.this::string, 0);
        }

        /// @param key A field holding a map of strings (empty by default).
        /// @return Its value.
        Map<String, String> stringMap(final String key) {
            if (!data.containsKey(key)) {
                return Map.of();
            }
            final Map<String, String> value = ModelReader.this.stringMap(data.get(key), at(loc, key));
            return value == null
                    ? Map.of()
                    : value;
        }

        /// @param key An optional field holding a map of strings.
        /// @return Its value, or null.
        Map<String, String> optionalStringMap(final String key) {
            final Object value = data.get(key);
            return value == null
                    ? null
                    : ModelReader.this.stringMap(value, at(loc, key));
        }

        /// @param key    An optional field holding a model.
        /// @param reader How the model is read.
        /// @param <T>    The model.
        /// @return Its value, or null.
        <T> T optionalModel(final String key, final ItemReader<T> reader) {
            final Object value = data.get(key);
            return value == null
                    ? null
                    : reader.read(value, at(loc, key));
        }

        /// @param key          A field holding a model.
        /// @param reader       How the model is read.
        /// @param defaultValue Its default.
        /// @param <T>          The model.
        /// @return Its value.
        <T> T model(final String key, final ItemReader<T> reader, final T defaultValue) {
            if (!data.containsKey(key)) {
                return defaultValue;
            }
            final T value = reader.read(data.get(key), at(loc, key));
            return value == null
                    ? defaultValue
                    : value;
        }

        /// @param key    A field holding a list of models (empty by default).
        /// @param reader How each is read.
        /// @param <T>    The model.
        /// @return Its value.
        <T> List<T> models(final String key, final ItemReader<T> reader) {
            if (!data.containsKey(key)) {
                return List.of();
            }
            return orEmpty(list(data.get(key), at(loc, key), reader, 0));
        }

        /// @param key       A required field holding a list of models.
        /// @param reader    How each is read.
        /// @param minLength The fewest it may hold.
        /// @param <T>       The model.
        /// @return Its value.
        <T> List<T> requiredModels(final String key, final ItemReader<T> reader, final int minLength) {
            return missing(key)
                    ? List.of()
                    : orEmpty(list(data.get(key), at(loc, key), reader, minLength));
        }

        /// The model's own check failing, at the model's place.
        void valueError(final String message) {
            ModelReader.this.valueError(loc, message, data);
        }

        private <T> List<T> orEmpty(final List<T> value) {
            return value == null
                    ? List.of()
                    : value;
        }
    }
}
