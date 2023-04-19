package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;

import javax.inject.Inject;

public class AnalyticRuleSearchRequestHelper {

    private final DataSourceResolver dataSourceResolver;

    @Inject
    public AnalyticRuleSearchRequestHelper(final DataSourceResolver dataSourceResolver) {
        this.dataSourceResolver = dataSourceResolver;
    }

    public SearchRequest create(final AnalyticRuleDoc alertRule) {
        // Map the rule query
        Query sampleQuery = Query.builder().build();
        SearchRequest sampleRequest = new SearchRequest(
                SearchRequestSource.builder().sourceType(SourceType.ANALYTIC_RULE).build(),
                alertRule.getQueryKey(),
                sampleQuery,
                null,
                null,
                false);
        final SearchRequest searchRequest = SearchRequestBuilder.create(alertRule.getQuery(), sampleRequest);
        return dataSourceResolver.resolveDataSource(searchRequest);
    }
}
