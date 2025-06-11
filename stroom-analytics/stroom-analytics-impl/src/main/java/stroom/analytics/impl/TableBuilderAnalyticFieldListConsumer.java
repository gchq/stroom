package stroom.analytics.impl;

import stroom.query.api.SearchRequest;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;

import java.util.function.Predicate;

public class TableBuilderAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    public TableBuilderAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                                 final CompiledColumns compiledColumns,
                                                 final FieldValueExtractor fieldValueExtractor,
                                                 final ValuesConsumer valuesConsumer,
                                                 final MemoryIndex memoryIndex,
                                                 final Long minEventId,
                                                 final Predicate<Val[]> valFilter) {
        super(
                searchRequest,
                compiledColumns,
                fieldValueExtractor,
                valuesConsumer,
                memoryIndex,
                minEventId,
                valFilter);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {

    }
}
