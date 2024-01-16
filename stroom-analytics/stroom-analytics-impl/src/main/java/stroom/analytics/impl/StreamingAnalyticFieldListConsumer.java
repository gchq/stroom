package stroom.analytics.impl;

import stroom.query.api.v2.SearchRequest;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.FieldValueExtractor;

public class StreamingAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    private final DetectionConsumerProxy detectionConsumerProxy;

    public StreamingAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                              final FieldIndex fieldIndex,
                                              final FieldValueExtractor fieldValueExtractor,
                                              final NotificationState notificationState,
                                              final ValuesConsumer valuesConsumer,
                                              final SearchExpressionQueryCache searchExpressionQueryCache,
                                              final Long minEventId,
                                              final DetectionConsumerProxy detectionConsumerProxy) {
        super(
                searchRequest,
                fieldIndex,
                fieldValueExtractor,
                notificationState,
                valuesConsumer,
                searchExpressionQueryCache,
                minEventId);
        this.detectionConsumerProxy = detectionConsumerProxy;
    }

    @Override
    public void start() {
        detectionConsumerProxy.start();
    }

    @Override
    public void end() {
        detectionConsumerProxy.end();
    }
}
