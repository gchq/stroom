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
import stroom.refdata.MapStoreImpl.RangeStore;
import stroom.refdata.MapStoreImpl.RangeStoreComparator;
import stroom.xml.event.EventList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class MapStoreBuilderImpl implements MapStoreBuilder {
    private final StoredErrorReceiver storedErrorReceiver;

    private Map<MapStoreKey, EventList> keyMap;
    private Map<String, Map<Range<Long>, EventList>> rangeMap;
    private boolean overrideExistingValues;

    public MapStoreBuilderImpl(final StoredErrorReceiver storedErrorReceiver) {
        this.storedErrorReceiver = storedErrorReceiver;
    }

    @Override
    public void setEvents(final String mapName, final String keyName, final EventList eventList,
                          final boolean overrideExistingValues) {
        this.overrideExistingValues = overrideExistingValues;

        final MapStoreKey key = new MapStoreKey(mapName, keyName);
        if (keyMap == null) {
            keyMap = new HashMap<>();
        }
        final EventList existing = keyMap.put(key, eventList);

        // Do we have an existing value in the map for this key?
        if (existing != null && !existing.equals(eventList)) {
            if (!overrideExistingValues) {
                // If we aren't allowing values to be overridden then put the
                // old value back.
                keyMap.put(key, existing);
            }
            throw new IllegalStateException("A value already exists for " + key);
        }
    }

    @Override
    public void setEvents(final String mapName, final Range<Long> range, final EventList eventList,
                          final boolean overrideExistingValues) {
        this.overrideExistingValues = overrideExistingValues;

        if (rangeMap == null) {
            rangeMap = new HashMap<>();
        }

        final Map<Range<Long>, EventList> map = rangeMap.computeIfAbsent(mapName, k -> new HashMap<>());
        final EventList existing = map.put(range, eventList);

        // Do we have an existing value in the map for this range?
        if (existing != null && !existing.equals(eventList)) {
            if (!overrideExistingValues) {
                // If we aren't allowing values to be overridden then put the
                // old value back.
                map.put(range, existing);
            }
            throw new IllegalStateException("A value already exists for " + range);
        }
    }

    @Override
    public MapStore getMapStore() {
        Map<String, RangeStore[]> newRangeMap = null;
        if (rangeMap != null) {
            // Turn the range map into a map of arrays rather than lists and
            // sort the arrays.
            newRangeMap = new HashMap<>();

            final RangeStoreComparator comparator = new RangeStoreComparator();
            for (final Entry<String, Map<Range<Long>, EventList>> entry : rangeMap.entrySet()) {
                // Turn the map into an array of range stores.
                final Map<Range<Long>, EventList> map = entry.getValue();
                final RangeStore[] array = new RangeStore[map.size()];
                int i = 0;
                for (final Entry<Range<Long>, EventList> entry2 : map.entrySet()) {
                    final RangeStore rangeStore = new RangeStore(entry2.getKey(), entry2.getValue());
                    array[i++] = rangeStore;
                }

                // Sort the array by range from.
                Arrays.sort(array, comparator);
                newRangeMap.put(entry.getKey(), array);
            }
        }

        return new MapStoreImpl(keyMap, newRangeMap, overrideExistingValues, storedErrorReceiver);
    }
}
