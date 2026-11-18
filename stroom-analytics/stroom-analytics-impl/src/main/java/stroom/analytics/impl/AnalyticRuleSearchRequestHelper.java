/*
 * Copyright 2023 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.Locale;

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

    public SearchRequest create(final AbstractAnalyticRuleDoc doc) {
        try {
            // Map the rule query
            final Query sampleQuery = Query.builder().build();
            final QueryKey queryKey = new QueryKey(doc.getUuid() +
                                                   " - " +
                                                   doc.getName());
            final SearchRequest sampleRequest = new SearchRequest(
                    SearchRequestSource.builder().sourceType(SourceType.TABLE_BUILDER_ANALYTIC).build(),
                    queryKey,
                    sampleQuery,
                    null,
                    DateTimeSettings.builder().build(),
                    false);
            final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
            final SearchRequest searchRequest = searchRequestFactory
                    .create(doc.getQuery(), sampleRequest, expressionContext);
            checkNoJoins(doc, searchRequest);
            return searchRequest;
        } catch (final RuntimeException e) {
            LOGGER.debug(() ->
                    LogUtil.message(
                            "Error creating search request for analytic rule - {}",
                            RuleUtil.getRuleIdentity(doc)), e);
            throw e;
        }
    }

    // Streaming and table builder rules run on each event as it is processed, so there is no other
    // source's search to join it to. Scheduled queries run as searches, so they can have joins.
    private static void checkNoJoins(final AbstractAnalyticRuleDoc doc, final SearchRequest searchRequest) {
        final AnalyticProcessType processType = doc.getAnalyticProcessType();
        final boolean runsOnEachEvent = AnalyticProcessType.STREAMING.equals(processType)
                                        || AnalyticProcessType.TABLE_BUILDER.equals(processType);
        if (runsOnEachEvent && searchRequest.getQuery().needsJoinSupport()) {
            throw new IllegalArgumentException("A " + processType.getDisplayValue().toLowerCase(Locale.ROOT)
                                               + " rule can't have joins, an alias or a bracketed query in from, "
                                               + "as it runs on each event as it is processed. Use a scheduled "
                                               + "query rule instead.");
        }
    }
}
