package stroom.index.lucene;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.impl.HighlightProvider;
import stroom.index.lucene.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.lucene.search.IndexSearcher;

import java.util.Collections;
import java.util.Set;

class LuceneHighlightProvider implements HighlightProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneHighlightProvider.class);

    private final IndexFieldCache indexFieldCache;
    private final WordListProvider wordListProvider;
    private final Provider<SearchConfig> searchConfigProvider;

    @Inject
    LuceneHighlightProvider(final IndexFieldCache indexFieldCache,
                            final WordListProvider wordListProvider,
                            final Provider<SearchConfig> searchConfigProvider) {
        this.indexFieldCache = indexFieldCache;
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
    }

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    @Override
    public Set<String> getHighlights(final DocRef indexDocRef,
                                     final IndexFieldCache indexFieldCache,
                                     final ExpressionOperator expression,
                                     final DateTimeSettings dateTimeSettings) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Parse the query.
            IndexSearcher.setMaxClauseCount(searchConfigProvider.get().getMaxBooleanClauseCount());
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    indexDocRef,
                    indexFieldCache,
                    wordListProvider,
                    dateTimeSettings);
            final SearchExpressionQuery query = searchExpressionQueryBuilder.buildQuery(expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }

}
