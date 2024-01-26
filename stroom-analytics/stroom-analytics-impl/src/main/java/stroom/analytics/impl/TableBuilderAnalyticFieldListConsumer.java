package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.query.api.v2.SearchRequest;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;

public class TableBuilderAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    public TableBuilderAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                                 final FieldIndex fieldIndex,
                                                 final FieldValueExtractor fieldValueExtractor,
                                                 final NotificationState notificationState,
                                                 final ValuesConsumer valuesConsumer,
                                                 final MemoryIndex memoryIndex,
                                                 final Long minEventId) {
        super(
                searchRequest,
                fieldIndex,
                fieldValueExtractor,
                notificationState,
                valuesConsumer,
                memoryIndex,
                minEventId);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {

    }
}
