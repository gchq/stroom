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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The StroomQL the model is shown in its guidance is checked against Stroom's own parser, so the examples it is
/// given can't drift from what Stroom accepts.
class TestStroomQlGuidance {

    private SearchRequestFactory factory;

    @BeforeEach
    void setUp() {
        final DataSourceResolver dataSourceResolver = Mockito.mock(DataSourceResolver.class);
        Mockito.when(dataSourceResolver.resolveDataSourceRef(Mockito.anyString()))
                .thenAnswer(invocation -> new DocRef("View", "view-uuid", invocation.getArgument(0)));
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.useAsReadResult(Mockito.any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        factory = new SearchRequestFactory((keywordGroup, parentTableSettings) -> null, dataSourceResolver,
                () -> criteria -> null, securityContext);
    }

    @Test
    void theExamplesInTheGuidanceAreValidStroomQl() {
        assertThat(ContextAssembler.TOOL_GUIDANCE)
                .contains("`from \"My View\" where EventTime > day() - 1d limit 100 select EventTime, User`")
                .contains("`Text = \"*sky*\"`")
                .contains("`eval Count = count() group by User select User, Count`");

        parse("from \"My View\" where EventTime > day() - 1d limit 100 select EventTime, User");
        parse("from \"TWITTER\" where Text = \"*sky*\" limit 100 select Text");
        parse("from \"My View\" eval Count = count() group by User select User, Count");
        // Each condition the guidance names.
        parse("from v where a != 1 and b >= 2 and c <= 3 and d > 4 and e < 5 select a");
        parse("from v where a in (\"x\", \"y\") or b between 1 and 10 select a");
    }

    @Test
    void whatTheGuidanceWarnsAgainstIsRefused() {
        // limit after select, as the model wrote it.
        assertThatThrownBy(() -> parse("from \"TWITTER\" where Text = \"afghanistan\" select Text limit 1000"))
                .hasMessageContaining("LIMIT");
        // contains and matches aren't conditions.
        assertThatThrownBy(() -> parse("from \"TWITTER\" where Text contains \"afghanistan\" select Text"))
                .hasMessageContaining("condition");
        assertThatThrownBy(() -> parse("from \"TWITTER\" where Text matches \".*a.*\" select Text"))
                .hasMessageContaining("condition");
        // Nor does Stroom's parser accept is null yet (a term needs three tokens), so the guidance leaves it out.
        assertThatThrownBy(() -> parse("from v where a is null select a")).hasMessageContaining("Incomplete term");
        assertThat(ContextAssembler.TOOL_GUIDANCE).doesNotContain("is null");
    }

    @Test
    void aQuotedFieldNameInSelectIsTextSoTheGuidanceSaysNotToQuoteOne() {
        // As the agent wrote it: every row held the field's name.
        assertThat(columnExpressions(parse("from v where a = 1 select \"create.document.description\"")))
                .containsExactly("'create.document.description'");
        // Unquoted, with dots or an @, and with spaces as a parameter, each is the field.
        assertThat(columnExpressions(parse("from v where a = 1 select create.document.description, @timestamp, "
                                           + "${Create Time}")))
                .containsExactly("${create.document.description}", "${@timestamp}", "${Create Time}");
        assertThat(ContextAssembler.TOOL_GUIDANCE)
                .contains("Never quote a field \nname in select or eval".replace("\n", ""))
                .contains("${Create Time}");
    }

    private static List<String> columnExpressions(final SearchRequest request) {
        return request.getResultRequests().getFirst().getMappings().getFirst().getColumns().stream()
                .filter(column -> !Boolean.TRUE.equals(column.isSpecial()))
                .map(Column::getExpression)
                .toList();
    }

    private SearchRequest parse(final String query) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(null, new QueryKey("test"), Query.builder().build(),
                new ArrayList<ResultRequest>(0), dateTimeSettings, false);
        return factory.create(query, in, ExpressionContext.builder().dateTimeSettings(dateTimeSettings).build());
    }
}
