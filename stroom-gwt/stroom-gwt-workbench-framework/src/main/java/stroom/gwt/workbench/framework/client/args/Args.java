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

package stroom.gwt.workbench.framework.client.args;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The values of a story's args, i.e. the inputs that the Controls addon can change.
///
/// Values are `String`, `Boolean`, `Double` or `List<String>`. A name with no value is
/// 'undefined', as in React Storybook, which the Controls addon shows as e.g. 'Set boolean'.
public final class Args {

    private static final Args EMPTY = new Args(Collections.emptyMap());

    private final Map<String, Object> values;

    private Args(final Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /// @return Args with no values.
    public static Args empty() {
        return EMPTY;
    }

    /// Creates args from name/value pairs, e.g. `Args.of("text", "Click me", "loading", true)`.
    ///
    /// @param namesAndValues Alternating names and values. Integers are stored as doubles.
    /// @return The args.
    public static Args of(final Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Names and values must be in pairs");
        }
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            map.put((String) namesAndValues[i], normalise(namesAndValues[i + 1]));
        }
        return new Args(map);
    }

    /// Creates args from a map without normalising the values, so the values must already be of
    /// the supported types.
    ///
    /// @param map The names and values, which may be null.
    /// @return The args.
    static Args fromMap(final Map<String, Object> map) {
        return new Args(map);
    }

    private static Object normalise(final Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Double) {
            return value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof List) {
            final List<String> list = new ArrayList<>();
            for (final Object item : (List<?>) value) {
                list.add(String.valueOf(item));
            }
            return Collections.unmodifiableList(list);
        }
        throw new IllegalArgumentException("Unsupported arg value type " + value.getClass().getName());
    }

    /// @param other Args whose values replace or add to these.
    /// @return New args with the values of both, the other's taking precedence.
    public Args merge(final Args other) {
        final Map<String, Object> map = new LinkedHashMap<>(values);
        map.putAll(other.values);
        return new Args(map);
    }

    /// @param base The args to compare with, e.g. a story's initial args.
    /// @return The args whose values differ from the base's, e.g. those the user has changed.
    public Args diff(final Args base) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : values.entrySet()) {
            if (!Objects.equals(entry.getValue(), base.values.get(entry.getKey()))) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        return new Args(map);
    }

    /// @param name  The name of an arg.
    /// @param value The new value, or null to make it undefined.
    /// @return New args with the value changed.
    public Args with(final String name, final Object value) {
        final Map<String, Object> map = new LinkedHashMap<>(values);
        if (value == null) {
            map.remove(name);
        } else {
            map.put(name, normalise(value));
        }
        return new Args(map);
    }

    /// Sets an arg to null, the equivalent of `null` (or `undefined`) in a React Storybook URL.
    /// Unlike [#with(String, Object)] with null, the arg is kept, so that [#merge(Args)] unsets
    /// the arg in the args being merged into, but it is otherwise treated as having no value.
    ///
    /// @param name The name of an arg.
    /// @return New args with the arg set to null.
    public Args withNull(final String name) {
        final Map<String, Object> map = new LinkedHashMap<>(values);
        map.put(name, null);
        return new Args(map);
    }

    /// @param name The name of an arg.
    /// @return True if the arg has a value.
    public boolean has(final String name) {
        return values.get(name) != null;
    }

    /// @param name The name of an arg.
    /// @return The raw value, or null if undefined.
    public Object get(final String name) {
        return values.get(name);
    }

    /// @param name The name of an arg.
    /// @return The value as a string, or null if undefined.
    public String getString(final String name) {
        final Object value = values.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return formatNumber((Double) value);
        }
        return String.valueOf(value);
    }

    /// @param name         The name of an arg.
    /// @param defaultValue The value to return if the arg is undefined.
    /// @return The value as a string.
    public String getString(final String name, final String defaultValue) {
        final String value = getString(name);
        return value != null
                ? value
                : defaultValue;
    }

    /// @param name The name of an arg.
    /// @return The value as a boolean, false if undefined.
    public boolean getBoolean(final String name) {
        final Object value = values.get(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equals(String.valueOf(value));
    }

    /// @param name         The name of an arg.
    /// @param defaultValue The value to return if the arg is undefined or not a number.
    /// @return The value as a number.
    public double getNumber(final String name, final double defaultValue) {
        final Object value = values.get(name);
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (final NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /// @param name The name of an arg.
    /// @return The value as a list, empty if undefined.
    @SuppressWarnings("unchecked")
    public List<String> getList(final String name) {
        final Object value = values.get(name);
        if (value instanceof List) {
            return (List<String>) value;
        }
        if (value != null) {
            return Collections.singletonList(String.valueOf(value));
        }
        return Collections.emptyList();
    }

    /// @return The names of the args with values, in the order they were added.
    public List<String> getNames() {
        return new ArrayList<>(values.keySet());
    }

    /// @return The number of args with values.
    public int size() {
        return values.size();
    }

    /// @return The names and values, including any null values, in the order they were added.
    Map<String, Object> asMap() {
        return values;
    }

    /// Formats a number as plain decimal digits, as React Storybook's URLs need, e.g. `5` not
    /// `5.0` and `100000000000000000000` not `1.0E20`. The result is the same on the JVM and in
    /// the browser.
    ///
    /// @param value A number.
    /// @return The number in plain decimal digits, or an empty string if it is NaN or infinite.
    public static String formatNumber(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        if (value == 0) {
            // Includes -0
            return "0";
        }
        return toPlainDecimal(String.valueOf(value));
    }

    /// Converts the string form of a finite, non-zero double, which may be in scientific notation
    /// (`1.0E20` on the JVM, `1e+21` in the browser), into plain decimal digits.
    private static String toPlainDecimal(final String text) {
        final boolean negative = text.charAt(0) == '-';
        final String unsigned = negative
                ? text.substring(1)
                : text;
        int expIndex = unsigned.indexOf('e');
        if (expIndex < 0) {
            expIndex = unsigned.indexOf('E');
        }
        final String mantissa = expIndex >= 0
                ? unsigned.substring(0, expIndex)
                : unsigned;
        final int exponent = expIndex >= 0
                ? parseExponent(unsigned.substring(expIndex + 1))
                : 0;
        final int point = mantissa.indexOf('.');
        String digits = point >= 0
                ? mantissa.substring(0, point) + mantissa.substring(point + 1)
                : mantissa;
        // The number of digits before the decimal point
        int pointPosition = (point >= 0
                ? point
                : mantissa.length()) + exponent;

        int leadingZeros = 0;
        while (leadingZeros < digits.length() - 1 && digits.charAt(leadingZeros) == '0') {
            leadingZeros++;
        }
        digits = digits.substring(leadingZeros);
        pointPosition -= leadingZeros;
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') {
            end--;
        }
        digits = digits.substring(0, end);

        final StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        if (pointPosition <= 0) {
            sb.append("0.");
            appendZeros(sb, -pointPosition);
            sb.append(digits);
        } else if (pointPosition >= digits.length()) {
            sb.append(digits);
            appendZeros(sb, pointPosition - digits.length());
        } else {
            sb.append(digits.substring(0, pointPosition)).append('.').append(digits.substring(pointPosition));
        }
        return sb.toString();
    }

    private static int parseExponent(final String exponent) {
        // Integer.parseInt may not accept a leading '+' in the browser
        return exponent.startsWith("+")
                ? Integer.parseInt(exponent.substring(1))
                : Integer.parseInt(exponent);
    }

    private static void appendZeros(final StringBuilder sb, final int count) {
        for (int i = 0; i < count; i++) {
            sb.append('0');
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return values.equals(((Args) o).values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
