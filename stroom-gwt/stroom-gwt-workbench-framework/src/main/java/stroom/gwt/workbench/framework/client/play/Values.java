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

package stroom.gwt.workbench.framework.client.play;

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/// The value comparisons and formatting behind [ValueExpectation] and [SpyExpectation], following
/// Jest's rules where Java allows, e.g. all numbers compare by value whatever their type (as
/// JavaScript has a single number type) and null stands for both `null` and `undefined`.
final class Values {

    // Larger whole numbers are shown in exponent form, as JavaScript does
    private static final double MAX_PLAIN_NUMBER = 1e21;

    private Values() {
        // Static utility
    }

    /// @param value Any value.
    /// @return The value as JavaScript would show it, e.g. `"text"`, `42`, `[1, 2]` or
    /// `{ id: 1 }`.
    static String format(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return Expectation.quote((String) value);
        }
        if (value instanceof Character) {
            return Expectation.quote(String.valueOf(value));
        }
        if (value instanceof Number) {
            return formatNumber((Number) value);
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof ValueMatcher) {
            return ((ValueMatcher) value).describe();
        }
        if (value instanceof Map) {
            return formatMap((Map<?, ?>) value);
        }
        if (isCollectionLike(value)) {
            final StringBuilder sb = new StringBuilder("[");
            final Iterator<?> iterator = toList(value).iterator();
            while (iterator.hasNext()) {
                sb.append(format(iterator.next()));
                if (iterator.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.append(']').toString();
        }
        if (value instanceof Element) {
            return Dom.describe((Element) value);
        }
        return String.valueOf(value);
    }

    private static String formatMap(final Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        final StringBuilder sb = new StringBuilder("{ ");
        final Iterator<? extends Entry<?, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            final Entry<?, ?> entry = iterator.next();
            sb.append(entry.getKey()).append(": ").append(format(entry.getValue()));
            if (iterator.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append(" }").toString();
    }

    /// @param number A number.
    /// @return The number as JavaScript shows it, e.g. `1` rather than `1.0`.
    static String formatNumber(final Number number) {
        final double value = number.doubleValue();
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0
                    ? "Infinity"
                    : "-Infinity";
        }
        if (value == Math.rint(value) && Math.abs(value) < MAX_PLAIN_NUMBER) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /// The equivalent of Jest's `toBe`, i.e. `Object.is`.
    ///
    /// @param actual   The actual value.
    /// @param expected The expected value, or a [ValueMatcher].
    /// @return True if the values are the same, numbers comparing by value.
    static boolean isSame(final Object actual, final Object expected) {
        if (expected instanceof ValueMatcher) {
            return ((ValueMatcher) expected).matchesValue(actual);
        }
        if (actual instanceof Number && expected instanceof Number) {
            final double a = ((Number) actual).doubleValue();
            final double e = ((Number) expected).doubleValue();
            return a == e || (Double.isNaN(a) && Double.isNaN(e));
        }
        if (actual instanceof Character && expected instanceof String
            || actual instanceof String && expected instanceof Character) {
            return String.valueOf(actual).equals(String.valueOf(expected));
        }
        return Objects.equals(actual, expected);
    }

    /// The equivalent of Jest's `toEqual`, i.e. recursive equality of maps, collections and
    /// arrays.
    ///
    /// @param actual   The actual value.
    /// @param expected The expected value, which may be or contain [ValueMatcher]s.
    /// @return True if the values are equal.
    static boolean deepEquals(final Object actual, final Object expected) {
        if (expected instanceof ValueMatcher) {
            return ((ValueMatcher) expected).matchesValue(actual);
        }
        if (actual instanceof Map && expected instanceof Map) {
            final Map<?, ?> actualMap = (Map<?, ?>) actual;
            final Map<?, ?> expectedMap = (Map<?, ?>) expected;
            if (!actualMap.keySet().equals(expectedMap.keySet())) {
                return false;
            }
            for (final Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!deepEquals(actualMap.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (isCollectionLike(actual) && isCollectionLike(expected)) {
            final List<?> actualList = toList(actual);
            final List<?> expectedList = toList(expected);
            if (actualList.size() != expectedList.size()) {
                return false;
            }
            for (int i = 0; i < actualList.size(); i++) {
                if (!deepEquals(actualList.get(i), expectedList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return isSame(actual, expected);
    }

    /// The equivalent of Jest's `toMatchObject` (and `expect.objectContaining`).
    ///
    /// @param actual   The actual value.
    /// @param expected The entries the actual value must have.
    /// @return True if the actual value is a map with (at least) the expected entries. A nested
    /// expected map only needs to match part of the actual one too.
    static boolean matchesObject(final Object actual, final Map<?, ?> expected) {
        if (!(actual instanceof Map)) {
            return false;
        }
        final Map<?, ?> actualMap = (Map<?, ?>) actual;
        for (final Entry<?, ?> entry : expected.entrySet()) {
            if (!actualMap.containsKey(entry.getKey())) {
                return false;
            }
            final Object actualValue = actualMap.get(entry.getKey());
            final boolean matches = entry.getValue() instanceof Map
                    ? matchesObject(actualValue, (Map<?, ?>) entry.getValue())
                    : deepEquals(actualValue, entry.getValue());
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    /// The equivalent of Jest's `toContain` (and `toContainEqual` if deep).
    ///
    /// @param container A string, collection or array.
    /// @param item      The item, or for a string, the text.
    /// @param deep      True to compare items with [#deepEquals(Object, Object)] rather than
    ///                  [#isSame(Object, Object)].
    /// @return True if the container contains the item, false if it doesn't or isn't a container.
    static boolean containsItem(final Object container, final Object item, final boolean deep) {
        if (container instanceof String) {
            return item != null && ((String) container).contains(String.valueOf(item));
        }
        if (!isCollectionLike(container)) {
            return false;
        }
        for (final Object element : toList(container)) {
            if (deep
                    ? deepEquals(element, item)
                    : isSame(element, item)) {
                return true;
            }
        }
        return false;
    }

    /// @param value Any value.
    /// @return True if it's a collection or an array of objects.
    static boolean isCollectionLike(final Object value) {
        return value instanceof Iterable || value instanceof Object[];
    }

    /// @param value A collection or array of objects.
    /// @return Its items as a list.
    static List<?> toList(final Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        final List<Object> list = new ArrayList<>();
        for (final Object item : (Iterable<?>) value) {
            list.add(item);
        }
        return list;
    }

    /// @param value A string, collection, map or array.
    /// @return Its length, or null if it doesn't have one.
    static Integer lengthOf(final Object value) {
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        if (isCollectionLike(value)) {
            return toList(value).size();
        }
        return null;
    }

    /// @param actual   The actual value.
    /// @param expected The value to compare it with.
    /// @return Negative, zero or positive as the actual value is less than, equal to or greater
    /// than the expected one.
    /// @throws PlayException If either value isn't a number.
    static int compare(final Object actual, final Number expected) {
        if (!(actual instanceof Number)) {
            throw new PlayException("Received value must be a number, but was " + format(actual));
        }
        if (expected == null) {
            throw new PlayException("Expected value must be a number, but was null");
        }
        return Double.compare(((Number) actual).doubleValue(), expected.doubleValue());
    }

    /// The equivalent of Jest's `toBeCloseTo`.
    ///
    /// @param actual    The actual value.
    /// @param expected  The expected number.
    /// @param numDigits The number of decimal places to check.
    /// @return True if the actual value is a number within `10^-numDigits / 2` of the expected one.
    static boolean isCloseTo(final Object actual, final Number expected, final int numDigits) {
        if (!(actual instanceof Number)) {
            return false;
        }
        final double difference = Math.abs(((Number) actual).doubleValue() - expected.doubleValue());
        return difference < Math.pow(10, -numDigits) / 2;
    }

    /// @param value Any value.
    /// @return True if the value is truthy in JavaScript, i.e. not null, false, zero, NaN or an
    /// empty string.
    static boolean isTruthy(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            final double number = ((Number) value).doubleValue();
            return number != 0 && !Double.isNaN(number);
        }
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length() > 0;
        }
        return true;
    }

    /// @param actual   The arguments of a call.
    /// @param expected The expected arguments, which may be [ValueMatcher]s.
    /// @return True if there are the same number of arguments and each is
    /// [#deepEquals(Object, Object)] the expected one.
    static boolean argumentsMatch(final List<?> actual, final List<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < actual.size(); i++) {
            if (!deepEquals(actual.get(i), expected.get(i))) {
                return false;
            }
        }
        return true;
    }

    /// @param values Values, e.g. the arguments of a call.
    /// @return The values formatted and separated by commas, e.g. `1, "a"`.
    static String formatAll(final List<?> values) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(format(values.get(i)));
        }
        return sb.toString();
    }
}
