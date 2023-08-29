package stroom.analytics.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.SearchRequest;

public class TableBuilderAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    public TableBuilderAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                                 final FieldIndex fieldIndex,
                                                 final NotificationState notificationState,
                                                 final ValuesConsumer valuesConsumer,
                                                 final SearchExpressionQueryCache searchExpressionQueryCache,
                                                 final Long minEventId) {
        super(searchRequest, fieldIndex, notificationState, valuesConsumer, searchExpressionQueryCache, minEventId);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {

    }
}
