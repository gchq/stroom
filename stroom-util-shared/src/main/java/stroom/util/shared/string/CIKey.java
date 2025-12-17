/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * original string at the cost of wrapping it in another object. Also
 * useful for case insensitive comparisons of common strings.
 * </p>
 * <p>
 * See {@link CIKeys} for common {@link CIKey} instances.
 * </p>
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class CIKey implements Comparable<CIKey> {

    //    public static final CIKey NULL_STRING = new CIKey(null);
    public static final CIKey EMPTY_STRING = new CIKey("");

    // Compare on the lower case form of the key. CIKey may be null and lowerKey may be null
    public static final Comparator<CIKey> COMPARATOR = Comparator.nullsFirst(Comparator.comparing(
            CIKey::getAsLowerCase,
            Comparator.nullsFirst(String::compareTo)));

    @JsonProperty
    // RestyGWT doesn't understand @JsonValue, so we can't (de)serialise to/from a simple string
    // like we do for StroomDuration, what a pain :-(
    //@JsonValue // No need to serialise the CIKey wrapper, just the key
    private final String key;

    @JsonIgnore
    private final transient String lowerKey;

    @JsonIgnore
    private transient int hash = 0;
    @JsonIgnore
    private transient boolean hashIsZero = false;

    @JsonCreator
    private CIKey(@JsonProperty("key") final String key) {
        this.key = Objects.requireNonNull(key);
        this.lowerKey = toLowerCase(key);
    }

    /**
     * key and lowerKey must be equal ignoring case.
     *
     * @param key      The key
     * @param lowerKey The key converted to lower-case
     */
    CIKey(final String key, final String lowerKey) {
        this.key = Objects.requireNonNull(key);
        this.lowerKey = Objects.requireNonNull(lowerKey);
    }

    /**
     * Create a {@link CIKey} for an unknown, upper or mixed case key, e.g. "FOO", or "Foo".
     * If key is known to definitely be all lower case then use {@link CIKey#ofLowerCase(String)} for
     * a slight performance gain.
     * If key is a common key this method will return an existing {@link CIKey} instance
     * else it will create a new instance.
     * If the key is likely to not be a common key then use {@link CIKey#ofDynamicKey(String)} to
     * save the map lookup.
     * <p>
     * The returned {@link CIKey} will wrap key with no change of case and no trimming.
     * </p>
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey of(final String key) {
        if (key == null) {
            return null;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            CIKey ciKey = CIKeys.getCommonKey(key);
            if (ciKey == null) {
                // Minor optimisation to save on the cost of doing toLowerCase() if we don't have to
                if (containsUpperCaseChar(key)) {
                    ciKey = new CIKey(key);
                } else {
                    // All lower
                    ciKey = new CIKey(key, key);
                }
            }
            return ciKey;
        }
    }

    /**
     * Equivalent to calling {@link CIKey#of(String)} with a trimmed key.
     */
    public static CIKey trimmed(final String key) {
        if (key == null) {
            return EMPTY_STRING;
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
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey of(final String key, final String lowerKey) {
        if ((key == null && lowerKey != null)
            || (key != null && lowerKey == null)) {
            throw new IllegalArgumentException("One is null, other is not - '" + key + "', '" + lowerKey + "'");
        } else if (key == null) {
            return null;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return NullSafe.requireNonNullElseGet(
                    CIKeys.getCommonKey(key),
                    () -> {
                        checkNoUpperCaseChars(lowerKey);
                        if (key.equalsIgnoreCase(lowerKey)) {
                            return new CIKey(key, lowerKey);
                        } else {
                            throw new IllegalArgumentException(
                                    "Not the same key (ignoring case)- '" + key + "', '" + lowerKey + "'");
                        }
                    });
        }
    }

    /**
     * Create a {@link CIKey} for key, providing a map of known {@link CIKey}s keyed
     * on their key value.
     * Will fall back on {@link CIKeys#getCommonKey(String)} ()} if not found in knownKeys.
     * Allows callers to hold their own local set of known {@link CIKey}s to save
     * re-creating them each time.
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey of(final String key, final Map<String, CIKey> knownKeys) {
        if (key == null) {
            return null;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            CIKey ciKey = null;
            if (knownKeys != null) {
                ciKey = knownKeys.get(key);
            }
            if (ciKey == null) {
                ciKey = CIKeys.getCommonKey(key);
                if (ciKey == null) {
                    // Minor optimisation to save on the cost of doing toLowerCase() if we don't have to
                    if (containsUpperCaseChar(key)) {
                        ciKey = new CIKey(key);
                    } else {
                        // All lower
                        ciKey = new CIKey(key, key);
                    }
                }
            }
            return ciKey;
        }
    }

    /**
     * Create a {@link CIKey} for an all lower case key, e.g. "foo".
     * This is a minor optimisation to avoid a call to toLowerCase as the
     * key is already in lower-case.
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey ofLowerCase(final String lowerKey) {
        if (lowerKey == null) {
            return null;
        } else if (lowerKey.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            // If an upper/mixed case string is passed in then we may return a common
            // CIKey for it (if known). Even though the arg is not lower case, the CiKey
            // would be correct. This saves the case check.
            CIKey ciKey = CIKeys.getCommonKey(lowerKey);
            if (ciKey == null) {
                // Make sure lowerKey is actually lowercase
                checkNoUpperCaseChars(lowerKey);
                ciKey = new CIKey(lowerKey, lowerKey);
            }
            return ciKey;
        }
    }

    /**
     * Will throw if any char is upper-case.
     */
    private static void checkNoUpperCaseChars(final String str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                throw new IllegalArgumentException("str '" + str + "' is not all lowercase");
            }
        }
    }

    private static boolean containsUpperCaseChar(final String str) {
        final int len = str.length();
        for (int i = 0; i < len; i++) {
            if (Character.isUpperCase(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a {@link CIKey} for a key that is known NOT to be in {@link CIKey}s map
     * of common keys and is a key that will not be added to the map of common keys in future.
     * This is a minor optimisation that saves a map lookup if the key is known
     * to probably not be in the map.
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey ofDynamicKey(final String dynamicKey) {
        if (dynamicKey == null) {
            return null;
        } else if (dynamicKey.isEmpty()) {
            return EMPTY_STRING;
        } else {
            return containsUpperCaseChar(dynamicKey)
                    ? new CIKey(dynamicKey)
                    // Upper or mixed
                    : new CIKey(dynamicKey, dynamicKey); // All lower
        }
    }

    /**
     * Create a {@link CIKey} that will be held as a static variable.
     * Only use this for commonly used static {@link CIKey} instances
     * as if the key is not already held in the map of common {@link CIKey}s
     * then it will be added.
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey internStaticKey(final String key) {
        if (key == null) {
            return null;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            return CIKeys.internCommonKey(key);
        }
    }

    /**
     * If ciKey matches a common {@link CIKey} (ignoring case) then return the common
     * {@link CIKey} else return ciKey. Use this if you don't care about the case of the
     * wrapped string, e.g. if key is 'FOO', you could get back a {@link CIKey} that wraps
     * 'foo', 'FOO', 'Foo', etc.
     * <p>
     * Is null safe. If key is null, returns null.
     * </p>
     */
    public static CIKey ofIgnoringCase(final String key) {
        if (key == null) {
            return null;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // This assumes that doing the optimistic hashmap lookups is
            // faster than lower-casing the key.
            // First assume it matches the case exactly
            CIKey ciKey = CIKeys.getCommonKey(key);
            if (ciKey == null) {
                // If the first char is lower case then there is a good chance key is
                // all lower case
                final char firstChar = key.charAt(0);
                if (Character.isLowerCase(firstChar)) {
                    // Now assume it is ALL lower-case
                    ciKey = CIKeys.getCommonKeyByLowerCase(key);
                }
                if (ciKey == null) {
                    final String lowerKey = toLowerCase(key);
                    ciKey = CIKeys.getCommonKeyByLowerCase(lowerKey);
                    if (ciKey == null) {
                        CIKey.ofLowerCase(lowerKey);
                    }
                }
            }
            return ciKey;
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

    /**
     * @return The lowercase form of the wrapped string.
     */
    @JsonIgnore
    public String getAsLowerCase() {
        return lowerKey;
    }

    /**
     * @return True if str is equal to the string wrapped in this {@link CIKey}, ignoring case.
     */
    public boolean equalsIgnoreCase(final String str) {
        return CIKey.equalsIgnoreCase(str, this);
    }

    /**
     * Standard equals method for comparing two {@link CIKey} instances, comparing the
     * lowerKey of each, i.e. a case-insensitive match.
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
        // Lazy hashCode caching as this is used as a map key.
        // Borrows pattern from String.hashCode()
        int h = hash;
        if (h == 0 && !hashIsZero) {
            // Hash on lower key only so we get a case in-sensitive match
            h = lowerKey.hashCode();
            if (h == 0) {
                hashIsZero = true;
            } else {
                hash = h;
            }
        }
        return h;
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public int compareTo(final CIKey o) {
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
        if (NullSafe.hasItems(keys)) {
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

    public static boolean isEmpty(final CIKey ciKey) {
        return ciKey.key == null || ciKey.key.isEmpty();
    }

    public static boolean isBlank(final CIKey ciKey) {
        return ciKey.key == null || ciKey.key.isBlank();
    }

    public static boolean isNonBlank(final CIKey ciKey) {
        return ciKey.key != null && !ciKey.key.isBlank();
    }

    @JsonIgnore
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
        return NullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toList());
    }

    @JsonIgnore
    public static Set<CIKey> setOf(final String... keys) {
        return NullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toSet());
    }

    @JsonIgnore
    public static Set<CIKey> setOf(final Set<String> keys) {
        return NullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toSet());
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1) {
        return Map.of(CIKey.of(k1), v1);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1, final String k2, final V v2) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1,
                                          final String k2, final V v2,
                                          final String k3, final V v3) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1,
                                          final String k2, final V v2,
                                          final String k3, final V v3,
                                          final String k4, final V v4) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1,
                                          final String k2, final V v2,
                                          final String k3, final V v3,
                                          final String k4, final V v4,
                                          final String k5, final V v5) {
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
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1,
                                          final String k2, final V v2,
                                          final String k3, final V v3,
                                          final String k4, final V v4,
                                          final String k5, final V v5,
                                          final String k6, final V v6) {
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
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1,
                                          final String k2, final V v2,
                                          final String k3, final V v3,
                                          final String k4, final V v4,
                                          final String k5, final V v5,
                                          final String k6, final V v6,
                                          final String k7, final V v7) {
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
    public static <V> Map<CIKey, V> mapOf(final String k1, final V v1,
                                          final String k2, final V v2,
                                          final String k3, final V v3,
                                          final String k4, final V v4,
                                          final String k5, final V v5,
                                          final String k6, final V v6,
                                          final String k7, final V v7,
                                          final String k8, final V v8) {
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
        return NullSafe.stream(entries)
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue));
    }

    /**
     * Convert a {@link String} keyed map into a {@link CIKey} keyed map.
     * Accepts null keys, but not null values, and never returns a null.
     */
    public static <V> Map<CIKey, V> mapOf(final Map<String, ? extends V> map) {
        return NullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue));
    }

    public static <V> Map<String, V> convertToStringMap(final Map<CIKey, ? extends V> map) {
        return NullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().get(),
                        Entry::getValue));
    }

    public static <V> Map<String, V> convertToLowerCaseStringMap(final Map<CIKey, ? extends V> map) {
        return NullSafe.map(map)
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
        final String lowerKey = NullSafe.get(ciKey, CIKey::getAsLowerCase);
        if (lowerKey == null && str == null) {
            return true;
        } else {
            return lowerKey != null && lowerKey.equalsIgnoreCase(str);
        }
    }

//    /**
//     * If ciKey is null or wraps a null String, throw a {@link NullPointerException},
//     * else return ciKey.
//     */
//    public static CIKey requireNonNullString(final CIKey ciKey) {
//        if (ciKey == null) {
//            throw new NullPointerException("ciKey is null");
//        } else if (ciKey.key == null) {
//            throw new NullPointerException("ciKey wraps a null");
//        } else {
//            return ciKey;
//        }
//    }
//
//    /**
//     * If ciKey is non-null and does not wrap a null string return it, else
//     * return other, which must also be non-null and not wrap a null string.
//     */
//    public static CIKey requireNonNullStringElse(final CIKey ciKey, CIKey other) {
//        if (CIKey.isNull(ciKey)) {
//            return CIKey.requireNonNullString(other);
//        } else {
//            return ciKey;
//        }
//    }
//
//    /**
//     * If ciKey is non-null and does not wrap a null string return it, else
//     * return the value supplied by supplier, which must also be non-null and not wrap a null string.
//     */
//    public static CIKey requireNonNullStringElseGet(final CIKey ciKey, Supplier<CIKey> supplier) {
//        if (CIKey.isNull(ciKey)) {
//            final CIKey other = Objects.requireNonNull(supplier, "supplier").get();
//            return CIKey.requireNonNullString(other);
//        } else {
//            return ciKey;
//        }
//    }

    /**
     * Method so we have a consistent way of doing it, in the unlikely event it changes.
     */
    static String toLowerCase(final String str) {
        return str != null
                ? str.toLowerCase(Locale.ENGLISH)
                : null;
    }
}
