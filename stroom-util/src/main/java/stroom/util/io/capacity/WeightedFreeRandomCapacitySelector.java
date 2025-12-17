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

import java.util.List;
import java.util.stream.Collectors;

public class WeightedFreeRandomCapacitySelector extends AbstractSelector {

    public static final String NAME = "WeightedFreeRandom";

    public WeightedFreeRandomCapacitySelector() {
        super(new RandomCapacitySelector());
    }

    @Override
    public <T extends HasCapacity> T doSelect(final List<T> filteredList) {

        final double[] thresholds = getWeightingThresholds(filteredList);
        final double random = Math.random();

        int index = thresholds.length - 1;
        for (int i = 0; i < thresholds.length; i++) {
            if (thresholds[i] >= random) {
                index = i;
                break;
            }
        }

        return filteredList.get(index);
    }

    @Override
    <T extends HasCapacity> List<T> createFilteredList(final List<T> list) {
        return list.stream()
                .filter(hasCapacity ->
                        hasCapacity.getCapacityInfo().hasValidState())
                .collect(Collectors.toList());
    }

    private <T extends HasCapacity> double[] getWeightingThresholds(final List<T> list) {
        double totalFree = 0;
        for (final T item : list) {
            @SuppressWarnings("OptionalGetWithoutIsPresent") // hasValidState has been checked
            final double free = item.getCapacityInfo().getFreeCapacityBytes().getAsLong();

            totalFree += free;
        }

        final double increment = 1D / totalFree;
        final double[] thresholds = new double[list.size()];
        int i = 0;
        for (final T item : list) {
            @SuppressWarnings("OptionalGetWithoutIsPresent") // hasValidState has been checked
            final double free = item.getCapacityInfo().getFreeCapacityBytes().getAsLong();

            thresholds[i] = increment * free;
            if (i > 0) {
                thresholds[i] += thresholds[i - 1];
            }

            i++;
        }

        return thresholds;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
