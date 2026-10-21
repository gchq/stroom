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

package stroom.search.elastic.indexing;

import stroom.pipeline.errorhandler.LoggedException;
import stroom.search.elastic.ElasticWireMock;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Tests [ElasticIndexingFilter] with the real Elasticsearch Java client, against a WireMock server standing
/// in for an Elasticsearch cluster, so that the HTTP requests the filter makes can be checked.
class TestElasticIndexingFilterWireMock {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String INDEX_NAME = "test-index";
    private static final long STREAM_ID = ElasticIndexingFilterTestHarness.STREAM_ID;
    private static final int RETRY_COUNT = 2;
    private static final String BULK_PATH = "/_bulk";
    private static final String SEARCH_PATH_REGEX = "/events-(\\*|%2A)/_search";
    private static final String RETRY_SCENARIO = "retry";
    private static final String RETRIED_STATE = "retried";

    @RegisterExtension
    static final WireMockExtension ELASTIC = ElasticWireMock.createExtension();

    private ElasticsearchClient elasticClient;
    private ElasticIndexingFilterTestHarness harness;

    @BeforeEach
    void setUp() {
        elasticClient = ElasticWireMock.createClient(ELASTIC);
        harness = new ElasticIndexingFilterTestHarness(List.of(ELASTIC.baseUrl()));
        harness.setReprocess(false);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (elasticClient != null) {
            elasticClient.close();
        }
    }

    @Test
    void testTooManyRequestsOnlyRetriedByFilter() {
        ELASTIC.stubFor(createBulk().willReturn(errorResponse(429, "es_rejected_execution_exception")));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        ELASTIC.verify(RETRY_COUNT + 1, createBulkRequests());
    }

    private ElasticIndexingFilter createFilter(final String indexName) {
        return harness.createFilter(
                elasticClient,
                ElasticIndexingFilterTestHarness.indexingConfig(10, RETRY_COUNT),
                indexName);
    }

    private static MappingBuilder createBulk() {
        return WireMock.post(WireMock.urlPathEqualTo(BULK_PATH))
                .withRequestBody(WireMock.containing("\"create\""));
    }

    private static MappingBuilder deleteBulk() {
        return WireMock.post(WireMock.urlPathEqualTo(BULK_PATH))
                .withRequestBody(WireMock.containing("\"delete\""));
    }

    private static RequestPatternBuilder createBulkRequests() {
        return WireMock.postRequestedFor(WireMock.urlPathEqualTo(BULK_PATH))
                .withRequestBody(WireMock.containing("\"create\""));
    }

    private static RequestPatternBuilder deleteBulkRequests() {
        return WireMock.postRequestedFor(WireMock.urlPathEqualTo(BULK_PATH))
                .withRequestBody(WireMock.containing("\"delete\""));
    }

    private static MappingBuilder search() {
        return WireMock.post(WireMock.urlPathMatching(SEARCH_PATH_REGEX));
    }

    private static MappingBuilder deleteByQuery() {
        return WireMock.post(WireMock.urlPathMatching("/[^/]+/_delete_by_query"));
    }

    private static ResponseDefinitionBuilder errorResponse(final int status, final String type) {
        return ElasticWireMock.elasticResponse(status, """
                {"error":{"root_cause":[{"type":"%s","reason":"Failure"}],"type":"%s","reason":"Failure"},
                 "status":%d}""".formatted(type, type, status));
    }

    private static ResponseDefinitionBuilder bulkSuccess(final int count) {
        final StringBuilder items = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"create\":{\"_index\":\"test-index\",\"_id\":\"id-")
                    .append(i)
                    .append("\",\"status\":201}}");
        }
        return ElasticWireMock.elasticResponse(200, "{\"took\":3,\"errors\":false,\"items\":[" + items + "]}");
    }

    private static ResponseDefinitionBuilder partiallyRejected() {
        return ElasticWireMock.elasticResponse(200, """
                {"took":3,"errors":true,"items":[
                  {"create":{"_index":"test-index","_id":"id-0","status":201}},
                  {"create":{"_index":"test-index","status":429,
                    "error":{"type":"es_rejected_execution_exception","reason":"rejected execution"}}}
                ]}""");
    }

    private static ResponseDefinitionBuilder searchResponse(final String... indexNames) {
        final StringBuilder buckets = new StringBuilder();
        for (final String indexName : indexNames) {
            if (!buckets.isEmpty()) {
                buckets.append(',');
            }
            buckets.append("{\"key\":{\"index_name\":\"").append(indexName).append("\"},\"doc_count\":1}");
        }
        final String afterKey = indexNames.length > 0
                ? "\"after_key\":{\"index_name\":\"" + indexNames[indexNames.length - 1] + "\"},"
                : "";
        // The client asks for typed keys, so the aggregation name is prefixed with its type
        return ElasticWireMock.elasticResponse(200, """
                {"took":1,"timed_out":false,
                 "_shards":{"total":1,"successful":1,"skipped":0,"failed":0},
                 "hits":{"total":{"value":0,"relation":"eq"},"max_score":null,"hits":[]},
                 "aggregations":{"composite#indices":{%s"buckets":[%s]}}}"""
                .formatted(afterKey, buckets));
    }

    private static List<JsonNode> ndjsonLines(final LoggedRequest request) {
        return request.getBodyAsString().lines()
                .filter(line -> !line.isBlank())
                .map(JSON_MAPPER::readTree)
                .toList();
    }

    private static JsonNode json(final String json) {
        return JSON_MAPPER.readTree(json);
    }

    private static String jsonXml(final String content) {
        return ElasticIndexingFilterTestHarness.jsonXml(content);
    }

    private static String documents(final int count) {
        return ElasticIndexingFilterTestHarness.documents(count);
    }
}
