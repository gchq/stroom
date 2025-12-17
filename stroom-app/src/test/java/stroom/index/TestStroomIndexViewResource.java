/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index;

import stroom.app.App;
import stroom.config.app.Config;
import stroom.docref.DocRef;
import stroom.index.shared.IndexResource;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResourcePaths;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.dropwizard.testing.junit5.DropwizardAppExtension;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not currently a test. It is a way of exercising the query api, i.e. it is support for manual testing.
 */
//@ExtendWith(DropwizardExtensionsSupport.class)
class TestStroomIndexViewResource {

    // local.yml is not in source control and is created using local.yml.sh
    public static final DropwizardAppExtension<Config> RULE = new DropwizardAppExtension<>(App.class, "../local.yml");

    public static final String SEARCH_TARGET = ResourcePaths
            .builder()
            .addPathPart("http://localhost:8080")
            .addPathPart(ResourcePaths.ROOT_PATH)
            .addPathPart(ResourcePaths.API_ROOT_PATH)
            .addPathPart(IndexResource.BASE_PATH)
            .addPathPart("/search")
            .build();

    private String jwtToken;

    private static SearchRequest getSearchRequest() {
        final QueryKey queryKey = new QueryKey("Some UUID");
        final Query query = Query.builder()
                .dataSource(new DocRef("docRefType", "docRefUuid", "docRefName"))
                .expression(ExpressionOperator.builder()
                        .addTerm("field1", ExpressionTerm.Condition.EQUALS, "value1")
                        .addTerm("field2", ExpressionTerm.Condition.BETWEEN, "value2")
                        .build())
                .build();

        final List<ResultRequest> resultRequestList = new ArrayList<>();
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().build();
        final SearchRequest searchRequest = new SearchRequest(
                null,
                queryKey,
                query,
                resultRequestList,
                dateTimeSettings,
                false);
        return searchRequest;
    }

    @Disabled
    // if this is re-enabled then un-comment the DropwizardExtensionSupport class extension above, else test takes
    // ages to run no tests
    @Test
    void testSavedFromFile() throws IOException {
        // Given
        final String searchRequestJson = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/searchRequest.json")));
        final SearchRequest searchRequest = JsonUtil.readValue(searchRequestJson, SearchRequest.class);
        final Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        final Response response = client
                .target(SEARCH_TARGET)
                .request()
                .header("Authorization", "Bearer " + jwtToken)
                .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
                .post(Entity.json(searchRequest));
        final SearchResponse searchResponse = response.readEntity(SearchResponse.class);

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(searchResponse.getResults().size()).isEqualTo(5);

        System.out.println(response.toString());
    }

    @Disabled
    // if this is re-enabled then un-comment the DropwizardExtensionSupport class extension above, else test takes
    // ages to run no tests
    @Test
    void test() throws JsonProcessingException {
        // Given
        final SearchRequest searchRequest = getSearchRequest();

        // When
        final Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));
        final Response response = client
                .target(SEARCH_TARGET)
                .request()
                .header("Authorization", "Bearer " + jwtToken)
                .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
                .post(Entity.json(searchRequest));

        // Then
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        System.out.println(response.toString());
    }
}
