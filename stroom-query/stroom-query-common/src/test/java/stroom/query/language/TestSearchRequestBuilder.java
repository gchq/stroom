package stroom.query.language;

import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestSearchRequestBuilder extends AbstractQueryTest {

    private static final ObjectMapper OBJECT_MAPPER = createMapper(true);

    @Override
    String getTestDirName() {
        return "TestSearchRequestBuilder";
    }

    @Override
    String convert(final String input) {
        final List<ResultRequest> resultRequests = new ArrayList<>(0);
        final QueryKey queryKey = new QueryKey("test");
        final Query query = Query.builder().build();
        SearchRequest searchRequest = new SearchRequest(queryKey,
                query,
                resultRequests,
                DateTimeSettings.builder().build(),
                false);
        searchRequest = new SearchRequestBuilder().create(input, searchRequest);

        try {
            return OBJECT_MAPPER.writeValueAsString(searchRequest);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

}
