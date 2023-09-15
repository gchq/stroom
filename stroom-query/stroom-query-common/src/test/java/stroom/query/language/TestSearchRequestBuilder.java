package stroom.query.language;

import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.util.json.JsonUtil;

import java.util.ArrayList;
import java.util.List;

public class TestSearchRequestBuilder extends AbstractQueryTest {

    @Override
    String getTestDirName() {
        return "TestSearchRequestBuilder";
    }

    @Override
    String convert(final String input) {
        try {
            final List<ResultRequest> resultRequests = new ArrayList<>(0);
            final QueryKey queryKey = new QueryKey("test");
            final Query query = Query.builder().build();
            SearchRequest searchRequest = new SearchRequest(
                    null,
                    queryKey,
                    query,
                    resultRequests,
                    DateTimeSettings.builder().referenceTime(0L).build(),
                    false);
            searchRequest = new SearchRequestBuilder(null).create(input, searchRequest);
            return JsonUtil.writeValueAsString(searchRequest);

        } catch (final RuntimeException e) {
            return e.toString();
        }
    }
}
