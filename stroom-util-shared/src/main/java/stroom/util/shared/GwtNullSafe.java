/*
 * Copyright 2024 Crown Copyright
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

package stroom.util.shared;

import stroom.util.shared.time.SimpleDuration;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>
 * This is pretty much a copy of NullSafe but with things GWT doesn't emulate removed
 * or implemented in a different way.
 * </p>
 * Utility methods for safely getting properties (or properties of properties of ...) from
 * a value with protection from null values anywhere in the chain.
 */
public class GwtNullSafe {

    private GwtNullSafe() {
    }

    /**
     * Allows you to safely compare a child property of val1 to other.
     *
     * @return False if val1 is null else whether the child property of val1 is equal to other
     */
    public static <T1, T2> boolean equals(final T1 val1,
                                          final Function<T1, T2> getter,
                                          final Object other) {
        if (val1 == null) {
            return false;
        } else {
            final T2 val2 = getter.apply(val1);
            return Objects.equals(val2, other);
        }
    }

    /**
     * Allows you to safely compare a grandchild property of val1 to other.
     *
     * @return False if val1 is null or if val1's child property is null,
     * else whether the grandchild property of val1 is equal to other
     */
    public static <T1, T2, T3> boolean equals(final T1 val1,
                                              final Function<T1, T2> getter1,
                                              final Function<T2, T3> getter2,
                                              final Object other) {
        if (val1 == null) {
            return false;
        } else {
            final T2 val2 = getter1.apply(val1);
            if (val2 == null) {
                return false;
            } else {
                final T3 val3 = getter2.apply(val2);
                return Objects.equals(val3, other);
            }
        }
    }

    /**
     * Test if the properties (accessed using the same getter for both) of two
     * objects of the same class are equal in a null safe way.
     *
     * @return True if val1 and val2 are both null or if the results of applying {@code getter}
     * to va1 and val2 are equal.
     */
    public static <T, R> boolean equalProperties(final T val1,
                                                 final T val2,
                                                 final Function<T, R> getter) {
        if (val1 == null && val2 == null) {
            return true;
        } else if (val1 != null && val2 == null) {
            return false;
        } else if (val1 == null) {
            return false;
        } else {
            Objects.requireNonNull(getter);
            final R result1 = getter.apply(val1);
            final R result2 = getter.apply(val2);
            return Objects.equals(result1, result2);
        }
    }

    /**
     * @return True if all values in the array are null or the array itself is null
     */
    public static <T> boolean allNull(final T... vals) {
        if (vals == null) {
            return true;
        } else {
            boolean allNull = true;
            for (final T val : vals) {
                if (val != null) {
                    allNull = false;
                    break;
                }
            }
            return allNull;
        }
    }

    /**
     * @return True if the array itself is non-null and all values in the array are non-null
     */
    public static <T> boolean allNonNull(final T... vals) {
        if (vals == null) {
            return false;
        } else {
            boolean allNonNull = true;
            for (final T val : vals) {
                if (val == null) {
                    allNonNull = false;
                    break;
                }
            }
            return allNonNull;
        }
    }

    /**
     * Return first non-null value or an empty {@link Optional} if all are null
     */
    public static <T> Optional<T> coalesce(final T val1, final T val2) {
        return val1 != null
                ? Optional.of(val1)
                : (val2 != null
                        ? Optional.of(val2)
                        : Optional.empty());
    }

    /**
     * Return first non-null value or an empty {@link Optional} if all are null
     */
    public static <T> Optional<T> coalesce(final T val1, final T val2, final T val3) {
        return val1 != null
                ? Optional.of(val1)
                : (val2 != null
                        ? Optional.of(val2)
                        : (val3 != null
                                ? Optional.of(val3)
                                : Optional.empty()));
    }

    /**
     * Return first non-null value or an empty {@link Optional} if all are null
     */
    public static <T> Optional<T> coalesce(final T val1,
                                           final T val2,
                                           final T val3,
                                           final T val4) {
        return val1 != null
                ? Optional.of(val1)
                : (val2 != null
                        ? Optional.of(val2)
                        : (val3 != null
                                ? Optional.of(val3)
                                : (val4 != null
                                        ? Optional.of(val4)
                                        : Optional.empty())));
    }


    /**
     * @return True if str is null or blank
     */
    public static boolean isBlankString(final String str) {
        // GWT doesn't emulate String::isBlank
        if (str != null && !str.isEmpty()) {
            for (final char chr : str.toCharArray()) {
                if (!Character.isWhitespace(chr)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @return True if str is non-null and has at least one character that is not
     * whitespace
     */
    public static boolean isNonBlankString(final String str) {
        // GWT doesn't emulate String::isBlank
        if (str != null && !str.isEmpty()) {
            for (final char chr : str.toCharArray()) {
                if (!Character.isWhitespace(chr)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return str if it is not null/empty/blank, else other.
     */
    public static String nonBlankStringElse(final String str, final String other) {
        return isBlankString(str)
                ? Objects.requireNonNull(other)
                : str;
    }

    /**
     * @return str if it is not null/empty/blank, else result of calling otherSupplier.
     */
    public static String nonBlankStringElseGet(final String str, final Supplier<String> otherSupplier) {
        return isBlankString(str)
                ? Objects.requireNonNull(Objects.requireNonNull(otherSupplier).get())
                : str;
    }

    /**
     * If str is not null/empty/blank then pass it to consumer (if that is not null).
     */
    public static void consumeNonBlankString(final String str, final Consumer<String> consumer) {
        // GWT doesn't emulate String::isBlank
        if (!isBlankString(str) && consumer != null) {
            consumer.accept(str);
        }
    }

    /**
     * If str is not null/empty/blank then pass it to consumer (if that is not null).
     */
    public static void consumeNonBlankString(final String str,
                                             final boolean trimString,
                                             final Consumer<String> consumer) {
        // GWT doesn't emulate String::isBlank
        if (!isBlankString(str) && consumer != null) {
            if (trimString) {
                consumer.accept(str.trim());
            } else {
                consumer.accept(str);
            }
        }
    }

    /**
     * @return True if str is null or empty
     */
    public static boolean isEmptyString(final String str) {
        return str == null || str.isEmpty();
    }

    /**
     * @return True if str is non-null and not empty
     */
    public static boolean isNonEmptyString(final String str) {
        return str != null && !str.isEmpty();
    }

    /**
     * @return str trimmed or an empty string if str is null
     */
    public static String trim(final String str) {
        return str != null
                ? str.trim()
                : "";
    }

    public static Optional<String> nonBlank(final String str) {
        if (isBlankString(str)) {
            return Optional.empty();
        } else {
            return Optional.of(str);
        }
    }

    public static String join(final CharSequence delimiter, final CharSequence... elements) {
        if (elements == null || elements.length == 0) {
            return "";
        } else {
            return String.join(delimiter, elements);
        }
    }

    /**
     * @return True if val is not null and true
     */
    public static boolean isTrue(final Boolean val) {
        return val != null && val;
    }

    /**
     * @return True if val is not null and the result of applying getter to value
     * is non-null and true
     */
    public static <T> boolean isTrue(final T val, final Function<T, Boolean> getter) {
        if (val == null) {
            return false;
        } else {
            Objects.requireNonNull(getter);
            final Boolean bool = getter.apply(val);
            return bool != null && bool;
        }
    }

    /**
     * @return True if both str and subStr are non-null and str contains subStr
     */
    public static boolean contains(final String str, final String subStr) {
        return str != null
                && subStr != null
                && str.contains(subStr);
    }

    /**
     * @return True if the collection is null or empty
     */
    public static <T> boolean isEmptyCollection(final Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * @return True if the map is null or empty
     */
    public static <T1, T2> boolean isEmptyMap(final Map<T1, T2> map) {
        return map == null || map.isEmpty();
    }

    /**
     * @return True if value is null or the string property is null or empty
     */
    public static <T> boolean isEmptyString(final T value,
                                            final Function<T, String> stringGetter) {
        if (value == null) {
            return true;
        } else {
            final String str = Objects.requireNonNull(stringGetter).apply(value);
            return str == null || str.isEmpty();
        }
    }

    /**
     * @return True if value is null or the string property is null/empty/blank
     */
    public static <T> boolean isBlankString(final T value,
                                            final Function<T, String> stringGetter) {
        if (value == null) {
            return true;
        } else {
            final String str = Objects.requireNonNull(stringGetter).apply(value);
            return str == null || isBlankString(str);
        }
    }

    /**
     * If str is not null/empty/blank then pass it to consumer (if that is not null).
     */
    public static <T> void consumeNonBlankString(final T value,
                                                 final Function<T, String> stringGetter,
                                                 final Consumer<String> consumer) {
        // GWT doesn't emulate String::isBlank
        if (value != null && stringGetter != null && consumer != null) {
            final String str = stringGetter.apply(value);

            if (!isBlankString(str)) {
                Objects.requireNonNull(consumer).accept(str);
            }
        }
    }

    /**
     * @return True if value is null or the collection is null or empty
     */
    public static <T1, T2 extends Collection<E>, E> boolean isEmptyCollection(final T1 value,
                                                                              final Function<T1, T2> collectionGetter) {
        if (value == null) {
            return true;
        } else {
            final T2 collection = Objects.requireNonNull(collectionGetter)
                    .apply(value);
            return collection == null || collection.isEmpty();
        }
    }

    /**
     * @return True if value is null or the map is null or empty
     */
    public static <T1, T2 extends Map<K, V>, K, V> boolean isEmptyMap(final T1 value,
                                                                      final Function<T1, T2> mapGetter) {
        if (value == null) {
            return true;
        } else {
            final T2 map = Objects.requireNonNull(mapGetter)
                    .apply(value);
            return map == null || map.isEmpty();
        }
    }

    /**
     * @return True if the collection is non-null and not empty
     */
    public static <T> boolean hasItems(final Collection<T> collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * @return True if the collection is non-null and not empty
     */
    public static <T> boolean hasItems(final T[] items) {
        return items != null && items.length > 0;
    }

    /**
     * @return True if the map is non-null and not empty
     */
    public static <T1, T2> boolean hasEntries(final Map<T1, T2> map) {
        return map != null && !map.isEmpty();
    }

    /**
     * @return The size of the collection or zero if null.
     */
    public static <T> int size(final Collection<T> collection) {
        return collection != null
                ? collection.size()
                : 0;
    }

    /**
     * @return The size of the collection or zero if null.
     */
    public static <T> int size(final T[] items) {
        return items != null
                ? items.length
                : 0;
    }

    /**
     * @return The size of the collection or zero if null.
     */
    public static <K, V> int size(final Map<K, V> map) {
        return map != null
                ? map.size()
                : 0;
    }

    /**
     * @return True if value is non-null and the collection is non-null and not empty
     */
    public static <T1, T2 extends Collection<E>, E> boolean hasItems(
            final T1 value,
            final Function<T1, T2> collectionGetter) {

        if (value == null) {
            return false;
        } else {
            final T2 collection = Objects.requireNonNull(collectionGetter)
                    .apply(value);
            return collection != null && !collection.isEmpty();
        }
    }

    /**
     * @return True if value is non-null and the map is non-null and not empty
     */
    public static <T1, T2 extends Map<K, V>, K, V> boolean hasEntries(
            final T1 value,
            final Function<T1, T2> mapGetter) {

        if (value == null) {
            return false;
        } else {
            final T2 map = Objects.requireNonNull(mapGetter)
                    .apply(value);
            return map != null && !map.isEmpty();
        }
    }

    /**
     * Returns a {@link Stream<E>} if collection is non-null else returns an empty {@link Stream<E>}
     */
    public static <E> Stream<E> stream(final Collection<E> collection) {
        if (collection == null || collection.isEmpty()) {
            return Stream.empty();
        } else {
            return collection.stream();
        }
    }

    /**
     * Returns a {@link Stream<T>} if items is non-null else returns an empty {@link Stream<T>}
     */
    public static <T> Stream<T> stream(final T... items) {
        if (items == null || items.length == 0) {
            return Stream.empty();
        } else {
            return Arrays.stream(items);
        }
    }

    /**
     * Returns the passed array of items or varargs items as a non-null list.
     * Does not support null items in the list.
     *
     * @return A non-null list of items. List should be assumed to be immutable.
     */
    public static <T> List<T> asList(final T... items) {
        return items == null || items.length == 0
                ? Collections.emptyList()
                : Arrays.asList(items);
    }

    /**
     * Returns the passed array of items or varargs items as a non-null set.
     * Does not support null items in the array.
     *
     * @return A non-null unmodifiable set of items.
     */
    public static <T> Set<T> asSet(final T... items) {
        //noinspection Java9CollectionFactory
        return items == null || items.length == 0
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(Arrays.asList(items)));
    }

    /**
     * Returns the passed list if it is non-null else returns an empty list.
     */
    public static <L extends List<T>, T> List<T> list(final L list) {
        return list != null
                ? list
                : Collections.emptyList();
    }

    /**
     * Returns the passed list if it is non-null else returns an empty list.
     */
    public static <L extends Collection<T>, T> Collection<T> collection(final L collection) {
        return collection != null
                ? collection
                : Collections.emptyList();
    }

    /**
     * Returns the passed set if it is non-null else returns an empty set.
     */
    public static <S extends Set<T>, T> Set<T> set(final S set) {
        return set != null
                ? set
                : Collections.emptySet();
    }

    /**
     * Returns the passed map if it is non-null else returns an empty map.
     */
    public static <M extends Map<K, V>, K, V> Map<K, V> map(final M map) {
        return map != null
                ? map
                : Collections.emptyMap();
    }

    /**
     * Returns the passed string if it is non-null else returns an empty string.
     */
    public static String string(final String str) {
        return str != null
                ? str
                : "";
    }

    /**
     * Returns the passed duration if it is non-null else returns a ZERO {@link SimpleDuration}
     */
    public static SimpleDuration duration(final SimpleDuration duration) {
        return duration != null
                ? duration
                : SimpleDuration.ZERO;
    }

    /**
     * Apply getter to value if value is non-null.
     *
     * @return The result of applying getter to value if value is non-null, else null.
     */
    public static <T1, R> R get(final T1 value,
                                final Function<T1, R> getter) {
        if (value == null) {
            return null;
        } else {
            return Objects.requireNonNull(getter).apply(value);
        }
    }

    /**
     * Apply getter to value if value is non-null. If value or the result of
     * applying getter to value is null, return other.
     */
    public static <T1, R> R getOrElse(final T1 value,
                                      final Function<T1, R> getter,
                                      final R other) {
        return requireNonNullElse(get(value, getter), other);
    }

    public static <T1> String toStringOrElse(final T1 value,
                                             final String other) {
        if (value == null) {
            return other;
        } else {
            return convertToString(value, other);
        }
    }

    /**
     * Apply getter to value if value is non-null. If value or the result of
     * applying getter to value is null, return the value supplied by otherSupplier.
     */
    public static <T1, R> R getOrElseGet(final T1 value,
                                         final Function<T1, R> getter,
                                         final Supplier<R> otherSupplier) {
        return requireNonNullElseGet(get(value, getter), otherSupplier);
    }

    /**
     * Apply getter to value if value is non-null and return wrapper in an {@link Optional}.
     * If this result or value are null return an empty {@link Optional}.
     */
    public static <T1, R> Optional<R> getAsOptional(final T1 value,
                                                    final Function<T1, R> getter) {
        if (value == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(Objects.requireNonNull(getter).apply(value));
        }
    }

    /**
     * If runnable is non-null run it.
     */
    public static void run(final Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    /**
     * If value is non-null pass it to the consumer, else it is a no-op.
     */
    public static <T> void consume(final T value,
                                   final Consumer<T> consumer) {
        if (value != null && consumer != null) {
            consumer.accept(value);
        }
    }

    /**
     * Allows you to test a value without worrying if the value is null, e.g.
     * <pre><code>
     *    boolean hasValues = NullSafe.test(myList, list -> !list.isEmpty());
     * </code></pre>
     *
     * @return false if value is null
     * else return the value of the predicate when applied
     * to the non-null value.
     */
    public static <T> boolean test(final T value,
                                   final Predicate<T> predicate) {
        if (value == null) {
            return false;
        } else {
            return Objects.requireNonNull(predicate)
                    .test(value);
        }
    }

    /**
     * Allows you to test some property of a value without worrying if the value is null, e.g.
     * <pre><code>
     *    boolean hasValues = NullSafe.test(myObject, MyObject::getItems, list -> !list.isEmpty());
     * </code></pre>
     *
     * @return false if value is null or the getter returns null,
     * else return the value of the predicate when applied
     * to the result of the getter.
     */
    public static <T1, R> boolean test(final T1 value,
                                       final Function<T1, R> getter,
                                       final Predicate<R> predicate) {
        if (value == null) {
            return false;
        } else {
            final R result = Objects.requireNonNull(getter)
                    .apply(value);
            return result != null
                    && Objects.requireNonNull(predicate)
                    .test(result);
        }
    }

    public static <T1> String toString(final T1 value,
                                       final Function<T1, Object> getter) {
        return toStringOrElse(value, getter, null);
    }

    public static <T1> String toStringOrElse(final T1 value,
                                             final Function<T1, Object> getter,
                                             final String other) {
        if (value == null) {
            return other;
        } else {
            final Object value2 = Objects.requireNonNull(getter).apply(value);
            return convertToString(value2, other);
        }
    }

    public static <T1> String toStringOrElseGet(final T1 value,
                                                final Function<T1, Object> getter,
                                                final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final Object value2 = Objects.requireNonNull(getter).apply(value);
            return convertToString(value2, otherSupplier);
        }
    }

    public static <T1, T2, R> R get(final T1 value,
                                    final Function<T1, T2> getter1,
                                    final Function<T2, R> getter2) {
        if (value == null) {
            return null;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return null;
            } else {
                return Objects.requireNonNull(getter2).apply(value2);
            }
        }
    }

    public static <T1, T2, R> R getOrElse(final T1 value,
                                          final Function<T1, T2> getter1,
                                          final Function<T2, R> getter2,
                                          final R other) {
        return requireNonNullElse(get(value, getter1, getter2), other);
    }

    public static <T1, T2, R> R getOrElseGet(final T1 value,
                                             final Function<T1, T2> getter1,
                                             final Function<T2, R> getter2,
                                             final Supplier<R> otherSupplier) {
        return requireNonNullElseGet(get(value, getter1, getter2), otherSupplier);
    }

    public static <T1, T2, R> Optional<R> getAsOptional(final T1 value,
                                                        final Function<T1, T2> getter1,
                                                        final Function<T2, R> getter2) {
        if (value == null) {
            return Optional.empty();
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(Objects.requireNonNull(getter2).apply(value2));
            }
        }
    }

    /**
     * If value is non-null apply getter1 to it.
     * If the result of that is non-null consume the result.
     */
    public static <T1, T2> void consume(final T1 value,
                                        final Function<T1, T2> getter1,
                                        final Consumer<T2> consumer) {
        if (value != null && consumer != null) {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 != null) {
                consumer.accept(value2);
            }
        }
    }

    /**
     * Allows you to test some property of a value without worrying if the value is null, e.g.
     * <pre><code>
     *    List<Sting> list = null;
     *    boolean hasValues = NullSafe.test(list, list -> list.size > 0);
     * </code></pre>
     *
     * @return false if value is null, else return the value of the predicate when applied
     * to the result of the getter.
     */
    public static <T1, T2, R> boolean test(final T1 value,
                                           final Function<T1, T2> getter1,
                                           final Function<T2, R> getter2,
                                           final Predicate<R> predicate) {
        if (value == null) {
            return false;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1)
                    .apply(value);
            if (value2 == null) {
                return false;
            } else {
                final R result = Objects.requireNonNull(getter2).apply(value2);
                return result != null
                        && Objects.requireNonNull(predicate)
                        .test(result);
            }
        }
    }

    public static <T1, T2> String toString(final T1 value,
                                           final Function<T1, T2> getter1,
                                           final Function<T2, Object> getter2) {
        return toStringOrElse(value, getter1, getter2, null);
    }

    public static <T1, T2> String toStringOrElse(final T1 value,
                                                 final Function<T1, T2> getter1,
                                                 final Function<T2, Object> getter2,
                                                 final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return handleNull(otherSupplier);
            } else {
                final Object value3 = Objects.requireNonNull(getter2).apply(value2);
                return convertToString(value3, otherSupplier);
            }
        }
    }

    public static <T1, T2, T3, R> R get(final T1 value,
                                        final Function<T1, T2> getter1,
                                        final Function<T2, T3> getter2,
                                        final Function<T3, R> getter3) {
        if (value == null) {
            return null;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return null;
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return null;
                } else {
                    return Objects.requireNonNull(getter3).apply(value3);
                }
            }
        }
    }

    public static <T1, T2, T3, R> R getOrElse(final T1 value,
                                              final Function<T1, T2> getter1,
                                              final Function<T2, T3> getter2,
                                              final Function<T3, R> getter3,
                                              final R other) {
        return requireNonNullElse(get(value, getter1, getter2, getter3), other);
    }

    public static <T1, T2, T3, R> R getOrElseGet(final T1 value,
                                                 final Function<T1, T2> getter1,
                                                 final Function<T2, T3> getter2,
                                                 final Function<T3, R> getter3,
                                                 final Supplier<R> otherSupplier) {
        return requireNonNullElseGet(get(value, getter1, getter2, getter3), otherSupplier);
    }

    public static <T1, T2, T3, R> Optional<R> getAsOptional(final T1 value,
                                                            final Function<T1, T2> getter1,
                                                            final Function<T2, T3> getter2,
                                                            final Function<T3, R> getter3) {
        if (value == null) {
            return Optional.empty();
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return Optional.empty();
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return Optional.empty();
                } else {
                    return Optional.ofNullable(Objects.requireNonNull(getter3).apply(value3));
                }
            }
        }
    }

    /**
     * If value is non-null apply getter1 to it.
     * If the result of that is non-null apply getter2 to the result.
     * If the result of that is non-null consume the result.
     */
    public static <T1, T2, T3> void consume(final T1 value,
                                            final Function<T1, T2> getter1,
                                            final Function<T2, T3> getter2,
                                            final Consumer<T3> consumer) {
        if (value != null && consumer != null) {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 != null) {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 != null) {
                    consumer.accept(value3);
                }
            }
        }
    }

    public static <T1, T2, T3> String toString(final T1 value,
                                               final Function<T1, T2> getter1,
                                               final Function<T2, T3> getter2,
                                               final Function<T3, Object> getter3) {
        return toStringOrElse(value, getter1, getter2, getter3, null);
    }

    public static <T1, T2, T3> String toStringOrElse(final T1 value,
                                                     final Function<T1, T2> getter1,
                                                     final Function<T2, T3> getter2,
                                                     final Function<T3, Object> getter3,
                                                     final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return handleNull(otherSupplier);
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return handleNull(otherSupplier);
                } else {
                    final Object value4 = Objects.requireNonNull(getter3).apply(value3);
                    return convertToString(value4, otherSupplier);
                }
            }
        }
    }

    public static <T1, T2, T3, T4, R> R get(final T1 value,
                                            final Function<T1, T2> getter1,
                                            final Function<T2, T3> getter2,
                                            final Function<T3, T4> getter3,
                                            final Function<T4, R> getter4) {
        if (value == null) {
            return null;
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return null;
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return null;
                } else {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 == null) {
                        return null;
                    } else {
                        return Objects.requireNonNull(getter4).apply(value4);
                    }
                }
            }
        }
    }

    public static <T1, T2, T3, T4, R> R getOrElse(final T1 value,
                                                  final Function<T1, T2> getter1,
                                                  final Function<T2, T3> getter2,
                                                  final Function<T3, T4> getter3,
                                                  final Function<T4, R> getter4,
                                                  final R other) {
        return requireNonNullElse(get(value, getter1, getter2, getter3, getter4), other);
    }

    public static <T1, T2, T3, T4, R> R getOrElseGet(final T1 value,
                                                     final Function<T1, T2> getter1,
                                                     final Function<T2, T3> getter2,
                                                     final Function<T3, T4> getter3,
                                                     final Function<T4, R> getter4,
                                                     final Supplier<R> otherSupplier) {
        return requireNonNullElseGet(get(value, getter1, getter2, getter3, getter4), otherSupplier);
    }

    @SuppressWarnings("unused")
    public static <T1, T2, T3, T4, R> Optional<R> getAsOptional(final T1 value,
                                                                final Function<T1, T2> getter1,
                                                                final Function<T2, T3> getter2,
                                                                final Function<T3, T4> getter3,
                                                                final Function<T4, R> getter4) {
        if (value == null) {
            return Optional.empty();
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return Optional.empty();
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return Optional.empty();
                } else {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 == null) {
                        return Optional.empty();
                    } else {
                        return Optional.ofNullable(Objects.requireNonNull(getter4).apply(value4));
                    }
                }
            }
        }
    }

    /**
     * If value is non-null apply getter1 to it.
     * If the result of that is non-null apply getter2 to the result.
     * If the result of that is non-null consume the result.
     */
    public static <T1, T2, T3, T4> void consume(final T1 value,
                                                final Function<T1, T2> getter1,
                                                final Function<T2, T3> getter2,
                                                final Function<T3, T4> getter3,
                                                final Consumer<T4> consumer) {
        if (value != null && consumer != null) {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 != null) {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 != null) {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 != null) {
                        consumer.accept(value4);
                    }
                }
            }
        }
    }

    public static <T1, T2, T3, T4> String toStringOrElse(final T1 value,
                                                         final Function<T1, T2> getter1,
                                                         final Function<T2, T3> getter2,
                                                         final Function<T3, T4> getter3,
                                                         final Function<T4, Object> getter4,
                                                         final Supplier<String> otherSupplier) {
        if (value == null) {
            return handleNull(otherSupplier);
        } else {
            final T2 value2 = Objects.requireNonNull(getter1).apply(value);
            if (value2 == null) {
                return handleNull(otherSupplier);
            } else {
                final T3 value3 = Objects.requireNonNull(getter2).apply(value2);
                if (value3 == null) {
                    return handleNull(otherSupplier);
                } else {
                    final T4 value4 = Objects.requireNonNull(getter3).apply(value3);
                    if (value4 == null) {
                        return handleNull(otherSupplier);
                    } else {
                        final Object value5 = Objects.requireNonNull(getter4).apply(value4);
                        return convertToString(value5, otherSupplier);
                    }
                }
            }
        }
    }

    private static <T> T handleNull(final Supplier<T> otherSupplier) {
        if (otherSupplier != null) {
            return otherSupplier.get();
        } else {
            return null;
        }
    }

    private static String convertToString(final Object value, final Supplier<String> otherSupplier) {
        if (value != null) {
            return value.toString();
        } else {
            if (otherSupplier != null) {
                return Objects.requireNonNull(otherSupplier).get();
            } else {
                return null;
            }
        }
    }

    private static String convertToString(final Object value, final String other) {
        if (value != null) {
            return value.toString();
        } else {
            return other;
        }
    }

    /**
     * GWT currently doesn't emulate requireNonNullElse
     */
    public static <T> T requireNonNullElse(T obj, T other) {
        return (obj != null)
                ? obj
                : Objects.requireNonNull(other, "other");
    }

    /**
     * GWT currently doesn't emulate requireNonNullElse
     */
    public static <T> T requireNonNullElseGet(T obj, Supplier<? extends T> supplier) {
        return (obj != null)
                ? obj
                : Objects.requireNonNull(
                        Objects.requireNonNull(supplier, "supplier").get(),
                        "supplier.get()");
    }

}
