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
import stroom.util.shared.Severity;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Unit tests for [ElasticIndexingFilter] that feed it JSON-in-XML SAX events and capture the bulk requests it
/// sends to a mock Elasticsearch client.
class TestElasticIndexingFilter {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final String INDEX_NAME = "test-index";
    private static final int RETRY_COUNT = 2;

    private final List<BulkRequest> bulkRequests = new ArrayList<>();
    private final List<Integer> bulkSizesWhenSent = new ArrayList<>();
    private ElasticsearchClient elasticClient;
    private ElasticIndexingFilterTestHarness harness;

    @BeforeEach
    void setUp() throws IOException {
        harness = new ElasticIndexingFilterTestHarness(List.of("http://localhost:9200"));
        harness.setReprocess(false);
        elasticClient = Mockito.mock(ElasticsearchClient.class);
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenAnswer(invocation -> {
                    final BulkRequest bulkRequest = invocation.getArgument(0);
                    // Null when a test re-stubs the mock
                    if (bulkRequest == null) {
                        return null;
                    }
                    recordBulkRequest(bulkRequest);
                    return successResponse(bulkRequest.operations().size());
                });
    }

    @Test
    void testDocumentWithNoValuesDoesNotCorruptNextDocument() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map><string key="empty"></string></map>
                <map><string key="a">b</string></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(ElasticIndexingFilterTestHarness.documentJsons(bulkRequests))
                .singleElement()
                .satisfies(json -> assertThat(json.strip()).isEqualTo("{\"a\":\"b\"}"));
    }

    @Test
    void testIndexNameVariableSpecifiedTwiceInDocumentRoot() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map>
                  <string key="_year">2023</string>
                  <string key="_year">2024</string>
                  <string key="a">b</string>
                </map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Index variable '_year' specified more than once in document");
        assertThat(bulkRequests).isEmpty();
    }

    @Test
    void testIndexNameVariableInNestedMapIsNotADuplicate() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        harness.process(filter, jsonXml("""
                <map>
                  <map key="nested"><string key="_year">1999</string></map>
                  <array key="list"><map><string key="_year">1998</string></map></array>
                  <string key="_year">2023</string>
                  <string key="a">b</string>
                </map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests.getFirst().operations().getFirst().create().index())
                .isEqualTo("events-2023");
    }

    @Test
    void testDocumentWithOnlyIndexNameVariablesIsSkipped() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        harness.process(filter, jsonXml("""
                <map><string key="_year">2023</string></map>
                <map><string key="_year">2024</string><string key="a">b</string></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests.getFirst().operations())
                .extracting(operation -> operation.create().index())
                .containsExactly("events-2024");
        assertThat(ElasticIndexingFilterTestHarness.documentJsons(bulkRequests))
                .singleElement()
                .satisfies(json -> assertThat(json.strip()).isEqualTo("{\"a\":\"b\"}"));
    }

    @Test
    void testIndexNameVariableOnlyTakenFromDocumentRoot() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        harness.process(filter, jsonXml("""
                <map>
                  <string key="_year">2023</string>
                  <map key="nested"><string key="_year">1999</string></map>
                  <string key="a">b</string>
                </map>"""));

        assertThat(bulkRequests.getFirst().operations().getFirst().create().index())
                .isEqualTo("events-2023");
    }

    @Test
    void testNullClusterLoggedOnce() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setCluster(null);

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Elasticsearch cluster ref has not been set");
    }

    private ElasticIndexingFilter createFilter(final String indexName) {
        return harness.createFilter(
                elasticClient,
                ElasticIndexingFilterTestHarness.indexingConfig(10, RETRY_COUNT),
                indexName);
    }

    private void recordBulkRequest(final BulkRequest bulkRequest) {
        bulkRequests.add(snapshot(bulkRequest));
        bulkSizesWhenSent.add(bulkRequest.operations().size());
    }

    // The request is a view over the filter's list of operations, which is cleared once the batch is sent,
    // so take a copy
    private static BulkRequest snapshot(final BulkRequest bulkRequest) {
        return BulkRequest.of(b -> b
                .operations(new ArrayList<>(bulkRequest.operations()))
                .refresh(bulkRequest.refresh()));
    }

    private void assertDocuments(final String... expectedJsons) {
        final List<JsonNode> actual = ElasticIndexingFilterTestHarness.documentJsons(bulkRequests).stream()
                .map(JSON_MAPPER::readTree)
                .toList();
        final List<JsonNode> expected = List.of(expectedJsons).stream()
                .map(JSON_MAPPER::readTree)
                .toList();
        assertThat(actual).containsExactlyElementsOf(expected);
    }

    private static BulkResponse successResponse(final int count) {
        final List<BulkResponseItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String id = "doc-" + i;
            items.add(BulkResponseItem.of(item -> item
                    .operationType(OperationType.Create)
                    .index(INDEX_NAME)
                    .id(id)
                    .status(201)));
        }
        return BulkResponse.of(b -> b
                .errors(false)
                .took(1L)
                .items(items));
    }

    private static ElasticsearchException elasticsearchException(final int status) {
        final String type = status == 429
                ? "es_rejected_execution_exception"
                : "internal_server_error";
        return new ElasticsearchException("bulk", ErrorResponse.of(e -> e
                .status(status)
                .error(cause -> cause.type(type).reason("Failure with status " + status))));
    }

    private static String jsonXml(final String content) {
        return ElasticIndexingFilterTestHarness.jsonXml(content);
    }

    private static String documents(final int count) {
        return ElasticIndexingFilterTestHarness.documents(count);
    }
}
