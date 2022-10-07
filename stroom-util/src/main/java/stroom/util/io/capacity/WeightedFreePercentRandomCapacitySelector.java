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

import java.util.List;
import java.util.stream.Collectors;

public class WeightedFreePercentRandomCapacitySelector implements HasCapacitySelector {
    public static final String NAME = "WeightedFreePercentRandom";

    private final RandomCapacitySelector randomCapacitySelector = new RandomCapacitySelector();

    @Override
    public <T extends HasCapacity> T select(final List<T> list) {
        final List<T> filtered = list.stream()
                .filter(hasCapacity -> hasCapacity.getCapacityInfo().hasValidState())
                .collect(Collectors.toList());

        if (filtered.size() == 0) {
            return randomCapacitySelector.select(list);
        } else if (filtered.size() == 1) {
            return filtered.get(0);
        } else {
            final double[] thresholds = getWeightingThresholds(filtered);
            final double random = Math.random();

            int index = thresholds.length - 1;
            for (int i = 0; i < thresholds.length; i++) {
                if (thresholds[i] >= random) {
                    index = i;
                    break;
                }
            }

            return filtered.get(index);
        }
    }

    private <T extends HasCapacity> double[] getWeightingThresholds(final List<T> list) {

        double totalFractionFree = 0;
        for (final T item : list) {
            @SuppressWarnings("OptionalGetWithoutIsPresent") // hasValidState has been checked
            final double fractionFree = item.getCapacityInfo().getFreeCapacityPercent().getAsDouble() / 100;

            totalFractionFree += fractionFree;
        }

        final double increment = 1D / totalFractionFree;
        final double[] thresholds = new double[list.size()];
        int i = 0;
        for (final T item : list) {
            @SuppressWarnings("OptionalGetWithoutIsPresent") // hasValidState has been checked
            final double fractionFree = item.getCapacityInfo().getFreeCapacityPercent().getAsDouble() / 100;

            thresholds[i] = increment * fractionFree;
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
