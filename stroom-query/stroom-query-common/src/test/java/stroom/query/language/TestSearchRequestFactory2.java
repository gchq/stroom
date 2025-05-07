package stroom.query.language;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;

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
                MockDocResolver.getInstance(),
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
                MockDocResolver.getInstance(),
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
}
