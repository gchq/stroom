package stroom.search;

import stroom.annotation.api.AnnotationFields;
import stroom.dashboard.impl.Parser;
import stroom.index.shared.IndexConstants;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestParser {

    @Test
    void test() throws Exception {
        String queryString =
                "'Test Index'\n" +
                        "| where UserId = user5 and Description = e0567\n" +
                        "| and EventTime <= 2000-01-01T00:00:00.000Z\n" +
                        "| and EventTime >= 2016-01-02T00:00:00.000Z\n" +
                        "| table \"Stream Id\", \"Event Id\", \"Event Time\", \"Status\"\n";


        final List<ResultRequest> resultRequests = new ArrayList<>(0);

//        for (final String componentId : componentIds) {
//            final TableSettings tableSettings = tableSettingsCreator.apply(extractValues);
//
//            final ResultRequest tableResultRequest = new ResultRequest(componentId,
//                    Collections.singletonList(tableSettings),
//                    null,
//                    null,
//                    ResultRequest.ResultStyle.TABLE,
//                    Fetch.CHANGES);
//            resultRequests.add(tableResultRequest);
//        }

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final Query query = Query.builder().build();
        SearchRequest searchRequest = new SearchRequest(queryKey,
                query,
                resultRequests,
                DateTimeSettings.builder().build(),
                false);
        searchRequest = new Parser().parse(queryString, searchRequest);

        final ObjectMapper mapper = createMapper(true);
        System.out.println(mapper.writeValueAsString(searchRequest));
    }

    private static ObjectMapper createMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

}
