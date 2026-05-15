/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.collections;

import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.collection.GwtCollectionUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionUtil {

    private CollectionUtil() {
        // Static util stuff only
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the order in items array. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledOrderedSet(final T item) {
        return item != null
                ? Collections.singleton(item)
                : Collections.emptySet();
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the items in natural sort order. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final T... items) {
        return GwtCollectionUtil.asUnmodifiabledConsistentOrderSet(items);
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the items in natural sort order. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final Set<T> items) {
        return GwtCollectionUtil.asUnmodifiabledConsistentOrderSet(items);
    }

    /**
     * Return a {@link Set} containing items, with iteration order matching
     * the items in natural sort order. Useful in config classes where a HashSet would
     * result in an undefined order on serialisation.
     */
    public static <T> Set<T> asUnmodifiabledConsistentOrderSet(final List<T> items) {
        return GwtCollectionUtil.asUnmodifiabledConsistentOrderSet(items);
    }

    public static <K, V> LinkedHashMapBuilder<K, V> linkedHashMapBuilder(final Class<K> keyType,
                                                                         final Class<V> valueType) {
        return new LinkedHashMapBuilder<>(keyType, valueType);
    }

    /**
     * Removes null items, applies formatter on each item, then removes any empty items.
     * Is null safe.
     */
    public static Set<String> cleanItems(final Set<String> items) {
        return cleanItems(items, null);
    }

    /**
     * Removes null items then applies formatter on each item, then removes any items that are
     * an empty String.
     *
     * @return An unmodifiable Set of the cleaned items which may be empty if all items
     * have been removed.
     */
    public static Set<String> cleanItems(final Set<String> items,
                                         final Function<String, String> formatter) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptySet();
        } else {
            Stream<String> stringStream = NullSafe.stream(items)
                    .filter(Objects::nonNull);
            if (formatter != null) {
                stringStream = stringStream.map(formatter);
            }
            return stringStream
                    .filter(NullSafe::isNonEmptyString)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    /**
     * Removes null items, applies formatter on each item, then removes any empty items.
     * Also removes duplicates.
     * Is null safe.
     */
    public static List<String> cleanItems(final List<String> items) {
        return cleanItems(items, null, true);
    }

    /**
     * Removes null items, applies formatter on each item, then removes any empty items.
     * Is null safe.
     */
    public static List<String> cleanItems(final List<String> items,
                                          final Function<String, String> formatter,
                                          final boolean removeDuplicates) {
        if (NullSafe.isEmptyCollection(items)) {
            return Collections.emptyList();
        } else {
            Stream<String> stringStream = NullSafe.stream(items)
                    .filter(Objects::nonNull);
            if (formatter != null) {
                stringStream = stringStream.map(formatter);
            }
            stringStream = stringStream
                    .filter(NullSafe::isNonEmptyString);

            if (removeDuplicates) {
                stringStream = stringStream.distinct();
            }
            return stringStream.toList();
        }
    }

    private static <V> BinaryOperator<V> createMergeFunction(final DuplicateMode duplicateMode) {
        return switch (Objects.requireNonNull(duplicateMode)) {
            case THROW -> (v1, v2) -> {
                throw new IllegalStateException(LogUtil.message(
                        "Duplicate values found for the same key, v1: {}, v2: {}", v1, v2));
            };
            case USE_FIRST -> (v1, v2) -> v1;
            case USE_LAST -> (v1, v2) -> v2;
        };
    }

    /**
     * Convert a {@link Collection} of values into a {@link Map} of those values
     * keyed using keyExtractor.
     * <p>
     * If duplicate values are found, the first value encountered is put in the map.
     * </p>
     *
     * @param duplicateMode How to handle multiple values for the same key.
     */
    public static <K, V> Map<K, V> mapBy(final Function<V, K> keyExtractor,
                                         final DuplicateMode duplicateMode,
                                         final Collection<V> values) {
        return NullSafe.stream(values)
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        createMergeFunction(duplicateMode)));
    }

    /**
     * Convert an array of values into a {@link EnumMap} of those values
     * keyed using keyExtractor.
     * <p>
     * If duplicate values are found, the first value encountered is put in the map.
     * </p>
     *
     * @param duplicateMode How to handle multiple values for the same key.
     */
    public static <K, V> Map<K, V> mapBy(final Function<V, K> keyExtractor,
                                         final DuplicateMode duplicateMode,
                                         final V... values) {
        return NullSafe.stream(values)
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        createMergeFunction(duplicateMode)));
    }

    /**
     * Convert a {@link Collection} of values into a {@link EnumMap} of those values
     * keyed using keyExtractor.
     * <p>
     * If duplicate values are found, the first value encountered is put in the map.
     * </p>
     *
     * @param duplicateMode How to handle multiple values for the same key.
     */
    public static <K extends Enum<K>, V> Map<K, V> enumMapBy(final Class<K> enumType,
                                                             final Function<V, K> keyExtractor,
                                                             final DuplicateMode duplicateMode,
                                                             final Collection<V> values) {
        return NullSafe.stream(values)
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        createMergeFunction(duplicateMode),
                        () -> new EnumMap<>(enumType)));
    }

    /**
     * Convert a {@link Collection} of values into a {@link Map} of those values
     * keyed using keyExtractor.
     * <p>
     * If duplicate values are found, the first value encountered is put in the map.
     * </p>
     *
     * @param duplicateMode How to handle multiple values for the same key.
     */
    public static <K extends Enum<K>, V> Map<K, V> enumMapBy(final Class<K> enumType,
                                                             final Function<V, K> keyExtractor,
                                                             final DuplicateMode duplicateMode,
                                                             final V... values) {
        return NullSafe.stream(values)
                .collect(Collectors.toMap(
                        keyExtractor,
                        Function.identity(),
                        createMergeFunction(duplicateMode),
                        () -> new EnumMap<>(enumType)));
    }

    /**
     * Gets a combined count of the number of items in each value in the map, where the value
     * is a collection.
     *
     * @return The sum of items in all values.
     */
    public static int sumValueSizes(final Map<?, ? extends Collection<?>> map) {
        if (NullSafe.isEmptyMap(map)) {
            return 0;
        } else {
            return map.values()
                    .stream()
                    .mapToInt(Collection::size)
                    .sum();
        }
    }

    /**
     * Creates a function to map just the key of a {@link Entry}.
     */
    public static <K1, K2, V> Function<Entry<K1, V>, Entry<K2, V>> createKeyMapper(
            final Function<K1, K2> keyMapper) {
        Objects.requireNonNull(keyMapper);
        return (Entry<K1, V> entry) ->
                Map.entry(keyMapper.apply(entry.getKey()), entry.getValue());
    }

    /**
     * Convert the supplied map, mapping each key in it using keyMapper.
     * Null keys are not allowed.
     * If keyMapper causes duplicate keys an exception will be thrown.
     */
    public static <K1, K2, V> Map<K2, V> mappingKeys(final Map<K1, V> map,
                                                     final Function<K1, K2> keyMapper) {
        if (map == null) {
            return null;
        } else if (map.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Objects.requireNonNull(keyMapper);
            return map.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> keyMapper.apply(entry.getKey()),
                            Entry::getValue));
        }
    }

    /**
     * Creates a function to map just the value of a {@link Entry}.
     */
    public static <K, V1, V2> Function<Entry<K, V1>, Entry<K, V2>> createValueMapper(
            final Function<V1, V2> valueMapper) {
        Objects.requireNonNull(valueMapper);
        return (Entry<K, V1> entry) ->
                Map.entry(entry.getKey(), valueMapper.apply(entry.getValue()));
    }

    /**
     * Convert the supplied map, mapping each value in it using valueMapper.
     * Null keys are not allowed.
     */
    public static <K, V1, V2> Map<K, V2> mappingValues(final Map<K, V1> map,
                                                       final Function<V1, V2> valueMapper) {
        if (map == null) {
            return null;
        } else if (map.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Objects.requireNonNull(valueMapper);
            return map.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> valueMapper.apply(entry.getValue())));
        }
    }


    // --------------------------------------------------------------------------------


    public enum DuplicateMode {
        /**
         * Throw an exception if multiple values exist for the same key.
         */
        THROW,
        /**
         * If multiple values exist for the same key, use the first value encountered.
         */
        USE_FIRST,
        /**
         * If multiple values exist for the same key, use the last value encountered.
         */
        USE_LAST,
        ;
    }


    // --------------------------------------------------------------------------------


    public static class LinkedHashMapBuilder<K, V> {

        private SequencedMap<K, V> map = null;

        private LinkedHashMapBuilder(final Class<K> ignoredKeyType,
                                     final Class<V> ignoredValueType) {
            // types to aid generics
        }

        public LinkedHashMapBuilder<K, V> add(final K key, final V val) {
            map = Objects.requireNonNullElseGet(map, LinkedHashMap::new);
            map.put(key, val);
            return this;
        }

        public SequencedMap<K, V> build() {
            return map;
        }
    }
}
