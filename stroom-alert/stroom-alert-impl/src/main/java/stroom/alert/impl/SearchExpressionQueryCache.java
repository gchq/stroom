package stroom.alert.impl;

import stroom.index.impl.LuceneVersionUtil;
import stroom.index.impl.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.SearchRequest;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.impl.SearchExpressionQueryBuilderFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.analysis.Analyzer;

import java.util.HashMap;
import java.util.Map;

public class SearchExpressionQueryCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchExpressionQueryCache.class);

    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final SearchRequest searchRequest;

    private final IndexFieldsMap indexFieldsMap = new IndexFieldsMap();
    private final Map<String, Analyzer> analyzerMap = new HashMap<>();
    private SearchExpressionQuery luceneQuery;

    public SearchExpressionQueryCache(final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                                      final SearchRequest searchRequest) {
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.searchRequest = searchRequest;
    }

    public SearchExpressionQuery getQuery() {
        try {
            if (luceneQuery == null) {
                final SearchExpressionQueryBuilder searchExpressionQueryBuilder =
                        searchExpressionQueryBuilderFactory.create(
                                indexFieldsMap,
                                searchRequest.getDateTimeSettings(),
                                System.currentTimeMillis());
                luceneQuery = searchExpressionQueryBuilder
                        .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, searchRequest.getQuery().getExpression());
            }
            return luceneQuery;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public Analyzer getAnalyser(final IndexField indexField) {
        try {
            Analyzer fieldAnalyzer = analyzerMap.get(indexField.getFieldName());
            if (fieldAnalyzer == null) {
                // Add the field analyser.
                fieldAnalyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                analyzerMap.put(indexField.getFieldName(), fieldAnalyzer);
            }
            return fieldAnalyzer;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    public void addIndexField(final IndexField indexField) {
        if (indexFieldsMap.putIfAbsent(indexField) == null) {
            // We didn't already have this field so make sure the query is rebuilt.
            luceneQuery = null;
        }
    }
}
