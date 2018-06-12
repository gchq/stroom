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
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.refdata.MapStoreImpl.RangeStore;
import stroom.refdata.MapStoreImpl.RangeStoreComparator;
import stroom.refdata.saxevents.EventListValue;
import stroom.refdata.saxevents.ValueProxy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Deprecated
public class MapStoreBuilderImpl implements MapStoreBuilder {
    private final StoredErrorReceiver storedErrorReceiver;

    private Map<MapStoreKey, ValueProxy<EventListValue>> keyMap;
    private Map<String, Map<Range<Long>, ValueProxy<EventListValue>>> rangeMap;
    private boolean overrideExistingValues;

    public MapStoreBuilderImpl(final StoredErrorReceiver storedErrorReceiver) {
        this.storedErrorReceiver = storedErrorReceiver;
    }

    @Override
    public void setEvents(final String mapName, final String keyName, final ValueProxy<EventListValue> eventListProxy,
                          final boolean overrideExistingValues) {
        this.overrideExistingValues = overrideExistingValues;

        final MapStoreKey key = new MapStoreKey(mapName, keyName);
        if (keyMap == null) {
            keyMap = new HashMap<>();
        }
        final ValueProxy<EventListValue> existing = keyMap.put(key, eventListProxy);

        // Do we have an existing value in the map for this key?
        if (existing == null) {
            // no previous entry for this key
            eventListProxy.inrementReferenceCount();
        } else {
            // replaced previous entry for this key
            if (existing.equals(eventListProxy)) {
                // replaced with an equal object
                if (existing != eventListProxy) {
                    // the two objects are equal but not the same instance so adjust ref counts
                    eventListProxy.inrementReferenceCount();
                    existing.decrementReferenceCount();
                }
            } else {
                // replaced with an different object
                if (overrideExistingValues) {
                    //successfully added the new proxy to the map so increment its reference count.
                    eventListProxy.inrementReferenceCount();
                } else {
                    // If we aren't allowing values to be overridden then put the old value back.
                    // No need to change ref counts as nothing has changed.
                    keyMap.put(key, existing);
                }
                //TODO why throw this after doing the put above?
                throw new IllegalStateException("A value already exists for " + key);
            }
        }
    }

    @Override
    public void setEvents(final String mapName, final Range<Long> range, final ValueProxy<EventListValue> eventListProxy,
                          final boolean overrideExistingValues) {
        this.overrideExistingValues = overrideExistingValues;

        if (rangeMap == null) {
            rangeMap = new HashMap<>();
        }

        final Map<Range<Long>, ValueProxy<EventListValue>> map = rangeMap.computeIfAbsent(mapName, k -> new HashMap<>());
        final ValueProxy<EventListValue> existing = map.put(range, eventListProxy);

        // Do we have an existing value in the map for this key?
        if (existing == null) {
            // no previous entry for this key
            eventListProxy.inrementReferenceCount();
        } else {
            // replaced previous entry for this key
            if (existing.equals(eventListProxy)) {
                // replaced with an equal object
                if (existing != eventListProxy) {
                    // the two objects are equal but not the same instance so adjust ref counts
                    eventListProxy.inrementReferenceCount();
                    existing.decrementReferenceCount();
                }
            } else {
                // replaced with an different object
                if (overrideExistingValues) {
                    //successfully added the new proxy to the map so increment its reference count.
                    eventListProxy.inrementReferenceCount();
                } else {
                    // If we aren't allowing values to be overridden then put the old value back.
                    // No need to change ref counts as nothing has changed.
                    map.put(range, existing);
                }
                //TODO why throw this after doing the put above?
                throw new IllegalStateException("A value already exists for " + range);
            }
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
            for (final Entry<String, Map<Range<Long>, ValueProxy<EventListValue>>> entry : rangeMap.entrySet()) {
                // Turn the map into an array of range stores.
                final Map<Range<Long>, ValueProxy<EventListValue>> map = entry.getValue();
                final RangeStore[] array = new RangeStore[map.size()];
                int i = 0;
                for (final Entry<Range<Long>, ValueProxy<EventListValue>> entry2 : map.entrySet()) {
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
