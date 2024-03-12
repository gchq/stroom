package stroom.index.lucene553;

import stroom.index.lucene553.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.index.lucene553.analyser.AnalyzerFactory;
import stroom.index.shared.LuceneIndexField;
import stroom.index.shared.LuceneIndexFieldsMap;
import stroom.query.api.v2.SearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.lucene553.analysis.Analyzer;

import java.util.HashMap;
import java.util.Map;

class SearchExpressionQueryCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchExpressionQueryCache.class);

    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final LuceneIndexFieldsMap indexFieldsMap = new LuceneIndexFieldsMap();
    private final Map<String, Analyzer> analyzerMap = new HashMap<>();
    private SearchExpressionQuery luceneQuery;

    @Inject
    SearchExpressionQueryCache(final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory) {
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
    }

    SearchExpressionQuery getQuery(final SearchRequest searchRequest, final boolean ignoreMissingFields) {
        try {
            if (luceneQuery == null) {
                final SearchExpressionQueryBuilder searchExpressionQueryBuilder =
                        searchExpressionQueryBuilderFactory.create(
                                indexFieldsMap,
                                searchRequest.getDateTimeSettings());
                luceneQuery = searchExpressionQueryBuilder
                        .buildQuery(searchRequest.getQuery().getExpression());
            }
            return luceneQuery;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    Analyzer getAnalyser(final LuceneIndexField indexField) {
        try {
            Analyzer fieldAnalyzer = analyzerMap.get(indexField.getName());
            if (fieldAnalyzer == null) {
                // Add the field analyser.
                fieldAnalyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                analyzerMap.put(indexField.getName(), fieldAnalyzer);
            }
            return fieldAnalyzer;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    void addIndexField(final LuceneIndexField indexField) {
        if (indexFieldsMap.putIfAbsent(indexField) == null) {
            // We didn't already have this field so make sure the query is rebuilt.
            luceneQuery = null;
        }
    }
}
