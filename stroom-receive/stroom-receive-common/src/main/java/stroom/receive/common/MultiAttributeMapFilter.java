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
