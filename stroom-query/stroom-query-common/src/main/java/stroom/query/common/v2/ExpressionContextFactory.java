package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;

import javax.inject.Inject;
import javax.inject.Provider;

public class ExpressionContextFactory {

    private final Provider<AnalyticResultStoreConfig> analyticResultStoreConfigProvider;
    private final Provider<SearchResultStoreConfig> searchResultStoreConfigProvider;

    public ExpressionContextFactory() {
        this.analyticResultStoreConfigProvider = AnalyticResultStoreConfig::new;
        this.searchResultStoreConfigProvider = SearchResultStoreConfig::new;
    }

    @Inject
    public ExpressionContextFactory(final Provider<AnalyticResultStoreConfig> analyticResultStoreConfigProvider,
                                    final Provider<SearchResultStoreConfig> searchResultStoreConfigProvider) {
        this.analyticResultStoreConfigProvider = analyticResultStoreConfigProvider;
        this.searchResultStoreConfigProvider = searchResultStoreConfigProvider;
    }

    public ExpressionContext createContext(final SearchRequest searchRequest) {
        return createContext(searchRequest.getSearchRequestSource(), searchRequest.getDateTimeSettings());
    }

    public ExpressionContext createContext(final SearchRequestSource searchRequestSource,
                                           DateTimeSettings dateTimeSettings) {
        final int maxStringLength = getMaxStringLength(searchRequestSource);

        if (dateTimeSettings == null) {
            dateTimeSettings = DateTimeSettings.builder().build();
        } else if (dateTimeSettings.getReferenceTime() == null) {
            // Ensure we have a reference time
            dateTimeSettings = dateTimeSettings.copy()
                    .referenceTime(System.currentTimeMillis())
                    .build();
        }

        return ExpressionContext.builder()
                .maxStringLength(maxStringLength)
                .dateTimeSettings(dateTimeSettings)
                .build();
    }

    public int getMaxStringLength(final SearchRequestSource searchRequestSource) {
        if (searchRequestSource == null) {
            return searchResultStoreConfigProvider.get().getMaxStringFieldLength();
        }

        switch (searchRequestSource.getSourceType()) {
            case SCHEDULED_QUERY_ANALYTIC, TABLE_BUILDER_ANALYTIC -> {
                return analyticResultStoreConfigProvider.get().getMaxStringFieldLength();
            }
            default -> {
                return searchResultStoreConfigProvider.get().getMaxStringFieldLength();
            }
        }
    }
}
