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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A wrapper for a {@link String} whose {@link CIKey#equals(Object)} and
 * {@link CIKey#hashCode()} methods are performed on the lower-case
 * form of {@code key}.
 * <p>
 * Useful as a case-insensitive cache key that retains the case of the
 * original string at the cost of wrapping it in another object.
 * </p>
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class CIKey implements Comparable<CIKey> {

    public static final CIKey NULL_STRING = new CIKey(null);
    public static final CIKey EMPTY_STRING = new CIKey("");

    // Compare on the lower case form of the key. CIKey may be null and lowerKey may be null
    public static final Comparator<CIKey> COMPARATOR = Comparator.nullsFirst(Comparator.comparing(
            CIKey::getAsLowerCase,
            Comparator.nullsFirst(String::compareTo)));

    @JsonValue // No need to serialise the CIKey wrapper, just the key
    private final String key;

    @JsonIgnore
    private final transient String lowerKey;

    @JsonCreator
    private CIKey(final String key) {
        this.key = key;
        this.lowerKey = toLowerCase(key);
    }

    /**
     * key and lowerKey must be equal ignoring case.
     *
     * @param key      The key
     * @param lowerKey The key converted to lower-case
     */
    CIKey(final String key, final String lowerKey) {
        this.key = key;
        this.lowerKey = lowerKey;
    }

    /**
     * Create a {@link CIKey} for an unknown, upper or mixed case key, e.g. "FOO", or "Foo".
     * If key is known to be all lower case then use {@link CIKey#ofLowerCase(String)}.
     * If key is a common key this method will return an existing {@link CIKey} instance
     * else it will create a new instance.
     * <p>
     * The returned {@link CIKey} will wrap key with no change of case and no trimming.
     * </p>
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
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> new CIKey(key));
        }
    }

    /**
     * Equivalent to calling {@link CIKey#of(String)} with a trimmed key.
     */
    public static CIKey trimmed(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else {
            final String trimmed = key.trim();
            return CIKey.of(trimmed);
        }
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo",
     * when you already know the lower-case form of the key.
     * If key is all lower case then user {@link CIKey#ofLowerCase(String)}.
     * If key is a common key this method will return an existing {@link CIKey} instance
     * else it will create a new instance.
     */
    public static CIKey of(final String key, final String lowerKey) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> new CIKey(key, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for key, providing a map of known {@link CIKey}s keyed
     * on their key value. Allows callers to hold their own set of known {@link CIKey}s.
     */
    public static CIKey of(final String key, final Map<String, CIKey> knownKeys) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            CIKey ciKey = null;
            if (knownKeys != null) {
                ciKey = knownKeys.get(key);
            }
            if (ciKey == null) {
                ciKey = CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key);
                if (ciKey == null) {
                    ciKey = new CIKey(key);
                }
            }
            return ciKey;
        }
    }

    /**
     * Create a {@link CIKey} for an all lower case key, e.g. "foo".
     * This is a minor optimisation to avoid a call to toLowerCase as the
     * key is already in lower-case.
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
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(lowerKey),
                    () -> new CIKey(lowerKey, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for a key that is known NOT to be in {@link CIKey}s list
     * of common keys and is a key that will not be added to the list of common keys in future.
     * This is a minor optimisation.
     */
    public static CIKey ofDynamicKey(final String dynamicKey) {
        if (dynamicKey == null) {
            return NULL_STRING;
        } else if (dynamicKey.isEmpty()) {
            return EMPTY_STRING;
        } else {
            return new CIKey(dynamicKey);
        }
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo",
     * that will be held as a static variable. This has the additional cost of
     * interning the lower-case form of the key. Only use this for static {@link CIKey}
     * instances as
     */
    public static CIKey ofStaticKey(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> CIKeys.commonKey(key));
        }
    }

    /**
     * If ciKey matches a common {@link CIKey} (ignoring case) then return the common
     * {@link CIKey} else return ciKey. Use this if you don't care about the case of the
     * wrapped string, e.g. if key is 'FOO', you could get back a {@link CIKey} that wraps
     * 'foo', 'FOO', 'Foo', etc.
     */
    public static CIKey ofIgnoringCase(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            final String lowerKey = toLowerCase(key);
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.LOWER_KEY_TO_COMMON_CIKEY_MAP.get(lowerKey),
                    () -> CIKey.ofLowerCase(lowerKey));
        }
    }

    /**
     * @return The wrapped string in its original case.
     */
    @JsonIgnore
    public String get() {
        return key;
    }

    /**
     * Here for JSON (de-)ser.
     */
    private String getKey() {
        return key;
    }

    @JsonIgnore
    public String getAsLowerCase() {
        return lowerKey;
    }

    public boolean equalsIgnoreCase(final String str) {
        return CIKey.equalsIgnoreCase(this, str);
    }

    /**
     * Standard equals method for comparing two {@link CIKey} instances, comparing the
     * lowerKey of each.
     */
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
//        Objects.requireNonNull(o);
        return COMPARATOR.compare(this, o);
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
     * Returns true if the string this {@link CIKey} wraps contains (ignoring case) lowerSubString.
     * {@code lowerSubString} MUST be all lower case.
     * <p>
     * This method is a slight optimisation to avoid having to lower-case the input if it
     * is know to already be lower-case.
     * </p>
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
     * @param keys
     * @return True if this key matches one of keys (ignoring case)
     */
    public boolean in(final Collection<String> keys) {
        if (GwtNullSafe.hasItems(keys)) {
            return keys.stream()
                    .anyMatch(aKey ->
                            CIKey.equalsIgnoreCase(this, aKey));
        } else {
            return false;
        }
    }

    /**
     * @return True if ciKey is null or wraps a null string
     */
    public static boolean isNull(final CIKey ciKey) {
        return ciKey == null || ciKey.key == null;
    }

    public boolean isEmpty(final CIKey ciKey) {
        return ciKey.key == null || ciKey.key.isEmpty();
    }

    public boolean isEmpty() {
        return key == null || key.isEmpty();
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

    public static List<CIKey> listOf(final String... keys) {
        return GwtNullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toList());
    }

    public static Set<CIKey> setOf(final String... keys) {
        return GwtNullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toSet());
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
     * Accepts nulls and never returns a null.
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
    public static boolean equalsIgnoreCase(final String str1, final String str2) {
        if (str1 == null && str2 == null) {
            return true;
        } else {
            return str1 != null && str1.equalsIgnoreCase(str2);
        }
    }

    /**
     * True if str is equal to the string wrapped by ciKey, ignoring case.
     */
    public static boolean equalsIgnoreCase(final CIKey ciKey, final String str) {
        return equalsIgnoreCase(str, ciKey);
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
     * Method so we have a consistent way of doing it, in the unlikely event it changes.
     */
    static String toLowerCase(final String str) {
        return GwtNullSafe.get(str, s -> s.toLowerCase(Locale.ENGLISH));
    }
}
