package stroom.index;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.assertj.core.api.Assertions;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.servicediscovery.RegisteredService;
import stroom.servicediscovery.ResourcePaths;
import stroom.startup.App;
import stroom.startup.Config;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * This is not currently a test. It is a way of exercising the query api, i.e. it is support for manual testing.
 */
public class TestStroomIndexQueryResource {

    @ClassRule
    public static final DropwizardAppRule<Config> RULE = new DropwizardAppRule<>(App.class, "dev.yml");

    public static final String SEARCH_TARGET = "http://localhost:8080" +
            ResourcePaths.ROOT_PATH +
            ResourcePaths.API_PATH +
            RegisteredService.INDEX_V2.getVersionedPath() +
            "/search";

    private String jwtToken;

    private static SearchRequest getSearchRequest() {
        QueryKey queryKey = new QueryKey("Some UUID");
        Query query = new Query(
                new DocRef("docRefType", "docRefUuid", "docRefName"),
                new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addTerm("field1", ExpressionTerm.Condition.EQUALS, "value1")
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.AND).build())
                        .addTerm("field2", ExpressionTerm.Condition.BETWEEN, "value2")
                        .build()
        );

        List<ResultRequest> resultRequestList = new ArrayList<>();
        String datetimeLocale = "en-gb";
        boolean incremental = false;
        SearchRequest searchRequest = new SearchRequest(queryKey, query, resultRequestList, datetimeLocale, incremental);
        return searchRequest;
    }

    private static ObjectMapper getMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        // Enabling default typing adds type information where it would otherwise be ambiguous, i.e. for abstract classes
//        mapper.enableDefaultTyping();
        return mapper;
    }

    private static String serialiseSearchRequest(SearchRequest searchRequest) throws JsonProcessingException {
        ObjectMapper objectMapper = getMapper(true);
        return objectMapper.writeValueAsString(searchRequest);
    }

    @Ignore
    @Test
    public void testSavedFromFile() throws IOException {
        // Given
        String searchRequestJson = new String(Files.readAllBytes(Paths.get("src/integrationTest/resources/searchRequest.json")));
        ObjectMapper objectMapper = new ObjectMapper();
        SearchRequest searchRequest = objectMapper.readValue(searchRequestJson, SearchRequest.class);
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        Response response = client
                .target(SEARCH_TARGET)
                .request()
                .header("Authorization", "Bearer " + jwtToken)
                .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
                .post(Entity.json(searchRequest));
        SearchResponse searchResponse = response.readEntity(SearchResponse.class);

        // Then
        Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        Assertions.assertThat(searchResponse.getResults().size()).isEqualTo(5);

        System.out.println(response.toString());
    }

    @Ignore
    @Test
    public void test() throws JsonProcessingException {
        // Given
        SearchRequest searchRequest = getSearchRequest();

        // When
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));
        Response response = client
                .target(SEARCH_TARGET)
                .request()
                .header("Authorization", "Bearer " + jwtToken)
                .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
                .post(Entity.json(searchRequest));

        // Then
        Assertions.assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        System.out.println(response.toString());
    }
}
