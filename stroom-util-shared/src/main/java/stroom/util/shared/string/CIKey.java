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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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

    // Hold some common keys, so we can just re-use instances rather than creating new each time
    // Some of these map values will get used statically in code, but a lot will come from dash/query
    // fields/columns/tokens which are not known at compile time so the map lets us save on
    // object creation for common ones.
    // The cost of a hashmap get is less than the combined cost of CIKey object creation and
    // the toLowerCase call.
    private static final Map<String, CIKey> COMMON_KEYS = new HashMap<>();

    public static final CIKey NULL_STRING = new CIKey(null);
    public static final CIKey EMPTY_STRING = new CIKey("");

    // Upper camel case keys
    public static final CIKey EFFECTIVE_TIME = CIKey.commonKey("EffectiveTime");
    public static final CIKey DURATION = CIKey.commonKey("Duration");
    public static final CIKey END = CIKey.commonKey("End");
    public static final CIKey EVENT_ID = CIKey.commonKey("EventId");
    public static final CIKey EVENT_TIME = CIKey.commonKey("EventTime");
    public static final CIKey FEED = CIKey.commonKey("Feed");
    public static final CIKey ID = CIKey.commonKey("Id");
    public static final CIKey INDEX = CIKey.commonKey("Index");
    public static final CIKey INSERT_TIME = CIKey.commonKey("InsertTime");
    public static final CIKey KEY = CIKey.commonKey("Key");
    public static final CIKey KEY_END = CIKey.commonKey("KeyEnd");
    public static final CIKey KEY_START = CIKey.commonKey("KeyStart");
    public static final CIKey NAME = CIKey.commonKey("Name");
    public static final CIKey NODE = CIKey.commonKey("Node");
    public static final CIKey PARTITION = CIKey.commonKey("Partition");
    public static final CIKey PIPELINE = CIKey.commonKey("Pipeline");
    public static final CIKey START = CIKey.commonKey("Start");
    public static final CIKey STATUS = CIKey.commonKey("Status");
    public static final CIKey STREAM_ID = CIKey.commonKey("StreamId");
    public static final CIKey SUBJECT = CIKey.commonKey("Subject");
    public static final CIKey TERMINAL = CIKey.commonKey("Terminal");
    public static final CIKey TIME = CIKey.commonKey("Time");
    public static final CIKey TITLE = CIKey.commonKey("Title");
    public static final CIKey TYPE = CIKey.commonKey("Type");
    public static final CIKey UUID = CIKey.commonKey("UUID");
    public static final CIKey VALUE = CIKey.commonKey("Value");
    public static final CIKey VALUE_TYPE = CIKey.commonKey("ValueType");

    // Upper sentence case keys
    public static final CIKey ANALYTIC__RULE = CIKey.commonKey("Analytic Rule");
    public static final CIKey CREATE__TIME = CIKey.commonKey("Create Time");
    public static final CIKey CREATE__TIME__MS = CIKey.commonKey("Create Time Ms");
    public static final CIKey DOC__COUNT = CIKey.commonKey("Doc Count");
    public static final CIKey EFFECTIVE__TIME = CIKey.commonKey("Effective Time");
    public static final CIKey END__TIME = CIKey.commonKey("End Time");
    public static final CIKey END__TIME__MS = CIKey.commonKey("End Time Ms");
    public static final CIKey ERROR__COUNT = CIKey.commonKey("Error Count");
    public static final CIKey FATAL__ERROR__COUNT = CIKey.commonKey("Fatal Error Count");
    public static final CIKey FILE__SIZE = CIKey.commonKey("File Size");
    public static final CIKey INDEX__NAME = CIKey.commonKey("Index Name");
    public static final CIKey INFO__COUNT = CIKey.commonKey("Info Count");
    public static final CIKey LAST__COMMIT = CIKey.commonKey("Last Commit");
    public static final CIKey META__ID = CIKey.commonKey("Meta Id");
    public static final CIKey PARENT__CREATE__TIME = CIKey.commonKey("Parent Create Time");
    public static final CIKey PARENT__FEED = CIKey.commonKey("Parent Feed");
    public static final CIKey PARENT__ID = CIKey.commonKey("Parent Id");
    public static final CIKey PARENT__STATUS = CIKey.commonKey("Parent Status");
    public static final CIKey PIPELINE__NAME = CIKey.commonKey("Pipeline Name");
    public static final CIKey PROCESSOR__DELETED = CIKey.commonKey("Processor Deleted");
    public static final CIKey PROCESSOR__ENABLED = CIKey.commonKey("Processor Enabled");
    public static final CIKey PROCESSOR__FILTER__DELETED = CIKey.commonKey("Processor Filter Deleted");
    public static final CIKey PROCESSOR__FILTER__ENABLED = CIKey.commonKey("Processor Filter Enabled");
    public static final CIKey PROCESSOR__FILTER__ID = CIKey.commonKey("Processor Filter Id");
    public static final CIKey PROCESSOR__FILTER__LAST__POLL__MS = CIKey.commonKey("Processor Filter Last Poll Ms");
    public static final CIKey PROCESSOR__FILTER__PRIORITY = CIKey.commonKey("Processor Filter Priority");
    public static final CIKey PROCESSOR__FILTER__UUID = CIKey.commonKey("Processor Filter UUID");
    public static final CIKey PROCESSOR__ID = CIKey.commonKey("Processor Id");
    public static final CIKey PROCESSOR__PIPELINE = CIKey.commonKey("Processor Pipeline");
    public static final CIKey PROCESSOR__TASK__ID = CIKey.commonKey("Processor Task Id");
    public static final CIKey PROCESSOR__TYPE = CIKey.commonKey("Processor Type");
    public static final CIKey PROCESSOR__UUID = CIKey.commonKey("Processor UUID");
    public static final CIKey RAW__SIZE = CIKey.commonKey("Raw Size");
    public static final CIKey READ__COUNT = CIKey.commonKey("Read Count");
    public static final CIKey START__TIME = CIKey.commonKey("Start Time");
    public static final CIKey START__TIME__MS = CIKey.commonKey("Start Time Ms");
    public static final CIKey STATUS__TIME = CIKey.commonKey("Status Time");
    public static final CIKey STATUS__TIME__MS = CIKey.commonKey("Status Time Ms");
    public static final CIKey TASK__ID = CIKey.commonKey("Task Id");
    public static final CIKey VOLUME__GROUP = CIKey.commonKey("Volume Group");
    public static final CIKey VOLUME__PATH = CIKey.commonKey("Volume Path");
    public static final CIKey WARNING__COUNT = CIKey.commonKey("Warning Count");
    public static final CIKey WRITE__COUNT = CIKey.commonKey("Write Count");

    // Reference Data fields
    public static final CIKey FEED__NAME = CIKey.commonKey("Feed Name");
    public static final CIKey VALUE__REFERENCE__COUNT = CIKey.commonKey("Value Reference Count");
    public static final CIKey MAP__NAME = CIKey.commonKey("Map Name");
    public static final CIKey LAST__ACCESSED__TIME = CIKey.commonKey("Last Accessed Time");
    public static final CIKey REFERENCE__LOADER__PIPELINE = CIKey.commonKey("Reference Loader Pipeline");
    public static final CIKey PROCESSING__STATE = CIKey.commonKey("Processing State");
    public static final CIKey STREAM__ID = CIKey.commonKey("Stream ID");
    public static final CIKey PART__NUMBER = CIKey.commonKey("Part Number");
    public static final CIKey PIPELINE__VERSION = CIKey.commonKey("Pipeline Version");

    // Annotations keys
    public static final CIKey ANNO_ASSIGNED_TO = CIKey.commonKey("annotation:AssignedTo");
    public static final CIKey ANNO_COMMENT = CIKey.commonKey("annotation:Comment");
    public static final CIKey ANNO_CREATED_BY = CIKey.commonKey("annotation:CreatedBy");
    public static final CIKey ANNO_CREATED_ON = CIKey.commonKey("annotation:CreatedOn");
    public static final CIKey ANNO_HISTORY = CIKey.commonKey("annotation:History");
    public static final CIKey ANNO_ID = CIKey.commonKey("annotation:Id");
    public static final CIKey ANNO_STATUS = CIKey.commonKey("annotation:Status");
    public static final CIKey ANNO_SUBJECT = CIKey.commonKey("annotation:Subject");
    public static final CIKey ANNO_TITLE = CIKey.commonKey("annotation:Title");
    public static final CIKey ANNO_UPDATED_BY = CIKey.commonKey("annotation:UpdatedBy");
    public static final CIKey ANNO_UPDATED_ON = CIKey.commonKey("annotation:UpdatedOn");

    public static final CIKey UNDERSCORE_EVENT_ID = CIKey.commonKey("__event_id__");
    public static final CIKey UNDERSCORE_STREAM_ID = CIKey.commonKey("__stream_id__");
    public static final CIKey UNDERSCORE_TIME = CIKey.commonKey("__time__");

    @JsonValue // No need to serialise the CIKey wrapper, just the key
    private final String key;

    @JsonIgnore
    private final transient String lowerKey;

    @JsonCreator
    private CIKey(final String key) {
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

    /**
     * Only intended for use on static {@link CIKey} instances due to the cost of
     * interning
     */
    private static CIKey commonKey(final String key) {
        final CIKey ciKey;
        if (key == null) {
            ciKey = NULL_STRING;
        } else {
            // Ensure we are using string pool instances for both
            final String k = key.intern();
            ciKey = new CIKey(k, toLowerCase(k).intern());
        }
        // Add it to our static map so CIKey.of() can look it up
        COMMON_KEYS.put(key, ciKey);
        return ciKey;
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
                    COMMON_KEYS.get(key),
                    () -> new CIKey(key, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo".
     * If key is all lower case then user {@link CIKey#ofLowerCase(String)}.
     * If key is a common key this method will return an existing {@link CIKey} instance
     * else it will create a new instance.
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
                ciKey = COMMON_KEYS.get(key);
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
                    COMMON_KEYS.get(lowerKey),
                    () -> new CIKey(lowerKey, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for a key that is known NOT to be in {@link CIKey}s list
     * of common keys.
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
     * interning the lower-case form of the key. Don't use this for dynamic keys.
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
                    COMMON_KEYS.get(key),
                    () -> CIKey.commonKey(key));
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
     * @return True if ciKey is null or wraps a null string
     */
    public static boolean isNull(final CIKey ciKey) {
        return ciKey == null || NULL_STRING.equals(ciKey);
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
}
