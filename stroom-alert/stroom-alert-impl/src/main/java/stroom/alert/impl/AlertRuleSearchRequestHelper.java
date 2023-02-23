package stroom.alert.impl;

import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;

import javax.inject.Inject;

public class AlertRuleSearchRequestHelper {

    private final DataSourceResolver dataSourceResolver;

    @Inject
    public AlertRuleSearchRequestHelper(final DataSourceResolver dataSourceResolver) {
        this.dataSourceResolver = dataSourceResolver;
    }

    public SearchRequest create(final AlertRuleDoc alertRule) {
        // Map the rule query
        Query sampleQuery = Query.builder().build();
        SearchRequest sampleRequest = new SearchRequest(
                SearchRequestSource.builder().sourceType(SourceType.ALERT_RULE).build(),
                null,
                sampleQuery,
                null,
                null,
                false);
        final SearchRequest searchRequest = SearchRequestBuilder.create(alertRule.getQuery(), sampleRequest);
        return dataSourceResolver.resolveDataSource(searchRequest);
    }
}
