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

package stroom.ai.impl.tools;

import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Param;
import stroom.query.api.QueryKey;
import stroom.query.api.TimeRange;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QuerySearchRequest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultsRequests {

    private static final DocRef INDEX = new DocRef("Index", "index-uuid", "Events");
    private static final TimeRange LAST_DAY = new TimeRange("Last day", "now()-1d", "now()");
    private static final TimeRange LAST_WEEK = new TimeRange("Last week", "now()-1w", "now()");
    private static final ExpressionOperator FEED_ACME = ExpressionOperator.builder()
            .addTerm(ExpressionTerm.equals("Feed", "ACME"))
            .build();

    @Test
    void theSameDashboardSearchHasTheSameHashWhateverItsQueryKey() {
        final DashboardTableContext ran = dashboard(FEED_ACME, List.of(), LAST_DAY, new QueryKey("k1"));
        final DashboardTableContext live = dashboard(FEED_ACME, List.of(), LAST_DAY, null);

        assertThat(ResultsRequests.hash(ran)).isNotNull().isEqualTo(ResultsRequests.hash(live));
        assertThat(ResultsRequests.changes(ran, live)).isEmpty();
    }

    @Test
    void aChangedExpressionParametersOrTimeRangeIsSaid() {
        final DashboardTableContext ran = dashboard(FEED_ACME, List.of(), LAST_DAY, new QueryKey("k1"));

        final ExpressionOperator otherFeed = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.equals("Feed", "OTHER"))
                .build();
        assertThat(ResultsRequests.changes(ran, dashboard(otherFeed, List.of(), LAST_DAY, null)))
                .containsExactly(ResultsRequests.EXPRESSION);
        assertThat(ResultsRequests.changes(ran, dashboard(FEED_ACME, List.of(new Param("user", "bob")), LAST_DAY,
                null))).containsExactly(ResultsRequests.PARAMETERS);
        assertThat(ResultsRequests.changes(ran, dashboard(FEED_ACME, List.of(), LAST_WEEK, null)))
                .containsExactly(ResultsRequests.TIME_RANGE);
        assertThat(ResultsRequests.hash(dashboard(FEED_ACME, List.of(), LAST_WEEK, null)))
                .isNotEqualTo(ResultsRequests.hash(ran));
    }

    @Test
    void aChangedQueryOrTimeRangeIsSaid() {
        final QueryTableContext ran = query("from Events select User", LAST_DAY, new QueryKey("k1"));

        assertThat(ResultsRequests.changes(ran, query("  from Events select User\n", LAST_DAY, null))).isEmpty();
        assertThat(ResultsRequests.changes(ran, query("from Events select User, Host", LAST_DAY, null)))
                .containsExactly(ResultsRequests.QUERY);
        assertThat(ResultsRequests.changes(ran, query("from Events select User", LAST_WEEK, null)))
                .containsExactly(ResultsRequests.TIME_RANGE);
        assertThat(ResultsRequests.queryKey(ran)).isEqualTo(new QueryKey("k1"));
        assertThat(ResultsRequests.timeRange(ran)).isEqualTo(LAST_DAY.toString());
    }

    @Test
    void noRequestHasNoHash() {
        assertThat(ResultsRequests.hash(null)).isNull();
        assertThat(ResultsRequests.changes(null, query("from Events", LAST_DAY, null))).isEmpty();
    }

    private static DashboardTableContext dashboard(final ExpressionOperator expression,
                                                   final List<Param> params,
                                                   final TimeRange timeRange,
                                                   final QueryKey queryKey) {
        return new DashboardTableContext("Dashboard 'D' -> Table 'T'", null, DashboardSearchRequest.builder()
                .queryKey(queryKey)
                .search(Search.builder()
                        .dataSourceRef(INDEX)
                        .expression(expression)
                        .params(params)
                        .timeRange(timeRange)
                        .build())
                .componentResultRequests(List.of())
                .build());
    }

    private static QueryTableContext query(final String query, final TimeRange timeRange, final QueryKey queryKey) {
        return new QueryTableContext("Query 'Q'", null, QuerySearchRequest.builder()
                .query(query)
                .queryKey(queryKey)
                .queryContext(QueryContext.builder().timeRange(timeRange).build())
                .build());
    }
}
