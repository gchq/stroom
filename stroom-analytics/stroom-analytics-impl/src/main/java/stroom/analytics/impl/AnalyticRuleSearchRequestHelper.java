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
import stroom.query.language.SearchRequestFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

public class AnalyticRuleSearchRequestHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticRuleSearchRequestHelper.class);

    private final SearchRequestFactory searchRequestFactory;
    private final ExpressionContextFactory expressionContextFactory;

    @Inject
    public AnalyticRuleSearchRequestHelper(final SearchRequestFactory searchRequestFactory,
                                           final ExpressionContextFactory expressionContextFactory) {
        this.searchRequestFactory = searchRequestFactory;
        this.expressionContextFactory = expressionContextFactory;
    }

    public SearchRequest create(final AnalyticRuleDoc analyticRuleDoc) {
        try {
            // Map the rule query
            Query sampleQuery = Query.builder().build();
            final QueryKey queryKey = new QueryKey(analyticRuleDoc.getUuid() +
                    " - " +
                    analyticRuleDoc.getName());
            SearchRequest sampleRequest = new SearchRequest(
                    SearchRequestSource.builder().sourceType(SourceType.TABLE_BUILDER_ANALYTIC).build(),
                    queryKey,
                    sampleQuery,
                    null,
                    DateTimeSettings.builder().build(),
                    false);
            final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
            return searchRequestFactory
                    .create(analyticRuleDoc.getQuery(), sampleRequest, expressionContext);
        } catch (final RuntimeException e) {
            LOGGER.debug(() ->
                    "Error creating search request for analytic rule - "
                            + AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc), e);
            throw e;
        }
    }
}
