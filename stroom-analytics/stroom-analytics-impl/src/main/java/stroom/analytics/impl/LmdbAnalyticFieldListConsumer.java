package stroom.analytics.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.SearchRequest;

public class LmdbAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    public LmdbAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                         final FieldIndex fieldIndex,
                                         final ValuesConsumer valuesConsumer,
                                         final SearchExpressionQueryCache searchExpressionQueryCache,
                                         final Long minEventId) {
        super(searchRequest, fieldIndex, valuesConsumer, searchExpressionQueryCache, minEventId);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {

    }
}
