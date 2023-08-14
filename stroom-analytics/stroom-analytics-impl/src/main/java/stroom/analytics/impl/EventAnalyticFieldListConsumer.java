package stroom.analytics.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.query.api.v2.SearchRequest;

public class EventAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    private final DetectionWriterProxy detectionWriterProxy;

    public EventAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                          final FieldIndex fieldIndex,
                                          final ValuesConsumer valuesConsumer,
                                          final SearchExpressionQueryCache searchExpressionQueryCache,
                                          final Long minEventId,
                                          final DetectionWriterProxy detectionWriterProxy) {
        super(searchRequest, fieldIndex, valuesConsumer, searchExpressionQueryCache, minEventId);
        this.detectionWriterProxy = detectionWriterProxy;
    }

    @Override
    public void start() {
        detectionWriterProxy.start();
    }

    @Override
    public void end() {
        detectionWriterProxy.end();
    }
}
