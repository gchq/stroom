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

package stroom.util.shared.string;

import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A wrapper for a {@link String} whose {@link CIKey#equals(Object)} and
 * {@link CIKey#hashCode()} methods are performed on the lower-case
 * form of {@code key}.
 * <p>
 * Useful as a case-insensitive cache key that retains the case of the
 * original string at the cost of wrapping it in another object.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class CIKey implements Comparable<CIKey> {

    public static final CIKey NULL_STRING = new CIKey(null);
    public static final CIKey EMPTY_STRING = new CIKey("");

    // Hold some common keys, so we can just re-use instances rather than creating new each time
    // Some of these map values will get held as static CIKey variables elsewhere in the code
    // that get used programmatically.
    // Intern the lowerKey so they are in the string pool
    private static final Map<String, CIKey> COMMON_KEYS = Stream.of(
                    "CreatedBy",
                    "CreatedOn",
                    "EventId",
                    "EventTime",
                    "Feed",
                    "Id",
                    "Name",
                    "Node",
                    "Partition",
                    "Status",
                    "StreamId",
                    "Subject",
                    "Time",
                    "Title",
                    "Type",
                    "UUID",
                    "UpdatedBy",
                    "UpdatedOn",
                    "Value",
                    "__event_id__",
                    "__stream_id__",
                    "__time__")
            .collect(Collectors.toMap(
                    Function.identity(),
                    k -> new CIKey(k, toLowerCase(k).intern())));

    @JsonProperty
    private final String key;
    @JsonIgnore
    private transient final String lowerKey;

    @JsonCreator
    private CIKey(@JsonProperty("key") final String key) {
        this.key = key;
        this.lowerKey = toLowerCase(key);
    }

    private static String toLowerCase(final String str) {
        return GwtNullSafe.get(str, s -> s.toLowerCase(Locale.ENGLISH));
    }

    /**
     * key and lowerKey must be equal ignoring case.
     *
     * @param key      The key
     * @param lowerKey The key converted to lower-case
     */
    private CIKey(final String key, final String lowerKey) {
        this.key = key;
        this.lowerKey = lowerKey;
    }

    public static CIKey of(final String key, final String lowerKey) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    COMMON_KEYS.get(key),
                    () -> new CIKey(key, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo".
     * If key is all lower case then user {@link CIKey#ofLowerCase(String)}.
     */
    public static CIKey of(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    COMMON_KEYS.get(key),
                    () -> new CIKey(key));
        }
    }

    /**
     * Create a {@link CIKey} for an all lower case key, e.g. "foo".
     */
    public static CIKey ofLowerCase(final String lowerKey) {
        if (lowerKey == null) {
            return NULL_STRING;
        } else if (lowerKey.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    COMMON_KEYS.get(lowerKey),
                    () -> new CIKey(lowerKey, lowerKey));
        }
    }

    /**
     * @return The wrapped string in its original case.
     */
    public String get() {
        return key;
    }

    @JsonIgnore
    public String getAsLowerCase() {
        return lowerKey;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CIKey that = (CIKey) object;
        return Objects.equals(lowerKey, that.lowerKey);
    }

    @Override
    public int hashCode() {
        // String lazily caches its hashcode so no need for us to do it too
        return lowerKey != null
                ? lowerKey.hashCode()
                : 0;
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public int compareTo(final CIKey o) {
        return lowerKey.compareTo(o.lowerKey);
    }

    /**
     * Returns true if the string this {@link CIKey} wraps contains subString
     * ignoring case.
     * If subString is all lower case, use {@link CIKey#containsLowerCase(String)} instead.
     */
    public boolean containsIgnoreCase(final String subString) {
        Objects.requireNonNull(subString);
        if (lowerKey == null) {
            return false;
        }
        return lowerKey.contains(toLowerCase(subString));
    }

    /**
     * Returns true if the string this {@link CIKey} wraps contains lowerSubString
     * which MUST be all lower case.
     * If lowerSubString is mixed or upper case, use {@link CIKey#containsIgnoreCase(String)} instead.
     */
    public boolean containsLowerCase(final String lowerSubString) {
        Objects.requireNonNull(lowerSubString);
        if (lowerKey == null) {
            return false;
        }
        return lowerKey.contains(toLowerCase(lowerSubString));
    }

    /**
     * Create a case-insensitive keyed {@link Entry} from a {@link String} key and value of type T.
     */
    public static <V> Entry<CIKey, V> entry(final String key, final V value) {
        return Map.entry(CIKey.of(key), value);
    }

    /**
     * Create a case-insensitive keyed {@link Entry} from a simple {@link String} keyed {@link Entry}.
     */
    public static <T> Entry<CIKey, T> entry(final Entry<String, T> entry) {
        if (entry == null) {
            return null;
        } else {
            return Map.entry(CIKey.of(entry.getKey()), entry.getValue());
        }
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1) {
        return Map.of(CIKey.of(k1), v1);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1, String k2, V v2) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5,
                                          String k6, V v6) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5,
                CIKey.of(k6), v6);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5,
                                          String k6, V v6,
                                          String k7, V v7) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5,
                CIKey.of(k6), v6,
                CIKey.of(k7), v7);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5,
                                          String k6, V v6,
                                          String k7, V v7,
                                          String k8, V v8) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5,
                CIKey.of(k6), v6,
                CIKey.of(k7), v7,
                CIKey.of(k8), v8);
    }

    /**
     * Convert an array of {@link String} keyed entries into a {@link CIKey} keyed map.
     */
    @SafeVarargs
    public static <V> Map<CIKey, V> mapOfEntries(final Entry<String, ? extends V>... entries) {
        return GwtNullSafe.stream(entries)
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue));
    }

    /**
     * Convert a {@link String} keyed map into a {@link CIKey} keyed map.
     */
    public static <V> Map<CIKey, V> mapOf(final Map<String, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue));
    }

    public static <V> Map<String, V> convertToStringMap(final Map<CIKey, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().get(),
                        Entry::getValue));
    }

    public static <V> Map<String, V> convertToLowerCaseStringMap(final Map<CIKey, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getAsLowerCase(),
                        Entry::getValue));
    }

    public static <V> V put(final Map<CIKey, V> map,
                            final String key,
                            final V value) {
        return map.put(CIKey.of(key), value);
    }

    /**
     * True if str is equal to the string wrapped by ciKey, ignoring case.
     */
    public static boolean equalsIgnoreCase(final String str, final CIKey ciKey) {
        final String lowerKey = ciKey.lowerKey;
        if (lowerKey == null && str == null) {
            return true;
        } else {
            return lowerKey != null && lowerKey.equalsIgnoreCase(str);
        }
    }

    /**
     * True if str is equal to the string wrapped by ciKey, ignoring case.
     */
    public static boolean equalsIgnoreCase(final CIKey ciKey, final String str) {
        return equalsIgnoreCase(str, ciKey);
    }
}
