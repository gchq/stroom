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

import com.google.gwt.core.client.JavaScriptObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

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
            final StringBuilder sb = new StringBuilder(value instanceof Set
                    ? "Set {"
                    : "[");
            final Iterator<?> iterator = toList(value).iterator();
            while (iterator.hasNext()) {
                sb.append(format(iterator.next()));
                if (iterator.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.append(value instanceof Set
                    ? "}"
                    : "]").toString();
        }
        if (value instanceof JavaScriptObject) {
            // Any JavaScript object passes 'instanceof Element' in GWT, so let the browser decide
            // what it is
            return Dom.describeObject((JavaScriptObject) value);
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
        final double value = toDouble(number);
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

    /// @param number A number.
    /// @return The number as a double. A float is converted by its decimal form, so `0.1f` is
    /// `0.1` as it is in the browser (where a float is a JavaScript number) rather than
    /// `0.10000000149011612`.
    static double toDouble(final Number number) {
        if (number instanceof Float) {
            final float value = number.floatValue();
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return value;
            }
            return Double.parseDouble(Float.toString(value));
        }
        return number.doubleValue();
    }

    /// The equivalent of Jest's `toBe`, i.e. `Object.is`, which is also how `toEqual` compares
    /// numbers.
    ///
    /// @param actual   The actual value.
    /// @param expected The expected value, or a [ValueMatcher].
    /// @return True if the values are the same, numbers comparing by value as `Object.is` does:
    /// NaN is the same as NaN, and 0 is not the same as -0.
    static boolean isSame(final Object actual, final Object expected) {
        if (expected instanceof ValueMatcher) {
            return ((ValueMatcher) expected).matchesValue(actual);
        }
        if (actual instanceof Number && expected instanceof Number) {
            return isSameNumber(toDouble((Number) actual), toDouble((Number) expected));
        }
        if (actual instanceof Character && expected instanceof String
            || actual instanceof String && expected instanceof Character) {
            return String.valueOf(actual).equals(String.valueOf(expected));
        }
        return Objects.equals(actual, expected);
    }

    // As JavaScript's Object.is for numbers
    private static boolean isSameNumber(final double a, final double e) {
        if (Double.isNaN(a) || Double.isNaN(e)) {
            return Double.isNaN(a) && Double.isNaN(e);
        }
        if (a == 0 && e == 0) {
            // 0 and -0 are equal by ==, but not the same: their reciprocals are +/- infinity
            return 1 / a == 1 / e;
        }
        return a == e;
    }

    /// The equivalent of Jest's `toEqual`, i.e. recursive equality of maps, collections and
    /// arrays. As in Jest, map entries whose value is null (`undefined`) are ignored, sets are
    /// equal whatever their order, and a set never equals a list or array.
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
            if (!definedKeys(actualMap).equals(definedKeys(expectedMap))) {
                return false;
            }
            for (final Entry<?, ?> entry : expectedMap.entrySet()) {
                if (entry.getValue() != null && !deepEquals(actualMap.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (actual instanceof Set || expected instanceof Set) {
            return actual instanceof Set && expected instanceof Set
                   && setsEqual((Set<?>) actual, (Set<?>) expected);
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

    private static Set<Object> definedKeys(final Map<?, ?> map) {
        final Set<Object> keys = new HashSet<>();
        for (final Entry<?, ?> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private static boolean setsEqual(final Set<?> actual, final Set<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        for (final Object expectedItem : expected) {
            boolean found = false;
            for (final Object actualItem : actual) {
                if (deepEquals(actualItem, expectedItem)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /// The equivalent of Jest's `toMatchObject` (and `expect.objectContaining`).
    ///
    /// @param actual   The actual value.
    /// @param expected The entries the actual value must have.
    /// @return True if the actual value is a map with (at least) the expected entries. A nested
    /// expected map only needs to match part of the actual one too, including maps in lists.
    static boolean matchesObject(final Object actual, final Map<?, ?> expected) {
        if (!(actual instanceof Map)) {
            return false;
        }
        final Map<?, ?> actualMap = (Map<?, ?>) actual;
        for (final Entry<?, ?> entry : expected.entrySet()) {
            if (!actualMap.containsKey(entry.getKey())
                || !matchesPartially(actualMap.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    // As Jest's toMatchObject compares a property: maps partially, lists and arrays item by item
    // (with the same length) and anything else as toEqual does
    private static boolean matchesPartially(final Object actual, final Object expected) {
        if (expected instanceof Map) {
            return matchesObject(actual, (Map<?, ?>) expected);
        }
        if (!(expected instanceof Set) && !(actual instanceof Set)
            && isCollectionLike(expected) && isCollectionLike(actual)) {
            final List<?> actualList = toList(actual);
            final List<?> expectedList = toList(expected);
            if (actualList.size() != expectedList.size()) {
                return false;
            }
            for (int i = 0; i < actualList.size(); i++) {
                if (!matchesPartially(actualList.get(i), expectedList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return deepEquals(actual, expected);
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
    /// @return True if it's a collection or an array (of objects or primitives).
    static boolean isCollectionLike(final Object value) {
        return value instanceof Iterable || value instanceof Object[] || value instanceof int[]
               || value instanceof long[] || value instanceof double[] || value instanceof float[]
               || value instanceof short[] || value instanceof byte[] || value instanceof char[]
               || value instanceof boolean[];
    }

    /// @param value A collection or array.
    /// @return Its items as a list.
    static List<?> toList(final Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        final List<Object> list = new ArrayList<>();
        if (value instanceof Iterable) {
            for (final Object item : (Iterable<?>) value) {
                list.add(item);
            }
        } else {
            addPrimitives(list, value);
        }
        return list;
    }

    // GWT has no java.lang.reflect.Array, so check each type of primitive array
    private static void addPrimitives(final List<Object> list, final Object array) {
        if (array instanceof int[]) {
            for (final int item : (int[]) array) {
                list.add(item);
            }
        } else if (array instanceof long[]) {
            for (final long item : (long[]) array) {
                list.add(item);
            }
        } else if (array instanceof double[]) {
            for (final double item : (double[]) array) {
                list.add(item);
            }
        } else if (array instanceof float[]) {
            for (final float item : (float[]) array) {
                list.add(item);
            }
        } else if (array instanceof short[]) {
            for (final short item : (short[]) array) {
                list.add(item);
            }
        } else if (array instanceof byte[]) {
            for (final byte item : (byte[]) array) {
                list.add(item);
            }
        } else if (array instanceof char[]) {
            for (final char item : (char[]) array) {
                list.add(item);
            }
        } else if (array instanceof boolean[]) {
            for (final boolean item : (boolean[]) array) {
                list.add(item);
            }
        }
    }

    /// @param value A string, collection or array.
    /// @return Its length, or null if it doesn't have one, as Jest's `toHaveLength` reads a
    /// `length` property: a map (like a JavaScript `Map` or object) has none.
    static Integer lengthOf(final Object value) {
        if (value instanceof CharSequence) {
            return ((CharSequence) value).length();
        }
        if (isCollectionLike(value)) {
            return toList(value).size();
        }
        return null;
    }

    /// The equivalent of Jest's `toBeGreaterThan`, `toBeLessThanOrEqual` etc., which compare with
    /// JavaScript's operators, so NaN is never greater, less or equal, and -0 equals 0.
    ///
    /// @param actual     The actual value.
    /// @param expected   The value to compare it with.
    /// @param comparison How to compare them.
    /// @return True if the comparison holds.
    /// @throws PlayException If either value isn't a number.
    static boolean compare(final Object actual, final Number expected, final Comparison comparison) {
        if (!(actual instanceof Number)) {
            throw new PlayException("Received value must be a number, but was " + format(actual));
        }
        if (expected == null) {
            throw new PlayException("Expected value must be a number, but was null");
        }
        final double a = toDouble((Number) actual);
        final double e = toDouble(expected);
        switch (comparison) {
            case GREATER_THAN:
                return a > e;
            case GREATER_THAN_OR_EQUAL:
                return a >= e;
            case LESS_THAN:
                return a < e;
            case LESS_THAN_OR_EQUAL:
            default:
                return a <= e;
        }
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
        final double difference = Math.abs(toDouble((Number) actual) - toDouble(expected));
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
            final double number = toDouble((Number) value);
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


    // --------------------------------------------------------------------------------


    /// How [#compare(Object, Number, Comparison)] compares numbers.
    enum Comparison {
        /// `toBeGreaterThan`.
        GREATER_THAN,
        /// `toBeGreaterThanOrEqual`.
        GREATER_THAN_OR_EQUAL,
        /// `toBeLessThan`.
        LESS_THAN,
        /// `toBeLessThanOrEqual`.
        LESS_THAN_OR_EQUAL
    }
}
