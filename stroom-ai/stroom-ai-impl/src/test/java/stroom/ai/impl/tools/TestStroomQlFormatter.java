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

import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomQlFormatter {

    // As the agent proposed it, on one line.
    private static final String AFGHANISTAN = "from \"TWITTER-ECS-v1\" where create.document.description = "
                                              + "\"*afghanistan*\" or create.document.description = "
                                              + "\"*Afghanistan*\" or create.document.description = "
                                              + "\"*AFGHANISTAN*\" sort by @timestamp select @timestamp, user.name, "
                                              + "create.document.description, create.document.id, event.code, "
                                              + "StreamId, EventId";

    @Test
    void eachClauseConditionAndColumnOfALongQueryHasItsOwnLine() {
        assertThat(StroomQlFormatter.format(AFGHANISTAN)).isEqualTo("""
                from "TWITTER-ECS-v1"
                where create.document.description = "*afghanistan*"
                    or create.document.description = "*Afghanistan*"
                    or create.document.description = "*AFGHANISTAN*"
                sort by @timestamp
                select
                    @timestamp,
                    user.name,
                    create.document.description,
                    create.document.id,
                    event.code,
                    StreamId,
                    EventId""");
    }

    @Test
    void theFormattedQueryMeansWhatTheOriginalDid() {
        final SearchRequest original = parse(AFGHANISTAN);
        final SearchRequest formatted = parse(StroomQlFormatter.format(AFGHANISTAN));

        assertThat(formatted.getQuery().getExpression()).isEqualTo(original.getQuery().getExpression());
        assertThat(formatted.getResultRequests()).isEqualTo(original.getResultRequests());
    }

    @Test
    void bracketsBetweenAndFunctionsAndStringsStayTogether() {
        assertThat(StroomQlFormatter.format("from v where (a = 1 or b = 2) and c between 1 and 10 and d = \"x "
                                            + "and y\" eval n = count() group by a select a, n"))
                .isEqualTo("""
                        from v
                        where (a = 1 or b = 2)
                            and c between 1 and 10
                            and d = "x and y"
                        eval n = count()
                        group by a
                        select a, n""");
    }

    @Test
    void aQueryLaidOutAlreadyOrWithCommentsIsLeftAlone() {
        final String laidOut = "from v\nwhere a = 1 select a";
        assertThat(StroomQlFormatter.format(laidOut)).isSameAs(laidOut);
        final String commented = "from v where a = 1 /* why */ select a";
        assertThat(StroomQlFormatter.format(commented)).isSameAs(commented);
        assertThat(StroomQlFormatter.format(null)).isNull();
        assertThat(StroomQlFormatter.format("  ")).isEqualTo("  ");
    }

    private static SearchRequest parse(final String query) {
        final DataSourceResolver dataSourceResolver = Mockito.mock(DataSourceResolver.class);
        Mockito.when(dataSourceResolver.resolveDataSourceRef(Mockito.anyString()))
                .thenAnswer(invocation -> new DocRef("ElasticIndex", "uuid", invocation.getArgument(0)));
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.useAsReadResult(Mockito.any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        return new SearchRequestFactory((keywordGroup, parentTableSettings) -> null, dataSourceResolver,
                () -> criteria -> null, securityContext)
                .create(query, new SearchRequest(null, new QueryKey("test"), Query.builder().build(),
                                new ArrayList<ResultRequest>(0), dateTimeSettings, false),
                        ExpressionContext.builder().dateTimeSettings(dateTimeSettings).build());
    }
}
