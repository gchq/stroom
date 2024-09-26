package stroom.analytics.impl;

import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.StringFieldValue;
import stroom.query.common.v2.ValFilter;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;

import java.util.List;
import java.util.function.Predicate;

abstract class AbstractAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final SearchRequest searchRequest;
    private final CompiledColumns compiledColumns;
    private final FieldValueExtractor fieldValueExtractor;
    private final ValuesConsumer valuesConsumer;
    private final MemoryIndex memoryIndex;
    private final Long minEventId;
    private final Predicate<Val[]> valFilter;

    private long eventId;

    AbstractAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                      final CompiledColumns compiledColumns,
                                      final FieldValueExtractor fieldValueExtractor,
                                      final ValuesConsumer valuesConsumer,
                                      final MemoryIndex memoryIndex,
                                      final Long minEventId,
                                      final Predicate<Val[]> valFilter) {
        this.searchRequest = searchRequest;
        this.compiledColumns = compiledColumns;
        this.fieldValueExtractor = fieldValueExtractor;
        this.valuesConsumer = valuesConsumer;
        this.memoryIndex = memoryIndex;
        this.minEventId = minEventId;
        this.valFilter = valFilter;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        eventId++;

        final FieldIndex fieldIndex = compiledColumns.getFieldIndex();
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

                if (valFilter.test(values)) {
                    valuesConsumer.accept(Val.of(values));
                }
            }
        }
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        acceptFieldValues(fieldValueExtractor.convert(stringValues));
    }
}
