/*
 * Copyright 2016 Crown Copyright
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

package stroom.refdata;

import stroom.entity.shared.Range;
import stroom.pipeline.server.errorhandler.StoredErrorReceiver;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.xml.event.EventList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;

public class MapStoreImpl implements MapStore {
    private static final String EQUALS = " = ";
    private static final String NEW_LINE = "\n";
    private final Map<MapStoreKey, EventList> keyMap;
    private final Map<String, RangeStore[]> rangeMap;
    private final boolean overrideExistingValues;
    private final StoredErrorReceiver storedErrorReceiver;
    public MapStoreImpl() {
        this(null, null, true, null);
    }
    public MapStoreImpl(final Map<MapStoreKey, EventList> keyMap, final Map<String, RangeStore[]> rangeMap,
                        final boolean overrideExistingValues, final StoredErrorReceiver storedErrorReceiver) {
        this.keyMap = keyMap;
        this.rangeMap = rangeMap;
        this.overrideExistingValues = overrideExistingValues;
        this.storedErrorReceiver = storedErrorReceiver;
    }

    @Override
    public EventList getEvents(final String mapName, final String keyName) {
        EventList eventList = null;

        // Try and find an exact match in the key map.
        if (keyMap != null) {
            final MapStoreKey key = new MapStoreKey(mapName, keyName);
            eventList = keyMap.get(key);
        }

        // If we didn't find a key match then take a look in the range map.
        if (eventList == null && rangeMap != null) {
            try {
                final RangeStore[] rangeStores = rangeMap.get(mapName);
                if (rangeStores != null) {
                    final long key = Long.parseLong(keyName);
                    final RangeStore keyRange = new RangeStore(new Range<>(key, key), null);
                    final RangeStoreComparator comparator = new RangeStoreComparator();
                    int maxIndex = Arrays.binarySearch(rangeStores, keyRange, comparator);

                    // If we didn't find an exact match for the 'from' value
                    // then we will get a negative position which is
                    // (-(insertionPoint) - 1). Invert this position and take
                    // away 2 to get the first range that had a 'from' less than
                    // the key.
                    if (maxIndex < 0) {
                        maxIndex = (maxIndex * -1) - 2;
                    }

                    if (maxIndex >= 0 && maxIndex < rangeStores.length) {
                        // All range stores that are in the array at a position
                        // less than or equal to the found position will have a
                        // range from that is less than or equal to the value we
                        // are looking for.
                        long currentDiff = Long.MAX_VALUE;
                        for (int i = 0; i <= maxIndex; i++) {
                            final RangeStore rangeStore = rangeStores[i];
                            final Range<Long> range = rangeStore.getRange();

                            // See if this range includes the key. The key
                            // should always be greater than or equal to the
                            // 'from' here.
                            if (key >= range.getFrom() && key <= range.getTo()) {
                                final long diff = range.getTo() - range.getFrom();
                                if (diff < currentDiff) {
                                    currentDiff = diff;
                                    eventList = rangeStore.getEventList();

                                } else if (diff == currentDiff && overrideExistingValues) {
                                    // We have found matching range that is just
                                    // as tight a match as one we found already.
                                    // If we are overriding existing values then
                                    // use this new value.

                                    // This is a bit of a fudge but I thought it
                                    // better to reuse this flag to choose
                                    // whether to keep the lower 'from' or the
                                    // new higher 'from' range.
                                    eventList = rangeStore.getEventList();
                                }
                            }
                        }
                    }
                }
            } catch (final Throwable t) {
                // Ignore.
            }
        }

        return eventList;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(keyMap);
        builder.append(rangeMap);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof MapStoreImpl)) {
            return false;
        }

        final MapStoreImpl mapStore = (MapStoreImpl) obj;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(keyMap, mapStore.keyMap);
        builder.append(rangeMap, mapStore.rangeMap);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        if (keyMap != null) {
            for (final Entry<MapStoreKey, EventList> entry : keyMap.entrySet()) {
                sb.append(entry.getKey().toString());
                sb.append(EQUALS);
                sb.append(entry.getValue());
                sb.append(NEW_LINE);
            }
        }
        if (rangeMap != null) {
            for (final Entry<String, RangeStore[]> entry : rangeMap.entrySet()) {
                for (final RangeStore rangeStore : entry.getValue()) {
                    sb.append(entry.getKey());
                    sb.append(":");
                    sb.append(rangeStore.getRange());
                    sb.append(EQUALS);
                    sb.append(rangeStore.getEventList());
                    sb.append(NEW_LINE);
                }
            }
        }

        return sb.toString();
    }

    @Override
    public StoredErrorReceiver getErrorReceiver() {
        return storedErrorReceiver;
    }

    public static class RangeStore {
        private final Range<Long> range;
        private final EventList eventList;

        public RangeStore(final Range<Long> range, final EventList eventList) {
            this.range = range;
            this.eventList = eventList;
        }

        public Range<Long> getRange() {
            return range;
        }

        public EventList getEventList() {
            return eventList;
        }

        @Override
        public String toString() {
            return range.toString();
        }
    }

    public static class RangeStoreComparator implements Comparator<RangeStore> {
        @Override
        public int compare(final RangeStore o1, final RangeStore o2) {
            return o1.getRange().getFrom().compareTo(o2.getRange().getFrom());
        }
    }
}
