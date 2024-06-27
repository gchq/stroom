package stroom.query.language;

import stroom.expression.api.DateTimeSettings;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
                new MockDocResolver());
        for (int i = 0; i < 2; i++) {
            searchRequest = searchRequestFactory.create(input, searchRequest, expressionContext);
            final String out = JsonUtil.writeValueAsString(searchRequest);
            assertThat(out).contains("\"floorMinute(${EventTime})\"");
            System.out.println(out);
        }
    }
}
