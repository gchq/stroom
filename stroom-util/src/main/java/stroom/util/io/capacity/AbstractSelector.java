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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasCapacity;
import stroom.util.shared.HasCapacityInfo;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.stream.Collectors;

public abstract class AbstractSelector implements HasCapacitySelector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractSelector.class);
    private static final NumberFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.#");

    private final HasCapacitySelector fallbackSelector;

    protected AbstractSelector(final HasCapacitySelector fallbackSelector) {
        this.fallbackSelector = fallbackSelector;
    }

    public abstract String getName();

    /**
     * Override this method if the selector needs to filter the original list of items.
     * The passed <pre>list</pre> should not be mutated.
     */
    <T extends HasCapacity> List<T> createFilteredList(final List<T> list) {
        return list;
    }

    public <T extends HasCapacity> T select(final List<T> list) {
        LOGGER.trace(() -> LogUtil.message("select() called on {} for items [{}]",
                this.getClass().getSimpleName(),
                dumpList(list)));

        if (list == null || list.isEmpty()) {
            throw new RuntimeException("No items provided to select from");
        } else {
            // Let the sub-class filter out items from the list if they want
            final List<T> filteredList = createFilteredList(list);

            if (filteredList.isEmpty()) {
                // We have filtered everything out so have to just grab from original
                // list with our fallback selector
                LOGGER.trace("All items filtered out so delegating to fallback selector");
                return fallbackSelector.select(list);
            } else if (filteredList.size() == 1) {
                return filteredList.get(0);
            } else {
                // Now let the sub-class do the selection
                T selected = doSelect(filteredList);

                LOGGER.trace(() -> LogUtil.message("Filtered items for {} [{}]",
                        this.getClass().getSimpleName(),
                        dumpList(filteredList)));

                if (selected == null && fallbackSelector != null) {
                    LOGGER.trace(() -> LogUtil.message(
                            "Falling back to selector {}",
                            fallbackSelector.getClass().getSimpleName()));
                    if (!filteredList.isEmpty()) {
                        selected = fallbackSelector.select(filteredList);
                    } else {
                        selected = fallbackSelector.select(list);
                    }
                }

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("{} selected: {}", this.getClass().getSimpleName(), itemToStr(selected));
                }
                return selected;
            }
        }
    }

    /**
     * Selects an item from the list containing more than one item.
     *
     * @param filteredList Will not be null or empty
     * @return An item from the list. If one can't be selected then return null
     * and the fallback selector (if provided) will be used instead.
     */
    abstract <T extends HasCapacity> T doSelect(final List<T> filteredList);

    private <T extends HasCapacity> String dumpList(final List<T> list) {
        return NullSafe.get(
                list,
                list2 ->
                        list2.stream()
                                .map(item -> "{" + itemToStr(item) + "}")
                                .collect(Collectors.joining(", ")));
    }

    public HasCapacitySelector getFallbackSelector() {
        return fallbackSelector;
    }

    static <T extends HasCapacity, R> String itemToStr(final T item) {
        if (item == null) {
            return "";
        } else if (item.getCapacityInfo() == null) {
            return LogUtil.message("{} - <NO CAPACITY INFO>",
                    item.getIdentifier());
        } else {
            final HasCapacityInfo capacityInfo = item.getCapacityInfo();
            return LogUtil.message("{} - used: {} ({}%), free {} ({}%), limit: {}, total: {}",
                    item.getIdentifier(),
                    optLongToStr(capacityInfo.getCapacityUsedBytes()),
                    optDoubleToStr(capacityInfo.getUsedCapacityPercent()),
                    optLongToStr(capacityInfo.getFreeCapacityBytes()),
                    optDoubleToStr(capacityInfo.getFreeCapacityPercent()),
                    optLongToStr(capacityInfo.getCapacityLimitBytes()),
                    optLongToStr(capacityInfo.getTotalCapacityBytes()));
        }
    }

    private static String optLongToStr(final OptionalLong optLong) {
        return optLong.isPresent()
                ? ModelStringUtil.formatCsv(optLong.getAsLong())
                : "-";
    }

    private static String optDoubleToStr(final OptionalDouble optDouble) {
        return optDouble.isPresent()
                ? DECIMAL_FORMAT.format(optDouble.getAsDouble())
                : "-";
    }

    @Override
    public String toString() {
        return getName() + " (fallback: "
               + NullSafe.get(fallbackSelector, selector ->
                selector.getClass().getSimpleName())
               + ")";
    }
}
