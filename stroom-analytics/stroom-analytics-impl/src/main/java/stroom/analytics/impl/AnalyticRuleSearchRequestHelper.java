package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.expression.api.DateTimeSettings;
import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.language.SearchRequestBuilder;

import javax.inject.Inject;

public class AnalyticRuleSearchRequestHelper {

    private final SearchRequestBuilder searchRequestBuilder;
    private final ExpressionContextFactory expressionContextFactory;

    @Inject
    public AnalyticRuleSearchRequestHelper(final SearchRequestBuilder searchRequestBuilder,
                                           final ExpressionContextFactory expressionContextFactory) {
        this.searchRequestBuilder = searchRequestBuilder;
        this.expressionContextFactory = expressionContextFactory;
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
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        return searchRequestBuilder
                .create(alertRule.getQuery(), sampleRequest, expressionContext);
    }
}
