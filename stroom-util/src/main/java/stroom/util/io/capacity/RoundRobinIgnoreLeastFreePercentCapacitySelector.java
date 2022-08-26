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

package stroom.util.io.capacity;

import stroom.util.shared.HasCapacity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

public class RoundRobinIgnoreLeastFreePercentCapacitySelector implements HasCapacitySelector {

    public static final String NAME = "RoundRobinIgnoreLeastFreePercent";

    private final RoundRobinCapacitySelector roundRobinCapacitySelector = new RoundRobinCapacitySelector();

    @Override
    public <T extends HasCapacity> T select(final List<T> list) {

        if (list == null || list.isEmpty()) {
            throw new RuntimeException("No items provided to select from");
        } else if (list.size() == 1) {
            return list.get(0);
        } else {
            double lowestFreePercent = Long.MAX_VALUE;
            List<Integer> lowestFreePercentIdxs = Collections.emptyList();
            for (int i = 0; i < list.size(); i++) {
                final OptionalDouble optFreePercent = list.get(i).getFreeCapacityPercent();
                if (optFreePercent.isPresent()) {
                    double freePercent = optFreePercent.getAsDouble();
                    if (freePercent < lowestFreePercent) {
                        lowestFreePercent = freePercent;
                        lowestFreePercentIdxs = List.of(i);
                    } else if (freePercent == lowestFreePercent) {
                        // May have more than one item that all share the lowest free space
                        final List<Integer> newList = new ArrayList<>(lowestFreePercentIdxs.size() + 1);
                        newList.addAll(lowestFreePercentIdxs);
                        newList.add(i);
                        lowestFreePercentIdxs = newList;
                    }
                }
            }

            // Now remove the items with the lowest free capacity by idx
            final List<T> filteredList = new ArrayList<>(list);
            for (final Integer idx : lowestFreePercentIdxs) {
                filteredList.remove((int) idx);
            }

            if (filteredList.isEmpty()) {
                // No items after filter, so we are just going to have to use one of the ones
                // we filtered out.
                return roundRobinCapacitySelector.select(list);
            } else if (filteredList.size() == 1) {
                return filteredList.get(0);
            } else {
                return roundRobinCapacitySelector.select(filteredList);
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
