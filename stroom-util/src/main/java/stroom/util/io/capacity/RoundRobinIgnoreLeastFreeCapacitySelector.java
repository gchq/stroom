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

package stroom.util.io.capacity;


import stroom.util.shared.HasCapacity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

public class RoundRobinIgnoreLeastFreeCapacitySelector extends AbstractSelector {

    public static final String NAME = "RoundRobinIgnoreLeastFree";

    public RoundRobinIgnoreLeastFreeCapacitySelector() {
        super(new RoundRobinCapacitySelector());
    }

    @Override
    public <T extends HasCapacity> T doSelect(final List<T> filteredList) {
        // super will use fallback selector to pick from the filtered list
        return null;
    }

    @Override
    <T extends HasCapacity> List<T> createFilteredList(final List<T> list) {
        long lowestFreeCapacity = Long.MAX_VALUE;
        List<Integer> lowestFreeCapacityIdxs = Collections.emptyList();
        for (int i = 0; i < list.size(); i++) {
            final OptionalLong optFreeCapacity = list.get(i).getCapacityInfo().getFreeCapacityBytes();
            if (optFreeCapacity.isPresent()) {
                final long freeCapacity = optFreeCapacity.getAsLong();
                if (freeCapacity < lowestFreeCapacity) {
                    lowestFreeCapacity = freeCapacity;
                    lowestFreeCapacityIdxs = List.of(i);
                } else if (freeCapacity == lowestFreeCapacity) {
                    // May have more than one item that all share the lowest free space
                    final List<Integer> newList = new ArrayList<>(lowestFreeCapacityIdxs.size() + 1);
                    newList.addAll(lowestFreeCapacityIdxs);
                    newList.add(i);
                    lowestFreeCapacityIdxs = newList;
                }
            }
        }

        // Now remove the items with the lowest free capacity by idx
        final List<T> filteredList = new ArrayList<>(list);
        for (final Integer idx : lowestFreeCapacityIdxs) {
            filteredList.remove((int) idx);
        }
        return filteredList;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
