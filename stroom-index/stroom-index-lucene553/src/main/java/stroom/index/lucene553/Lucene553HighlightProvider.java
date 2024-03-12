package stroom.index.lucene553;

import stroom.dictionary.api.WordListProvider;
import stroom.expression.api.DateTimeSettings;
import stroom.index.impl.HighlightProvider;
import stroom.index.lucene553.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexFieldsMap;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Collections;
import java.util.Set;

class Lucene553HighlightProvider implements HighlightProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Lucene553HighlightProvider.class);

    private final WordListProvider wordListProvider;
    private final Provider<SearchConfig> searchConfigProvider;

    @Inject
    Lucene553HighlightProvider(final WordListProvider wordListProvider,
                                      final Provider<SearchConfig> searchConfigProvider) {
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
    }

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    @Override
    public Set<String> getHighlights(final LuceneIndexDoc index,
                                     final ExpressionOperator expression,
                                     final DateTimeSettings dateTimeSettings) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Create a map of index fields keyed by name.
            final LuceneIndexFieldsMap indexFieldsMap = new LuceneIndexFieldsMap(index.getFields());
            // Parse the query.
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider,
                    indexFieldsMap,
                    searchConfigProvider.get().getMaxBooleanClauseCount(),
                    dateTimeSettings);
            final SearchExpressionQuery query = searchExpressionQueryBuilder.buildQuery(expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }

}
