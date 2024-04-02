package stroom.analytics.impl;

import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;

import java.util.List;

abstract class AbstractAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final SearchRequest searchRequest;
    private final FieldIndex fieldIndex;
    private final FieldValueExtractor fieldValueExtractor;
    private final ValuesConsumer valuesConsumer;
    private final MemoryIndex memoryIndex;
    private final Long minEventId;

    private long eventId;

    AbstractAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                      final FieldIndex fieldIndex,
                                      final FieldValueExtractor fieldValueExtractor,
                                      final ValuesConsumer valuesConsumer,
                                      final MemoryIndex memoryIndex,
                                      final Long minEventId) {
        this.searchRequest = searchRequest;
        this.fieldIndex = fieldIndex;
        this.fieldValueExtractor = fieldValueExtractor;
        this.valuesConsumer = valuesConsumer;
        this.memoryIndex = memoryIndex;
        this.minEventId = minEventId;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        eventId++;

        // Filter events if we have already added them to LMDB.
        if (minEventId == null || minEventId <= eventId) {
            // See if this set of fields matches the rule expression.
            if (memoryIndex.match(searchRequest, fieldValues)) {
                // We have a match so pass the values on to the receiver.
                final Val[] values = new Val[fieldIndex.size()];
                for (final FieldValue fieldValue : fieldValues) {
                    final Integer index = fieldIndex.getPos(fieldValue.field().getFldName());
                    if (index != null) {
                        values[index] = fieldValue.value();
                    }
                }

                valuesConsumer.accept(Val.of(values));
            }
        }
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        acceptFieldValues(fieldValueExtractor.convert(stringValues));
    }
}
