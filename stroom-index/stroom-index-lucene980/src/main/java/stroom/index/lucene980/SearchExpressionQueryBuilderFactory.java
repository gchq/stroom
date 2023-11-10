package stroom.index.lucene980;

import stroom.dictionary.api.WordListProvider;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexFieldsMap;
import stroom.search.impl.SearchConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene980.search.IndexSearcher;

import javax.inject.Inject;
import javax.inject.Provider;

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

    public SearchExpressionQueryBuilder create(final IndexFieldsMap indexFieldsMap,
                                               final DateTimeSettings dateTimeSettings) {
        try {
            IndexSearcher.setMaxClauseCount(searchConfigProvider.get().getMaxBooleanClauseCount());
            return new SearchExpressionQueryBuilder(
                    wordListProvider,
                    indexFieldsMap,
                    dateTimeSettings);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
