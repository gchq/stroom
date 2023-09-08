package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;

import javax.inject.Inject;

public class AnalyticRuleSearchRequestHelper {

    private final DataSourceResolver dataSourceResolver;
    private final SearchRequestBuilder searchRequestBuilder;

    @Inject
    public AnalyticRuleSearchRequestHelper(final DataSourceResolver dataSourceResolver,
                                           final SearchRequestBuilder searchRequestBuilder) {
        this.dataSourceResolver = dataSourceResolver;
        this.searchRequestBuilder = searchRequestBuilder;
    }

    public SearchRequest create(final AnalyticRuleDoc alertRule) {
        // Map the rule query
        Query sampleQuery = Query.builder().build();
        final QueryKey queryKey = new QueryKey(alertRule.getUuid() +
                " - " +
                alertRule.getName());
        SearchRequest sampleRequest = new SearchRequest(
                SearchRequestSource.builder().sourceType(SourceType.TABLE_BUILDER_ANALYTIC).build(),
                queryKey,
                sampleQuery,
                null,
                DateTimeSettings.builder().build(),
                false);
        final SearchRequest searchRequest = searchRequestBuilder
                .create(alertRule.getQuery(), sampleRequest);
        return dataSourceResolver.resolveDataSource(searchRequest);
    }
}
