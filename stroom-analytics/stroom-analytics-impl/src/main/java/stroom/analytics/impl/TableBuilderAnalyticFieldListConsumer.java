package stroom.analytics.impl;

import stroom.query.api.v2.SearchRequest;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.FieldValueExtractor;

public class TableBuilderAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    public TableBuilderAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                                 final FieldIndex fieldIndex,
                                                 final FieldValueExtractor fieldValueExtractor,
                                                 final NotificationState notificationState,
                                                 final ValuesConsumer valuesConsumer,
                                                 final SearchExpressionQueryCache searchExpressionQueryCache,
                                                 final Long minEventId) {
        super(
                searchRequest,
                fieldIndex,
                fieldValueExtractor,
                notificationState,
                valuesConsumer,
                searchExpressionQueryCache,
                minEventId);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {

    }
}
