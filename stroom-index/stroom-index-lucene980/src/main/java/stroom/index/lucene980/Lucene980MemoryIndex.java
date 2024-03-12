package stroom.index.lucene980;

import stroom.index.lucene980.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.index.shared.IndexField;
import stroom.index.shared.LuceneIndexField;
import stroom.query.api.v2.SearchRequest;
import stroom.search.extraction.FieldValue;
import stroom.search.impl.SearchException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.lucene980.analysis.Analyzer;
import org.apache.lucene980.index.IndexableField;
import org.apache.lucene980.index.memory.MemoryIndex;
import org.apache.lucene980.search.IndexSearcher;
import org.apache.lucene980.search.TopDocs;

import java.io.IOException;
import java.util.List;

class Lucene980MemoryIndex implements stroom.search.extraction.MemoryIndex {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Lucene980MemoryIndex.class);

    private final SearchExpressionQueryCache searchExpressionQueryCache;

    @Inject
    public Lucene980MemoryIndex(final SearchExpressionQueryCache searchExpressionQueryCache) {
        this.searchExpressionQueryCache = searchExpressionQueryCache;
    }

    @Override
    public boolean match(final SearchRequest searchRequest, final List<FieldValue> fieldValues) {
        final MemoryIndex memoryIndex = new MemoryIndex();
        for (final FieldValue fieldValue : fieldValues) {
            final IndexField indexField = fieldValue.field();
            final LuceneIndexField luceneIndexField = LuceneIndexField
                    .fromIndexField(indexField);

            if (luceneIndexField.isIndexed()) {
                final Analyzer fieldAnalyzer = searchExpressionQueryCache.getAnalyser(luceneIndexField);
                final IndexableField field = FieldFactory.create(fieldValue);
                if (field != null) {
                    memoryIndex.addField(field, fieldAnalyzer);
                }
            }

            searchExpressionQueryCache.addIndexField(luceneIndexField);
        }

        // See if this set of fields matches the rule expression.
        return matchQuery(searchRequest, memoryIndex);
    }

    private boolean matchQuery(final SearchRequest searchRequest, final MemoryIndex memoryIndex) {
        try {
            final SearchExpressionQuery query = searchExpressionQueryCache.getQuery(searchRequest, true);
            final IndexSearcher indexSearcher = memoryIndex.createSearcher();
            final TopDocs docs = indexSearcher.search(query.getQuery(), 100);

            if (docs.totalHits.value == 0) {
                return false;
            } else if (docs.totalHits.value == 1) {
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
