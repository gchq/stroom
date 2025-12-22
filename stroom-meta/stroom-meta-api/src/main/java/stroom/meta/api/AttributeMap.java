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

package stroom.meta.api;

import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Map that does not care about key case.
 */
public class AttributeMap extends CIStringHashMap {

    // TODO change AttributeMap to not extend, and instead implement Map<CIKey, String> and delegate to
    //  a Map<CIKey, String>.
    //  See changes to AttributeMap, AttributeMapUtil and their tests in the branch
    //  gh-4365-stroomql-case-sens-fields-7.6

    // Delimiter within a value
    static final String VALUE_DELIMITER = ",";
    static final Pattern VALUE_DELIMITER_PATTERN = Pattern.compile(Pattern.quote(VALUE_DELIMITER));

    private final boolean overrideEmbeddedMeta;

    public AttributeMap() {
        this.overrideEmbeddedMeta = false;
    }

    public AttributeMap(final boolean overrideEmbeddedMeta) {
        super();
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
    }

    public AttributeMap(final boolean overrideEmbeddedMeta, final Map<String, String> values) {
        super(values != null
                ? values.size()
                : 16);
        this.overrideEmbeddedMeta = overrideEmbeddedMeta;
        if (values != null) {
            putAll(values);
        }
    }

    /**
     * Create a new {@link AttributeMap} from an existing {@link AttributeMap}, populating
     * the new map with all attributeMap's entries.
     *
     * @param attributeMap Cannot be null.
     */
    public AttributeMap(final AttributeMap attributeMap) {
        super(Objects.requireNonNull(attributeMap));
        this.overrideEmbeddedMeta = attributeMap.overrideEmbeddedMeta;
    }

    public AttributeMap(final Map<String, String> values) {
        this.overrideEmbeddedMeta = false;
        if (values != null) {
            putAll(values);
        }
    }

    private AttributeMap(final Builder builder) {
        overrideEmbeddedMeta = builder.overrideEmbeddedMeta;
        putAll(builder.attributes);
    }

    @Override
    public String put(final String key, final String value) {
        if (key != null && StandardHeaderArguments.DATE_HEADER_KEYS.contains(key)) {
            final String normalisedValue = DateUtil.normaliseDate(value, true);
            return super.put(key, normalisedValue);
        } else {
            return super.put(key, value);
        }
    }

    /**
     * Puts a random UUID using the specified key, but only if the key doesn't
     * already exist.
     *
     * @return The value associated with key, whether existing or computed.
     */
    public String putRandomUuidIfAbsent(final String key) {
        return super.computeIfAbsent(key, k -> UUID.randomUUID().toString());
    }

    /**
     * Puts the current date time into the map in Stroom standard ISO 8601 format,
     * e.g. {@link DateUtil#createNormalDateTimeString()} using the specified key.
     *
     * @return The previous value for the key.
     */
    public String putCurrentDateTime(final String key) {
        // Already normalised, so use super.put not the local one
        return super.put(key, DateUtil.createNormalDateTimeString());
    }

    /**
     * Puts the specified date time (as epoch millis) into the map in Stroom standard ISO 8601 format,
     * e.g. {@link DateUtil#createNormalDateTimeString()} using the specified key.
     *
     * @return The previous value for the key.
     */
    public String putDateTime(final String key, final Long epochMs) {
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
    public String putDateTime(final String key, final Instant instant) {
        if (instant == null) {
            return super.put(key, null);
        } else {
            final String dateStr = DateUtil.createNormalDateTimeString(instant.toEpochMilli());
            // Already normalised, so use super.put not the local one
            return super.put(key, dateStr);
        }
    }

    public Long getAsEpochMillis(final String key) {
        return NullSafe.get(
                get(key),
                DateUtil::parseNormalDateTimeString);
    }

    public Instant getAsInstant(final String key) {
        return NullSafe.get(
                get(key),
                DateUtil::parseNormalDateTimeStringToInstant);
    }

    /**
     * Appends the time to the end of the existing value (delimited by {@link AttributeMap#VALUE_DELIMITER})
     * or sets the value if not present. instant is converted to a normal date time string
     *
     * @return The previous value for the key.
     */
    public String appendDateTime(final String key, final Instant instant) {
        if (instant != null) {
            String val = super.get(key);
            final String dateStr = DateUtil.createNormalDateTimeString(instant.toEpochMilli());
            if (NullSafe.isEmptyString(val)) {
                val = dateStr;
            } else {
                if (!val.endsWith(VALUE_DELIMITER)) {
                    val += VALUE_DELIMITER;
                }
                val += dateStr;
            }
            return super.put(key, val);
        } else {
            return super.get(key);
        }
    }

    /**
     * Appends the time to the end of the existing value (delimited by {@link AttributeMap#VALUE_DELIMITER})
     * or sets the value if not present, but ONLY if item is not already present at the end of the current value.
     * instant is converted to a normal date time string.
     *
     * @return The previous value for the key.
     */
    public String appendDateTimeIfDifferent(final String key, final Instant instant) {
        if (instant != null) {
            final String dateStr = DateUtil.createNormalDateTimeString(instant.toEpochMilli());
            return appendItemIfDifferent(key, dateStr);
        } else {
            return super.get(key);
        }
    }

    /**
     * Appends the time to the end of the existing value (delimited by {@link AttributeMap#VALUE_DELIMITER})
     * or sets the value if not present. instant is converted to a normal date time string
     *
     * @return The previous value for the key.
     */
    public String appendItem(final String key, final String item) {
        if (item != null) {
            String normalisedItem = item;
            if (key != null && StandardHeaderArguments.DATE_HEADER_KEYS.contains(key)) {
                normalisedItem = DateUtil.normaliseDate(item, true);
            }
            String val = super.get(key);
            if (NullSafe.isEmptyString(val)) {
                val = normalisedItem;
            } else {
                if (!val.endsWith(VALUE_DELIMITER)) {
                    val += VALUE_DELIMITER;
                }
                val += normalisedItem;
            }
            return super.put(key, val);
        } else {
            return super.get(key);
        }
    }

    /**
     * Appends the time to the end of the existing value (delimited by {@link AttributeMap#VALUE_DELIMITER})
     * or sets the value if not present, but ONLY if item is not already present at the end of the current value.
     * instant is converted to a normal date time string
     *
     * @return The previous value for the key.
     */
    public String appendItemIfDifferent(final String key, final String item) {
        if (item != null) {
            String normalisedItem = item.trim();
            if (key != null && StandardHeaderArguments.DATE_HEADER_KEYS.contains(key)) {
                normalisedItem = DateUtil.normaliseDate(item, true);
            }
            String val = super.get(key);
            if (NullSafe.isEmptyString(val)) {
                val = normalisedItem;
                return super.put(key, val);
            } else {
                boolean doAppend = false;
                if (val.contains(AttributeMap.VALUE_DELIMITER)) {
                    // Multiple items, check last one
                    if (!val.endsWith(AttributeMap.VALUE_DELIMITER + item)) {
                        doAppend = true;
                    }
                } else {
                    // Single item
                    if (!Objects.equals(val, item)) {
                        doAppend = true;
                    }
                }
                if (doAppend) {
                    if (!val.endsWith(AttributeMap.VALUE_DELIMITER)) {
                        val += AttributeMap.VALUE_DELIMITER;
                    }
                    val += normalisedItem;
                    return super.put(key, val);
                } else {
                    return super.get(key);
                }
            }
        } else {
            return super.get(key);
        }
    }

    /**
     * Appends the item to the end of the existing value (delimited by {@link AttributeMap#VALUE_DELIMITER})
     * or sets the value if not present, but only if currentValuePredicate returns true. If currentValuePredicate
     * returns false, no change is made to the map
     *
     * @param currentValuePredicate {@link Predicate} to test the current value for the key to determine whether
     *                              to append the item or not. The {@link Predicate} accepts the current value or
     *                              null if there is no entry for the key.
     * @return The previous value for the key.
     */
    public String appendItemIf(final String key,
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
                    if (!val.endsWith(VALUE_DELIMITER)) {
                        val += VALUE_DELIMITER;
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

    /**
     * Put an entry where the value is itself a collection of values, e.g. a list of files
     */
    public String putCollection(final String key, final Collection<String> values) {
        final String value;
        if (values == null) {
            value = null;
        } else if (values.isEmpty()) {
            value = "";
        } else {
            value = String.join(VALUE_DELIMITER, values);
        }
        return put(key, value);
    }

    /**
     * Get the value for a given key as a {@link List}, e.g. where the value is known to be a
     * delimited collection of items. If the value only contains one item, then a singleton
     * {@link List} is returned.
     */
    public List<String> getAsList(final String key) {
        final String val = get(key);
        if (NullSafe.isEmptyString(val)) {
            return Collections.emptyList();
        } else {
            return VALUE_DELIMITER_PATTERN.splitAsStream(val)
                    .toList();
        }
    }

    public boolean isDelimited(final String key) {
        final String val = get(key);
        return NullSafe.test(val, val2 ->
                val2.contains(VALUE_DELIMITER));
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

    public void removeAll(final Collection<String> keySet) {
        if (keySet != null) {
            for (final String key : keySet) {
                remove(key);
            }
        }
    }

    public boolean isOverrideEmbeddedMeta() {
        return overrideEmbeddedMeta;
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

        public Builder put(final String key, final String value) {
            Objects.requireNonNull(key);
            attributes.put(key, value);
            return this;
        }

        public Builder putDateTime(final String key, final Instant value) {
            Objects.requireNonNull(key);
            attributes.putDateTime(key, value);
            return this;
        }

        public Builder putDateTime(final String key, final Long value) {
            Objects.requireNonNull(key);
            attributes.putDateTime(key, value);
            return this;
        }

        public Builder putCollection(final String key, final Collection<String> values) {
            Objects.requireNonNull(key);
            attributes.putCollection(key, values);
            return this;
        }

        public AttributeMap build() {
            return new AttributeMap(this);
        }
    }
}
