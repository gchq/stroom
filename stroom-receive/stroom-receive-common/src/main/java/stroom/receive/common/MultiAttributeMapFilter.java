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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.stream.Collectors;

class MultiAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MultiAttributeMapFilter.class);

    private final List<AttributeMapFilter> attributeMapFilters;

    MultiAttributeMapFilter(final List<AttributeMapFilter> attributeMapFilters) {
        if (NullSafe.isEmptyCollection(attributeMapFilters)) {
            throw new IllegalArgumentException("Null or empty attributeMapFilters");
        }
        this.attributeMapFilters = attributeMapFilters;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        for (final AttributeMapFilter attributeMapFilter : attributeMapFilters) {
            if (attributeMapFilter != null) {
                final boolean filterResult = attributeMapFilter.filter(attributeMap);
                LOGGER.debug(() -> LogUtil.message("filter: {}, filterResult: {}, attributeMap: {}",
                        attributeMapFilter.getClass().getSimpleName(),
                        filterResult,
                        attributeMap));
                if (!filterResult) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return attributeMapFilters.stream()
                .map(AttributeMapFilter::getName)
                .collect(Collectors.joining(" -> "));
    }
}
