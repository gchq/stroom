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

package stroom.query.language;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSearchRequestFactory2 {

    @Test
    void testReuse() {
        final List<ResultRequest> resultRequests = new ArrayList<>(0);
        final QueryKey queryKey = new QueryKey("test");
        final Query query = Query.builder().build();
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        SearchRequest searchRequest = new SearchRequest(
                null,
                queryKey,
                query,
                resultRequests,
                dateTimeSettings,
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        final String input = """
                from test
                where field1="blah" and field2="blah"
                eval EventTime=floorMinute(EventTime)
                select EventTime, Field1, Field2
                """;
        final SearchRequestFactory searchRequestFactory = new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> null,
                MockSecurityContext.getInstance());
        for (int i = 0; i < 2; i++) {
            searchRequest = searchRequestFactory.create(input, searchRequest, expressionContext);
            final String out = JsonUtil.writeValueAsString(searchRequest);
            assertThat(out).contains("\"floorMinute(${EventTime})\"");
            System.out.println(out);
        }
    }

    @Test
    void testSelectStar() {
        final List<ResultRequest> resultRequests = new ArrayList<>(0);
        final QueryKey queryKey = new QueryKey("test");
        final Query query = Query.builder().build();
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        SearchRequest searchRequest = new SearchRequest(
                null,
                queryKey,
                query,
                resultRequests,
                dateTimeSettings,
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        final String input = """
                from test
                where field1="blah" and field2="blah"
                eval EventTime=floorMinute(EventTime)
                select *
                """;

        final ResultPage<QueryField> resultPage = new ResultPage<>(List.of(
                QueryField.createDate("EventTime"),
                QueryField.createText("Field1"),
                QueryField.createText("Field2")));
        final SearchRequestFactory searchRequestFactory = new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> resultPage,
                MockSecurityContext.getInstance());
        final Map<String, String> expressionMap = new HashMap<>();
        searchRequest = searchRequestFactory.create(input, searchRequest, expressionContext);
        searchRequest.getResultRequests().forEach(rr ->
                rr.getMappings().forEach(mapping ->
                        mapping.getColumns().forEach(col ->
                                expressionMap.put(col.getName(), col.getExpression()))));
        assertThat(expressionMap).containsKey("EventTime");
        assertThat(expressionMap).containsKey("Field1");
        assertThat(expressionMap).containsKey("Field2");
        assertThat(expressionMap.get("EventTime")).isEqualTo("floorMinute(${EventTime})");
        assertThat(expressionMap.get("Field1")).isEqualTo("${Field1}");
        assertThat(expressionMap.get("Field2")).isEqualTo("${Field2}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "now()-1d2h",
            "2024-01-01T00:00:00Z",
            "2024-01-01T00:00:00+01:00",
            "2024-01-01T00:00:00.000+0100",
            "2024-01-01T00:00:00Z-1d"})
    void testDateTerms(final String value) {
        final SearchRequest searchRequest = create("from test where EventTime > " + value + " select EventTime");

        final ExpressionTerm term = (ExpressionTerm) searchRequest.getQuery().getExpression().getChildren().getFirst();
        assertThat(term.getValue()).isEqualTo(value);
        assertThat(DateExpressionParser.parse(term.getValue(), searchRequest.getDateTimeSettings())).isPresent();
    }

    @Test
    void testDateLiteralsInExpressions() {
        final SearchRequest searchRequest = create("""
                from test
                eval Late = EventTime > 2024-07-01T09:00:00+01:00
                select Late""");

        final String expression = searchRequest.getResultRequests().getFirst()
                .getMappings().getFirst()
                .getColumns().getFirst()
                .getExpression();
        // The columns are compiled again from this text.
        assertThat(expression).contains("2024-07-01T08:00:00.000Z");
    }

    private static SearchRequest create(final String input) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        return new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> null,
                MockSecurityContext.getInstance())
                .create(
                        input,
                        new SearchRequest(
                                null,
                                new QueryKey("test"),
                                Query.builder().build(),
                                new ArrayList<>(0),
                                dateTimeSettings,
                                false),
                        ExpressionContext.builder().dateTimeSettings(dateTimeSettings).maxStringLength(100).build());
    }
}
