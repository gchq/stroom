package stroom.search.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexFieldsMap;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
                                               final DateTimeSettings dateTimeSettings) {
        try {
            return new SearchExpressionQueryBuilder(
                    wordListProvider,
                    indexFieldsMap,
                    searchConfigProvider.get().getMaxBooleanClauseCount(),
                    dateTimeSettings);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }
}
