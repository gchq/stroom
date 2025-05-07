package stroom.query.language;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.token.AbstractQueryTest;
import stroom.security.mock.MockSecurityContext;
import stroom.util.json.JsonUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestSearchRequestFactory extends AbstractQueryTest {

    @Override
    protected Path getTestDir() {
        final Path dir = Paths.get("../stroom-query-common/src/test/resources/TestSearchRequestFactory");
        if (!Files.isDirectory(dir)) {
            throw new RuntimeException("Test data directory not found: " + dir.toAbsolutePath());
        }
        return dir;
    }

    @Override
    protected String convert(final String input) {
        try {
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
            searchRequest = new SearchRequestFactory(
                    (keywordGroup, parentTableSettings) -> null,
                    MockDocResolver.getInstance(),
                    () -> criteria -> null,
                    MockSecurityContext.getInstance())
                    .create(input, searchRequest, expressionContext);
            return JsonUtil.writeValueAsString(searchRequest);

        } catch (final RuntimeException e) {
            return e.toString();
        }
    }
}
