package stroom.query.common.v2;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.ValNull;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ExpressionContextFactory {

    private final Provider<AnalyticResultStoreConfig> analyticResultStoreConfigProvider;
    private final Provider<SearchResultStoreConfig> searchResultStoreConfigProvider;
    private final Provider<StateProvider> stateProviderProvider;

    public ExpressionContextFactory() {
        this.analyticResultStoreConfigProvider = AnalyticResultStoreConfig::new;
        this.searchResultStoreConfigProvider = SearchResultStoreConfig::new;
        stateProviderProvider = () -> (StateProvider) (map, key, effectiveTimeMs) -> ValNull.INSTANCE;
    }

    @Inject
    public ExpressionContextFactory(final Provider<AnalyticResultStoreConfig> analyticResultStoreConfigProvider,
                                    final Provider<SearchResultStoreConfig> searchResultStoreConfigProvider,
                                    final Provider<StateProvider> stateProviderProvider) {
        this.analyticResultStoreConfigProvider = analyticResultStoreConfigProvider;
        this.searchResultStoreConfigProvider = searchResultStoreConfigProvider;
        this.stateProviderProvider = stateProviderProvider;
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
                .stateProvider(stateProviderProvider.get())
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
