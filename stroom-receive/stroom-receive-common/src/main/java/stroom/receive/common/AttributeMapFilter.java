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
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;

import java.util.List;

public interface AttributeMapFilter {

    boolean filter(AttributeMap attributeMap, final UserIdentity userIdentity);

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
            return PermissiveAttributeMapFilter.getInstance();
        } else if (attributeMapFilters.size() == 1 && attributeMapFilters.getFirst() != null) {
            return attributeMapFilters.getFirst();
        } else {
            return new MultiAttributeMapFilter(attributeMapFilters);
        }
    }


    // --------------------------------------------------------------------------------


    class MultiAttributeMapFilter implements AttributeMapFilter {

        private final List<AttributeMapFilter> attributeMapFilters;

        private MultiAttributeMapFilter(final List<AttributeMapFilter> attributeMapFilters) {
            if (NullSafe.isEmptyCollection(attributeMapFilters)) {
                throw new IllegalArgumentException("Null or empty attributeMapFilters");
            }
            this.attributeMapFilters = attributeMapFilters;
        }

//        public static AttributeMapFilter wrap(final AttributeMapFilter... attributeMapFilters) {
//            return wrap(NullSafe.asList(attributeMapFilters));
//        }
//
//        public static AttributeMapFilter wrap(final List<AttributeMapFilter> attributeMapFilters) {
//            if (NullSafe.isEmptyCollection(attributeMapFilters)) {
//                return PermissiveAttributeMapFilter.getInstance();
//            } else if (attributeMapFilters.size() == 1 && attributeMapFilters.getFirst() != null) {
//                return attributeMapFilters.getFirst();
//            } else {
//                return new MultiAttributeMapFilter(attributeMapFilters);
//            }
//        }

        @Override
        public boolean filter(final AttributeMap attributeMap, final UserIdentity userIdentity) {
            for (final AttributeMapFilter attributeMapFilter : attributeMapFilters) {
                if (attributeMapFilter != null) {
                    final boolean filterResult = attributeMapFilter.filter(attributeMap, userIdentity);
                    if (!filterResult) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
