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

package stroom.pipeline.refdata;

import stroom.util.date.DateUtil;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class LookupIdentifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(LookupIdentifier.class);

    static final String NEST_SEPARATOR = "/";

    private final String map; // e.g. "MAP1/MAP2/MAP3"
    private final String key; // e.g. 'jbloggs'
    private final long eventTime;
    private final String primaryMapName; // e.g. "MAP1"
    private final String secondaryMapName; // e.g. "MAP2/MAP3"

    /**
     * Class to describe an identifier to be looked up in the temporal reference data store.
     * Supports chained lookups where 'map' describes a series of map names to use in a
     * recursive fashion, e.g. for ("map1/map2", "key1", 123456)
     * Lookup in map1: "key1" => "key2"
     * Lookup in map2: "key2" => "someValue"
     *
     * @param map       The name of the map, or a delimited string listing a sequence of map names to
     *                  perform a chained lookup, e.g. "myMap" or "map1/map2/map3"
     * @param key       The key to lookup in the map or primaryMap
     * @param eventTime The time the lookup is effective at (epochMs)
     */
    public LookupIdentifier(final String map, final String key, final long eventTime) {

        this.map = Objects.requireNonNull(map);
        this.key = Objects.requireNonNull(key);
        this.eventTime = eventTime;

        // the following are all derived properties of the object
        final int splitPos = map.indexOf(NEST_SEPARATOR);

        // compute the nested parts if we have nesting
        if (splitPos != -1) {
            this.primaryMapName = map.substring(0, splitPos);
            this.secondaryMapName = map.substring(splitPos + NEST_SEPARATOR.length());
            if (primaryMapName.isEmpty() || secondaryMapName.isEmpty()) {
                throw new RuntimeException(LogUtil.message("map [{}] is badly formatted, unable to split", map));
            }
        } else {
            this.primaryMapName = map;
            this.secondaryMapName = null;
        }
    }

    public static LookupIdentifier of(final String map, final String key, final long eventTime) {
        return new LookupIdentifier(map, key, eventTime);
    }

    public static LookupIdentifier of(final String map, final String key, final String eventTimeStr) {
        final long eventTimeMs = DateUtil.parseNormalDateTimeString(eventTimeStr);
        return new LookupIdentifier(map, key, eventTimeMs);
    }

    public void appendTo(final StringBuilder sb) {
        sb.append("(map: '");
        sb.append(primaryMapName);
        sb.append("', key: '");
        sb.append(key);
        sb.append("', lookup time: ");
        sb.append(DateUtil.createNormalDateTimeString(eventTime));
        sb.append(")");
    }

    public boolean isMapNested() {
        return secondaryMapName != null;
    }

    /**
     * @return The map being looked up against. This may be a nested map, e.g. MAP1/MAP2/MAP3.
     * Use {@link LookupIdentifier#getPrimaryMapName()} for the initial map to look in.
     */
    public String getMap() {
        return map;
    }

    public String getKey() {
        return key;
    }

    public long getEventTime() {
        return eventTime;
    }

    /**
     * @return The map to perform the lookup in, or the initial map to look in when map is a nested map.
     */
    public String getPrimaryMapName() {
        return primaryMapName;
    }

    /**
     * @return The secondary map name which may itself be a nested map, e.g. "MAP2/MAP3"
     */
    public String getSecondaryMapName() {
        return secondaryMapName;
    }

    public LookupIdentifier getNestedLookupIdentifier(final String newKey) {
        if (!isMapNested()) {
            throw new RuntimeException(LogUtil.message("Identifier {} is not nested", this));
        }
        return new LookupIdentifier(secondaryMapName, newKey, eventTime);
    }

    public LookupIdentifier cloneWithNewKey(final String newKey) {
        return new LookupIdentifier(map, newKey, eventTime);
    }

    @Override
    public String toString() {
        return "LookupIdentifier{" +
                "key='" + key + '\'' +
                ", eventTime=" + eventTime +
                ", primaryMapName='" + primaryMapName + '\'' +
                ", secondaryMapName='" + secondaryMapName + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LookupIdentifier that = (LookupIdentifier) o;
        return eventTime == that.eventTime &&
                Objects.equals(map, that.map) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map, key, eventTime);
    }
}
