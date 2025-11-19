package stroom.index.lucene;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.apache.lucene.search.IndexSearcher;

class SearchExpressionQueryBuilderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchExpressionQueryBuilderFactory.class);

    private final WordListProvider wordListProvider;
    private final Provider<SearchConfig> searchConfigProvider;

    @Inject
    SearchExpressionQueryBuilderFactory(final WordListProvider wordListProvider,
                                        final Provider<SearchConfig> searchConfigProvider) {
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
    }

    public SearchExpressionQueryBuilder create(final DocRef indexDocRef,
                                               final IndexFieldCache indexFieldCache,
                                               final DateTimeSettings dateTimeSettings) {
        try {
            IndexSearcher.setMaxClauseCount(searchConfigProvider.get().getMaxBooleanClauseCount());
            return new SearchExpressionQueryBuilder(
                    indexDocRef,
                    indexFieldCache,
                    wordListProvider,
                    dateTimeSettings);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
