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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Objects;

public interface AttributeMapFilter {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapFilter.class);

    /**
     * Used for filtering received data based on its attributeMap and userIdentity.
     * May add items to the {@link AttributeMap}, e.g. adding a Feed attribute if
     * possible and configured.
     *
     * @return True if the data should be accepted, false if it should be dropped.
     * @throws StroomStreamException When data should be rejected for some reason.
     */
    boolean filter(AttributeMap attributeMap);

    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Combine multiple filters into a single filter. Each one will be called in turn
     * until one returns false.
     * If null or empty, a permissive filter will be returned.
     */
    static AttributeMapFilter wrap(final AttributeMapFilter... attributeMapFilters) {
        return wrap(NullSafe.asList(attributeMapFilters));
    }

    /**
     * Combine multiple filters into a single filter. Each one will be called in turn
     * until one returns false.
     * If null or empty, a permissive filter will be returned.
     */
    static AttributeMapFilter wrap(final List<AttributeMapFilter> attributeMapFilters) {
        // No point evaluating filters if we have a reject-all in there
        if (NullSafe.stream(attributeMapFilters)
                .anyMatch(filter -> filter instanceof RejectAllAttributeMapFilter)) {
            return RejectAllAttributeMapFilter.getInstance();
        }

        // Ignore any permissive filters in the chain as they do nothing
        final List<AttributeMapFilter> filteredFilters = NullSafe.stream(attributeMapFilters)
                .filter(Objects::nonNull)
                .filter(filter ->
                        !(filter instanceof ReceiveAllAttributeMapFilter))
                .toList();

        if (filteredFilters.isEmpty()) {
            final AttributeMapFilter filter = ReceiveAllAttributeMapFilter.getInstance();
            LOGGER.debug(() -> LogUtil.message("No non-null attributeMapFilters, returning filter: '{}'",
                    filter.getName()));
            return ReceiveAllAttributeMapFilter.getInstance();
        } else if (filteredFilters.size() == 1) {
            final AttributeMapFilter filter = filteredFilters.getFirst();
            LOGGER.debug(() -> LogUtil.message("Returning single filter: '{}'", filter.getName()));
            return filter;
        } else {
            final MultiAttributeMapFilter filter = new MultiAttributeMapFilter(filteredFilters);
            LOGGER.debug("Returning filter chain: {}", filter);
            return filter;
        }
    }

    /**
     * @return A filter that applies the supplied action to ALL data regardless of
     * what is passed into the filter.
     */
    static AttributeMapFilter getBlanketFilter(final ReceiveAction receiveAction) {
        return switch (Objects.requireNonNull(receiveAction)) {
            case DROP -> DropAllAttributeMapFilter.getInstance();
            case RECEIVE -> ReceiveAllAttributeMapFilter.getInstance();
            case REJECT -> RejectAllAttributeMapFilter.getInstance();
        };
    }
}
