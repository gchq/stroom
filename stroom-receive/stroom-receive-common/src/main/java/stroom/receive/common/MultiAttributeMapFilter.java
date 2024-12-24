package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;
import stroom.util.NullSafe;

import java.util.List;

class MultiAttributeMapFilter implements AttributeMapFilter {

    private final List<AttributeMapFilter> attributeMapFilters;

    MultiAttributeMapFilter(final List<AttributeMapFilter> attributeMapFilters) {
        if (NullSafe.isEmptyCollection(attributeMapFilters)) {
            throw new IllegalArgumentException("Null or empty attributeMapFilters");
        }
        this.attributeMapFilters = attributeMapFilters;
    }

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
