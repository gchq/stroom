/*
 * Copyright 2026 Crown Copyright
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

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.SearchRequest;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAnalyticRuleSearchRequestHelper {

    private static final DocRef EVENTS = new DocRef("Index", "events", "events");
    private static final Query PLAIN_QUERY = Query.builder().dataSource(EVENTS).build();
    private static final Query JOINED_QUERY = Query.builder()
            .dataSource(EVENTS)
            .joins(List.of(new QueryJoin(
                    JoinType.LEFT,
                    "hr",
                    Query.builder().dataSource(new DocRef("DataConnection", "hr", "hr")).build(),
                    null,
                    List.of(new JoinKey("${UserId}", "${hr.Username}")),
                    null)))
            .build();

    @Test
    void ruleWithoutJoinsIsCreated() {
        final SearchRequest searchRequest = createHelper(PLAIN_QUERY).create(rule(AnalyticProcessType.STREAMING));

        assertThat(searchRequest.getQuery()).isEqualTo(PLAIN_QUERY);
    }

    @Test
    void streamingRuleWithJoinsIsRefused() {
        assertThatThrownBy(() -> createHelper(JOINED_QUERY).create(rule(AnalyticProcessType.STREAMING)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("A streaming rule can't have joins");
    }

    @Test
    void tableBuilderRuleWithAnAliasIsRefused() {
        final Query aliased = PLAIN_QUERY.copy().alias("e").build();

        assertThatThrownBy(() -> createHelper(aliased).create(rule(AnalyticProcessType.TABLE_BUILDER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("A table builder rule can't have joins");
    }

    @Test
    void scheduledQueryRuleWithJoinsIsCreated() {
        final SearchRequest searchRequest = createHelper(JOINED_QUERY)
                .create(rule(AnalyticProcessType.SCHEDULED_QUERY));

        assertThat(searchRequest.getQuery()).isEqualTo(JOINED_QUERY);
    }

    private static AnalyticRuleDoc rule(final AnalyticProcessType processType) {
        return AnalyticRuleDoc.builder()
                .uuid("rule")
                .name("rule")
                .query("from events select UserId")
                .analyticProcessType(processType)
                .build();
    }

    private static AnalyticRuleSearchRequestHelper createHelper(final Query query) {
        final SearchRequestFactory searchRequestFactory = Mockito.mock(SearchRequestFactory.class);
        Mockito.when(searchRequestFactory.create(Mockito.anyString(), Mockito.any(), Mockito.any()))
                .thenReturn(new SearchRequest(null, null, query, null, null, false));
        final ExpressionContextFactory expressionContextFactory = Mockito.mock(ExpressionContextFactory.class);
        Mockito.when(expressionContextFactory.createContext(Mockito.any()))
                .thenReturn(ExpressionContext.builder().build());
        return new AnalyticRuleSearchRequestHelper(searchRequestFactory, expressionContextFactory);
    }
}
