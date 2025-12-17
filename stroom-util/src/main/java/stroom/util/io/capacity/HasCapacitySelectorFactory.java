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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HasCapacitySelectorFactory {

    public static final String DEFAULT_SELECTOR_NAME = new RoundRobinCapacitySelector().getName();

    // Can't be built dynamically as it is used in annotations
    // Must be kept in sync with the map below.
    public static final String VOLUME_SELECTOR_PATTERN = "^(" +
                                                         MostFreePercentCapacitySelector.NAME + "|" +
                                                         MostFreeCapacitySelector.NAME + "|" +
                                                         RoundRobinCapacitySelector.NAME + "|" +
                                                         RandomCapacitySelector.NAME + "|" +
                                                         RoundRobinIgnoreLeastFreeCapacitySelector.NAME + "|" +
                                                         RoundRobinIgnoreLeastFreePercentCapacitySelector.NAME + "|" +
                                                         RoundRobinCapacitySelector.NAME + "|" +
                                                         WeightedFreePercentRandomCapacitySelector.NAME + "|" +
                                                         WeightedFreeRandomCapacitySelector.NAME + ")$";

    // Need to create an instance briefly just to get the name
    private static final Map<String, Supplier<HasCapacitySelector>> SELECTOR_FACTORY_MAP = Stream.of(
                    new MostFreePercentCapacitySelector(),
                    new MostFreeCapacitySelector(),
                    new RandomCapacitySelector(),
                    new RoundRobinIgnoreLeastFreePercentCapacitySelector(),
                    new RoundRobinIgnoreLeastFreeCapacitySelector(),
                    new RoundRobinCapacitySelector(),
                    new WeightedFreePercentRandomCapacitySelector(),
                    new WeightedFreeRandomCapacitySelector()
            )
            .collect(Collectors.toMap(
                    HasCapacitySelector::getName,
                    HasCapacitySelectorFactory::createSelectorSupplier
            ));

    public HasCapacitySelectorFactory() {
        if (!SELECTOR_FACTORY_MAP.containsKey(DEFAULT_SELECTOR_NAME)) {
            throw new RuntimeException("Selector " + DEFAULT_SELECTOR_NAME + " not found in map");
        }
    }

    public Optional<HasCapacitySelector> createSelector(final String name) {
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        } else {
            final Supplier<HasCapacitySelector> supplier = SELECTOR_FACTORY_MAP.get(name);
            if (supplier == null) {
                return Optional.empty();
            } else {
                return Optional.ofNullable(supplier.get());
            }
        }
    }

    public HasCapacitySelector createSelectorOrDefault(final String name) {
        return createSelector(name)
                .orElseGet(this::createDefaultSelector);
    }

    public HasCapacitySelector createDefaultSelector() {
        return createSelector(DEFAULT_SELECTOR_NAME)
                .orElseThrow(() -> new RuntimeException(
                        "Default selector " + DEFAULT_SELECTOR_NAME + " not found"));
    }

    public static Set<String> getSelectorNames() {
        return SELECTOR_FACTORY_MAP.keySet();
    }

    private static Supplier<HasCapacitySelector> createSelectorSupplier(final HasCapacitySelector selector) {
        try {
            final Constructor<? extends HasCapacitySelector> constructor = selector.getClass()
                    .getConstructor();

            return () -> {
                try {
                    return constructor.newInstance();
                } catch (final InstantiationException
                               | IllegalAccessException
                               | InvocationTargetException e) {
                    throw new RuntimeException("Unable to instantiate "
                                               + selector.getClass().getName()
                                               + ". Is there a public no-args constructor?");
                }
            };
        } catch (final Exception e) {
            throw new RuntimeException("Unable to instantiate "
                                       + selector.getClass().getName()
                                       + ". Is there a public no-args constructor?");
        }
    }
}
