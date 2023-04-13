package stroom.search.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.DateTimeSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

public class SearchExpressionQueryBuilderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchExpressionQueryBuilderFactory.class);

    private final WordListProvider wordListProvider;
    private final Provider<SearchConfig> searchConfigProvider;

    @Inject
    public SearchExpressionQueryBuilderFactory(final WordListProvider wordListProvider,
                                               final Provider<SearchConfig> searchConfigProvider) {
        this.wordListProvider = wordListProvider;
        this.searchConfigProvider = searchConfigProvider;
    }

    public SearchExpressionQueryBuilder create(final IndexFieldsMap indexFieldsMap,
                                               final DateTimeSettings dateTimeSettings,
                                               final long nowEpochMilli) {
        try {
            return new SearchExpressionQueryBuilder(
                    wordListProvider,
                    indexFieldsMap,
                    searchConfigProvider.get().getMaxBooleanClauseCount(),
                    dateTimeSettings,
                    nowEpochMilli);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
