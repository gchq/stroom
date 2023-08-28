package stroom.analytics.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.SearchRequest;

public class StreamingAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    private final DetectionConsumerProxy detectionConsumerProxy;

    public StreamingAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                              final FieldIndex fieldIndex,
                                              final NotificationState notificationState,
                                              final ValuesConsumer valuesConsumer,
                                              final SearchExpressionQueryCache searchExpressionQueryCache,
                                              final Long minEventId,
                                              final DetectionConsumerProxy detectionConsumerProxy) {
        super(searchRequest, fieldIndex, notificationState, valuesConsumer, searchExpressionQueryCache, minEventId);
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
