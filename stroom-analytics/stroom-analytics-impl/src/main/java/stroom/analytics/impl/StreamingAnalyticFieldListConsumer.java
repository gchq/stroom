package stroom.analytics.impl;

import stroom.query.api.SearchRequest;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;
import stroom.search.extraction.ProcessLifecycleAware;

import java.util.function.Predicate;

public class StreamingAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    private final ProcessLifecycleAware detectionConsumerProxy;

    public StreamingAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                              final CompiledColumns compiledColumns,
                                              final FieldValueExtractor fieldValueExtractor,
                                              final ValuesConsumer valuesConsumer,
                                              final MemoryIndex memoryIndex,
                                              final Long minEventId,
                                              final DetectionConsumerProxy detectionConsumerProxy,
                                              final Predicate<Val[]> valFilter) {
        super(
                searchRequest,
                compiledColumns,
                fieldValueExtractor,
                valuesConsumer,
                memoryIndex,
                minEventId,
                valFilter);
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
