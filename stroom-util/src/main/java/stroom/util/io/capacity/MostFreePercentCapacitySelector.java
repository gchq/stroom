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
import java.util.Objects;
import java.util.OptionalDouble;

public class MostFreePercentCapacitySelector implements HasCapacitySelector {
    public static final String NAME = "MostFreePercent";

    private final RoundRobinCapacitySelector roundRobinCapacitySelector = new RoundRobinCapacitySelector();

    @Override
    public <T extends HasCapacity> T select(final List<T> list) {

        if (list == null || list.isEmpty()) {
            throw new RuntimeException("No items provided to select from");
        } else if (list.size() == 1) {
            return list.get(0);
        } else {
            double largestPercentFree = 0;
            T selected = null;
            for (final T item : list) {
                final OptionalDouble optPercentFree = item.getFreeCapacityPercent();
                if (optPercentFree.isPresent() &&
                        optPercentFree.getAsDouble() > largestPercentFree) {
                    largestPercentFree = optPercentFree.getAsDouble();
                    selected = item;
                }
            }

            return Objects.requireNonNullElseGet(selected, () ->
                    roundRobinCapacitySelector.select(list));
        }
    }

    @Override
    public String getName() {
        return NAME;
    }
}
