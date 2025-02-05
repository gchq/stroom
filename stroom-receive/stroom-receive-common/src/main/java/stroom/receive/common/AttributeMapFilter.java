/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;

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
        if (NullSafe.isEmptyCollection(attributeMapFilters)) {
            LOGGER.debug("Returning permissive instance");
            return PermissiveAttributeMapFilter.getInstance();
        } else if (attributeMapFilters.size() == 1) {
            final AttributeMapFilter first = NullSafe.first(attributeMapFilters);
            if (first != null) {
                LOGGER.debug(() -> "Returning " + first.getClass().getSimpleName());
                return first;
            } else {
                LOGGER.debug("Returning permissive instance");
                return PermissiveAttributeMapFilter.getInstance();
            }
        } else {
            final MultiAttributeMapFilter filter = new MultiAttributeMapFilter(attributeMapFilters);
            LOGGER.debug("Returning {}", filter);
            return filter;
        }
    }
}
