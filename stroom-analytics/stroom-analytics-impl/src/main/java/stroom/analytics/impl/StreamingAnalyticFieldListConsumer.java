package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.query.api.v2.SearchRequest;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.MemoryIndex;
import stroom.search.extraction.ProcessLifecycleAware;

public class StreamingAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    private final ProcessLifecycleAware detectionConsumerProxy;

    public StreamingAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                              final FieldIndex fieldIndex,
                                              final NotificationState notificationState,
                                              final ValuesConsumer valuesConsumer,
                                              final MemoryIndex memoryIndex,
                                              final Long minEventId,
                                              final ProcessLifecycleAware detectionConsumerProxy) {
        super(searchRequest, fieldIndex, notificationState, valuesConsumer, memoryIndex, minEventId);
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
