/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.StateFetcher;
import stroom.query.language.functions.ValNull;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ExpressionContextFactory {

    private final Provider<AnalyticResultStoreConfig> analyticResultStoreConfigProvider;
    private final Provider<SearchResultStoreConfig> searchResultStoreConfigProvider;
    private final Provider<StateFetcher> stateFetcherProvider;

    public ExpressionContextFactory() {
        this.analyticResultStoreConfigProvider = AnalyticResultStoreConfig::new;
        this.searchResultStoreConfigProvider = SearchResultStoreConfig::new;
        stateFetcherProvider = () -> (StateFetcher) (map, key, effectiveTimeMs) -> ValNull.INSTANCE;
    }

    @Inject
    public ExpressionContextFactory(final Provider<AnalyticResultStoreConfig> analyticResultStoreConfigProvider,
                                    final Provider<SearchResultStoreConfig> searchResultStoreConfigProvider,
                                    final Provider<StateFetcher> stateFetcherProvider) {
        this.analyticResultStoreConfigProvider = analyticResultStoreConfigProvider;
        this.searchResultStoreConfigProvider = searchResultStoreConfigProvider;
        this.stateFetcherProvider = stateFetcherProvider;
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
                .stateFetcher(stateFetcherProvider.get())
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
