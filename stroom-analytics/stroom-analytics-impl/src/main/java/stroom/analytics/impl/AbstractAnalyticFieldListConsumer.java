package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.query.api.v2.SearchRequest;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;
import stroom.search.extraction.MemoryIndex;

import java.util.List;

abstract class AbstractAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final SearchRequest searchRequest;
    private final FieldIndex fieldIndex;
    private final NotificationState notificationState;
    private final ValuesConsumer valuesConsumer;
    private final MemoryIndex memoryIndex;
    private final Long minEventId;

    private long eventId;

    AbstractAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                      final FieldIndex fieldIndex,
                                      final NotificationState notificationState,
                                      final ValuesConsumer valuesConsumer,
                                      final MemoryIndex memoryIndex,
                                      final Long minEventId) {
        this.searchRequest = searchRequest;
        this.fieldIndex = fieldIndex;
        this.notificationState = notificationState;
        this.valuesConsumer = valuesConsumer;
        this.memoryIndex = memoryIndex;
        this.minEventId = minEventId;
    }

    @Override
    public void accept(final List<FieldValue> fieldValues) {
        // Only notify if the state is enabled.
        notificationState.enableIfPossible();
        if (notificationState.isEnabled()) {
            eventId++;

            // Filter events if we have already added them to LMDB.
            if (minEventId == null || minEventId <= eventId) {
                // See if this set of fields matches the rule expression.
                if (memoryIndex.match(searchRequest, fieldValues)) {
                    // We have a match so pass the values on to the receiver.
                    final Val[] values = new Val[fieldIndex.size()];
                    for (final FieldValue fieldValue : fieldValues) {
                        final Integer index = fieldIndex.getPos(fieldValue.field().getFieldName());
                        if (index != null) {
                            values[index] = fieldValue.value();
                        }
                    }

                    if (notificationState.incrementAndCheckEnabled()) {
                        valuesConsumer.accept(Val.of(values));
                    }
                }
            }
        }
    }
}
