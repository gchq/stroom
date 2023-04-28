package stroom.analytics.impl;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.index.impl.FieldFactory;
import stroom.index.shared.IndexField;
import stroom.pipeline.filter.FieldValue;
import stroom.query.api.v2.SearchRequest;
import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class AnalyticFieldListConsumer implements Consumer<List<FieldValue>> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticFieldListConsumer.class);

    private final SearchRequest searchRequest;
    private final FieldIndex fieldIndex;
    private final ValuesConsumer valuesConsumer;
    private final SearchExpressionQueryCache searchExpressionQueryCache;
    private final Long minEventId;

    private long eventId;

    public AnalyticFieldListConsumer(final SearchRequest searchRequest,
                                     final FieldIndex fieldIndex,
                                     final ValuesConsumer valuesConsumer,
                                     final SearchExpressionQueryCache searchExpressionQueryCache,
                                     final Long minEventId) {
        this.searchRequest = searchRequest;
        this.fieldIndex = fieldIndex;
        this.valuesConsumer = valuesConsumer;
        this.searchExpressionQueryCache = searchExpressionQueryCache;
        this.minEventId = minEventId;
    }

    @Override
    public void accept(final List<FieldValue> fieldValues) {
        eventId++;

        // Filter events if we have already added them to LMDB.
        if (minEventId == null || minEventId <= eventId) {
            final MemoryIndex memoryIndex = new MemoryIndex();
            for (final FieldValue fieldValue : fieldValues) {
                final IndexField indexField = fieldValue.field();
                if (indexField.isIndexed()) {
                    final Analyzer fieldAnalyzer = searchExpressionQueryCache.getAnalyser(indexField);
                    final IndexableField field = FieldFactory.create(fieldValue);
                    TokenStream tokenStream = field.tokenStream(fieldAnalyzer, null);
                    if (tokenStream != null) {
                        memoryIndex.addField(field.name(), tokenStream, field.boost());
                    }
                }

                searchExpressionQueryCache.addIndexField(indexField);
            }

            // See if this set of fields matches the rule expression.
            if (matchQuery(memoryIndex)) {
                // We have a match so pass the values on to the receiver.
                final Val[] values = new Val[fieldIndex.size()];
                for (final FieldValue fieldValue : fieldValues) {
                    final Integer index = fieldIndex.getPos(fieldValue.field().getFieldName());
                    if (index != null) {
                        values[index] = fieldValue.value();
                    }
                }
                valuesConsumer.add(Val.of(values));
            }
        }
    }

    private boolean matchQuery(final MemoryIndex memoryIndex) {
        try {
            final SearchExpressionQuery query = searchExpressionQueryCache.getQuery();
            final IndexSearcher indexSearcher = memoryIndex.createSearcher();
            final TopDocs docs = indexSearcher.search(query.getQuery(), 100);

            if (docs.totalHits == 0) {
                return false;
            } else if (docs.totalHits == 1) {
                return true;
            } else {
                LOGGER.error("Unexpected number of documents {}  found by rule, should be 1 or 0.", docs.totalHits);
            }
        } catch (final SearchException | IOException se) {
            LOGGER.warn("Unable to create alerts for rule " + searchRequest.getQuery() + " due to " + se.getMessage());
        }

        return false;
    }
}
