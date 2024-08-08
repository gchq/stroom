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

package stroom.meta.api;

import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.string.CIHashMap;
import stroom.util.shared.string.CIKey;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Map that does not care about key case.
 */
public class AttributeMap extends CIHashMap<String> {

    private final boolean overrideEmbeddedMeta;

    public AttributeMap(final boolean overrideEmbeddedMeta) {
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
    }

    public AttributeMap(final boolean overrideEmbeddedMeta,
                        final Map<CIKey, String> values) {
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
        putAll(values);
    }

    public AttributeMap() {
        this.overrideEmbeddedMeta = false;
    }

    public AttributeMap(final Map<CIKey, String> values) {
        this.overrideEmbeddedMeta = false;
        putAll(values);
    }

//    public AttributeMap(final Map<String, String> values) {
//        this.overrideEmbeddedMeta = false;
//        putAllStringKeys(values);
//    }

    private AttributeMap(final Builder builder) {
        overrideEmbeddedMeta = builder.overrideEmbeddedMeta;
        putAll(builder.attributes);
    }

    public Map<String, String> asStringKeyedMap() {
        return entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().get(), Entry::getValue));
    }

    public Set<Entry<String, String>> stringEntrySet() {
        return entrySet().stream()
                .map(entry -> Map.entry(entry.getKey().get(), entry.getValue()))
                .collect(Collectors.toSet());
    }

    public Set<String> stringKeySet() {
        return keySet()
                .stream()
                .map(CIKey::get)
                .collect(Collectors.toSet());
    }

    public String put(final String key, final String value) {
        // CIString used to trim all keys
        return put(CIKey.trimmed(key), value);
    }

    @Override
    public String put(final CIKey key, final String value) {
        Objects.requireNonNull(key);
        if (StandardHeaderArguments.DATE_HEADER_KEYS.contains(key)) {
            final String normalisedValue = DateUtil.normaliseDate(value, true);
            return super.put(key, normalisedValue);
        } else {
            return super.put(key, value);
        }
    }

    // Overload all the super methods that take key as an Object as the compiler
    // won't spot people using a String key.
    public String get(final String key) {
        // CIString used to trim all keys
        return super.get(CIKey.trimmed(key));
    }

    public String getOrDefault(final String key, final String defaultValue) {
        // CIString used to trim all keys
        return super.getOrDefault(CIKey.trimmed(key), defaultValue);
    }

    public boolean remove(final String key, final Object value) {
        // CIString used to trim all keys
        return super.remove(CIKey.trimmed(key), value);
    }

    public boolean containsKey(final String key) {
        // CIString used to trim all keys
        return super.containsKey(CIKey.trimmed(key));
    }

    /**
     * @return True if the key is in the map (ignoring case)
     */
    public boolean containsStringKey(final String key) {
        // CIString used to trim all keys
        return super.containsKey(CIKey.trimmed(key));
    }

    /**
     * Equivalent to {@link Map#putAll(Map)} but for a map keyed by strings.
     */
    public void putAllWithStringKeys(final Map<String, String> map) {
        GwtNullSafe.map(map)
                .forEach(this::put);
    }

    public Set<String> keySetAsString() {
        return keySet()
                .stream()
                .map(CIKey::get)
                .collect(Collectors.toSet());
    }

    /**
     * Puts the current date time into the map in Stroom standard ISO 8601 format,
     * e.g. {@link DateUtil#createNormalDateTimeString()} using the specified key.
     *
     * @return The previous value for the key.
     */
    public String putCurrentDateTime(final CIKey key) {
        // Already normalised, so use super.put not the local one
        return super.put(key, DateUtil.createNormalDateTimeString());
    }

    /**
     * Puts the specified date time (as epoch millis) into the map in Stroom standard ISO 8601 format,
     * e.g. {@link DateUtil#createNormalDateTimeString()} using the specified key.
     *
     * @return The previous value for the key.
     */
    public String putDateTime(final CIKey key, final Long epochMs) {
        final String dateStr = DateUtil.createNormalDateTimeString(epochMs);
        // Already normalised, so use super.put not the local one
        return super.put(key, dateStr);
    }

    /**
     * Puts the specified {@link Instant} into the map in Stroom standard ISO 8601 format,
     * e.g. {@link DateUtil#createNormalDateTimeString()} using the specified key.
     *
     * @return The previous value for the key.
     */
    public String putDateTime(final CIKey key, final Instant instant) {
        if (instant == null) {
            return super.put(key, null);
        } else {
            final String dateStr = DateUtil.createNormalDateTimeString(instant.toEpochMilli());
            // Already normalised, so use super.put not the local one
            return super.put(key, dateStr);
        }
    }

    /**
     * Appends the time to the end of the existing value (delimited by {@link AttributeMapUtil#VALUE_DELIMITER})
     * or sets the value if not present. instant is converted to a normal date time string
     *
     * @return The previous value for the key.
     */
    public String appendDateTime(final CIKey key, final Instant instant) {
        if (instant != null) {
            String val = super.get(key);
            final String dateStr = DateUtil.createNormalDateTimeString(instant.toEpochMilli());
            if (NullSafe.isEmptyString(val)) {
                val = dateStr;
            } else {
                if (!val.endsWith(AttributeMapUtil.VALUE_DELIMITER)) {
                    val += AttributeMapUtil.VALUE_DELIMITER;
                }
                val += dateStr;
            }
            return super.put(key, val);
        } else {
            return null;
        }
    }

    /**
     * Appends the time to the end of the existing value (delimited by {@link AttributeMapUtil#VALUE_DELIMITER})
     * or sets the value if not present. instant is converted to a normal date time string
     *
     * @return The previous value for the key.
     */
    public String appendItem(final CIKey key, final String item) {
        if (item != null) {
            String normalisedItem = item;
            if (key != null && StandardHeaderArguments.DATE_HEADER_KEYS.contains(key)) {
                normalisedItem = DateUtil.normaliseDate(item, true);
            }
            String val = super.get(key);
            if (NullSafe.isEmptyString(val)) {
                val = normalisedItem;
            } else {
                if (!val.endsWith(AttributeMapUtil.VALUE_DELIMITER)) {
                    val += AttributeMapUtil.VALUE_DELIMITER;
                }
                val += normalisedItem;
            }
            return super.put(key, val);
        } else {
            return null;
        }
    }

    /**
     * Appends the item to the end of the existing value (delimited by {@link AttributeMapUtil#VALUE_DELIMITER})
     * or sets the value if not present, but only if currentValuePredicate returns true. If currentValuePredicate
     * returns false, no change is made to the map
     *
     * @param currentValuePredicate {@link Predicate} to test the current value for the key to determine whether
     *                              to append the item or not. The {@link Predicate} accepts the current value or
     *                              null if there is no entry for the key.
     * @return The previous value for the key.
     */
    public String appendItemIf(final CIKey key,
                               final String item,
                               final Predicate<String> currentValuePredicate) {
        if (item != null) {
            String normalisedItem = item;
            if (key != null && StandardHeaderArguments.DATE_HEADER_KEYS.contains(key)) {
                normalisedItem = DateUtil.normaliseDate(item, true);
            }
            String val = super.get(key);
            Objects.requireNonNull(currentValuePredicate);
            if (currentValuePredicate.test(val)) {
                if (NullSafe.isEmptyString(val)) {
                    val = normalisedItem;
                } else {
                    if (!val.endsWith(AttributeMapUtil.VALUE_DELIMITER)) {
                        val += AttributeMapUtil.VALUE_DELIMITER;
                    }
                    val += normalisedItem;
                }
                return super.put(key, val);
            } else {
                return val;
            }
        } else {
            return null;
        }
    }

//    /**
//     * Put an entry where the value is itself a collection of values, e.g. a list of files
//     */
//    public String putCollection(final String key, Collection<String> values) {
//        return putCollection(CIKey.of(key), values);
//    }

    /**
     * Put an entry where the value is itself a collection of values, e.g. a list of files
     */
    public String putCollection(final CIKey key, Collection<String> values) {
        final String value;
        if (values == null) {
            value = null;
        } else if (values.isEmpty()) {
            value = "";
        } else {
            value = values.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .collect(Collectors.joining(AttributeMapUtil.VALUE_DELIMITER));
        }
        return put(key, value);
    }

//    /**
//     * Get the value for a given key as a {@link List}, e.g. where the value is known to be a
//     * delimited collection of items. If the value only contains one item, then a singleton
//     * {@link List} is returned.
//     */
//    public List<String> getAsList(final String key) {
//        return getAsList(CIKey.of(key));
//    }

    /**
     * Get the value for a given key as a {@link List}, e.g. where the value is known to be a
     * delimited collection of items. If the value only contains one item, then a singleton
     * {@link List} is returned.
     */
    public List<String> getAsList(final CIKey key) {
        final String val = get(key);
        if (NullSafe.isEmptyString(val)) {
            return Collections.emptyList();
        } else {
            return AttributeMapUtil.VALUE_DELIMITER_PATTERN.splitAsStream(val)
                    .toList();
        }
    }

    /**
     * Get a value from the {@link AttributeMap}, mapping it to a T if it is non-null.
     *
     * @return The value mapped to type T if key exists and has a non-null value, else null.
     */
    public <T> T getAs(final CIKey key, Function<String, T> mapper) {
        Objects.requireNonNull(mapper);
        return GwtNullSafe.get(get(key), mapper);
    }

//    public boolean isDelimited(final String key) {
//        return isDelimited(CIKey.of(key));
//    }

    public boolean isDelimited(final CIKey key) {
        final String val = get(key);
        return NullSafe.test(val, val2 ->
                val2.contains(AttributeMapUtil.VALUE_DELIMITER));
    }

    public static Builder copy(final AttributeMap copy) {
        final Builder builder = new Builder();
        builder.overrideEmbeddedMeta = copy.overrideEmbeddedMeta;
        builder.attributes = new AttributeMap();
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

//    public void removeAllStringKeys(final Collection<String> keySet) {
//        if (keySet != null) {
//            for (final String key : keySet) {
//                remove(CIKey.of(key));
//            }
//        }
//    }

    public void removeAll(final Collection<CIKey> keySet) {
        if (keySet != null) {
            for (final CIKey key : keySet) {
                remove(key);
            }
        }
    }

    /**
     * @return A new {@link AttributeMap} that includes only the entries whose keys are in includeKeys
     */
    public AttributeMap filterIncluding(final Set<CIKey> includeKeys) {
        if (GwtNullSafe.isEmptyCollection(includeKeys)) {
            return this;
        } else {
            return entrySet()
                    .stream()
                    .filter(entry -> includeKeys.contains(entry.getKey()))
                    .collect(collector());
        }
    }

    /**
     * @return A new {@link AttributeMap} that includes only the entries whose keys are not in excludeKeys
     */
    public AttributeMap filterExcluding(final Set<CIKey> excludeKeys) {
        if (GwtNullSafe.isEmptyCollection(excludeKeys)) {
            return this;
        } else {
            return entrySet()
                    .stream()
                    .filter(entry -> !excludeKeys.contains(entry.getKey()))
                    .collect(collector());
        }
    }

    public boolean isOverrideEmbeddedMeta() {
        return overrideEmbeddedMeta;
    }

    /**
     * @return A {@link Collector} for use with the {@link java.util.stream.Stream} API.
     */
    public static Collector<Entry<CIKey, String>, AttributeMap, AttributeMap> collector() {
        return AttributeMapCollector.INSTANCE;
    }


    // --------------------------------------------------------------------------------


    public static class AttributeMapCollector
            implements Collector<Entry<CIKey, String>, AttributeMap, AttributeMap> {

        private static final Collector<Entry<CIKey, String>, AttributeMap, AttributeMap> INSTANCE =
                new AttributeMapCollector();

        @Override
        public Supplier<AttributeMap> supplier() {
            return AttributeMap::new;
        }

        @Override
        public BiConsumer<AttributeMap, Entry<CIKey, String>> accumulator() {
            return (map, entry) -> {
                if (entry != null) {
                    map.put(entry.getKey(), entry.getValue());
                }
            };
        }

        @Override
        public BinaryOperator<AttributeMap> combiner() {
            return (map, map2) -> {
                map.putAll(map2);
                return map;
            };
        }

        @Override
        public Function<AttributeMap, AttributeMap> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
        }
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private boolean overrideEmbeddedMeta = false;
        private AttributeMap attributes = new AttributeMap();

        private Builder() {
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder withOverrideEmbeddedMeta(final boolean val) {
            overrideEmbeddedMeta = val;
            return this;
        }

        public Builder overrideEmbeddedMeta() {
            overrideEmbeddedMeta = true;
            return this;
        }

        /**
         * You really ought to be using a {@link CIKey} key.
         */
        @Deprecated
        public Builder put(final String key, final String value) {
            this.put(CIKey.trimmed(key), value);
            return this;
        }

        public Builder put(final CIKey key, final String value) {
            Objects.requireNonNull(key);
            attributes.put(key, GwtNullSafe.trim(value));
            return this;
        }

        /**
         * You really ought to be using a {@link CIKey} key.
         */
        @Deprecated
        public Builder putCollection(final String key, Collection<String> values) {
            putCollection(CIKey.of(key), values);
            return this;
        }

        public Builder putCollection(final CIKey key, Collection<String> values) {
            Objects.requireNonNull(key);
            attributes.putCollection(key, values);
            return this;
        }

        public AttributeMap build() {
            return new AttributeMap(this);
        }
    }
}
