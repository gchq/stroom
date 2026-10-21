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
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
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
    void testScalarValues() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map>
                  <string key="str">hello</string>
                  <number key="int">42</number>
                  <number key="dec">-1.5e3</number>
                  <boolean key="yes">true</boolean>
                  <boolean key="no">false</boolean>
                  <null key="nothing"/>
                </map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests).hasSize(1);
        assertDocuments("""
                {"str":"hello","int":42,"dec":-1.5e3,"yes":true,"no":false,"nothing":null}""");
    }

    @Test
    void testArraysAndNestedMaps() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map>
                  <array key="tags">
                    <string>a</string>
                    <string>b</string>
                    <number>3</number>
                  </array>
                  <map key="user">
                    <string key="name">jbloggs</string>
                    <map key="address">
                      <string key="town">Bristol</string>
                    </map>
                  </map>
                  <array key="items">
                    <map><number key="id">1</number></map>
                    <map><number key="id">2</number></map>
                  </array>
                </map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertDocuments("""
                {
                  "tags": ["a", "b", 3],
                  "user": {"name": "jbloggs", "address": {"town": "Bristol"}},
                  "items": [{"id": 1}, {"id": 2}]
                }""");
    }

    @Test
    void testEmptyValuesAreOmitted() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map>
                  <string key="emptyString"></string>
                  <number key="emptyNumber"/>
                  <boolean key="emptyBoolean"/>
                  <string key="kept">value</string>
                </map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertDocuments("""
                {"kept":"value"}""");
    }

    @Test
    void testWhitespaceOnlyStringIsKept() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map><string key="spaces">   </string><string key="tab">\t</string></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertDocuments("""
                {"spaces":"   ","tab":"\\t"}""");
    }

    @Test
    void testUnparseableBooleanIsFalse() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map><boolean key="flag">yes</boolean><boolean key="upper">TRUE</boolean></map>"""));

        assertDocuments("""
                {"flag":false,"upper":true}""");
    }

    @Test
    void testUnderscoreFieldsAreNotWritten() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map><string key="_hidden">x</string><string key="visible">y</string></map>"""));

        assertDocuments("""
                {"visible":"y"}""");
    }

    @Test
    void testMultipleDocuments() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map><number key="n">1</number></map>
                <map><number key="n">2</number></map>
                <map><number key="n">3</number></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests).hasSize(1);
        assertDocuments("""
                {"n":1}""", """
                {"n":2}""", """
                {"n":3}""");
        assertThat(bulkRequests.getFirst().operations())
                .allSatisfy(operation -> {
                    assertThat(operation.isCreate()).isTrue();
                    assertThat(operation.create().index()).isEqualTo(INDEX_NAME);
                    assertThat(operation.create().pipeline()).isNull();
                });
    }

    @Test
    void testDocumentsSpanningMultipleParses() {
        // Each record/part of a stream arrives as a separate XML document, but they share the batch
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml("<map><string key=\"part\">one</string></map>"));
        harness.parse(filter, jsonXml("<map><string key=\"part\">two</string></map>"));
        filter.endProcessing();

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests).hasSize(1);
        assertDocuments("""
                {"part":"one"}""", """
                {"part":"two"}""");
    }

    @Test
    void testDocumentWithNoValuesIsSkipped() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml("""
                <map><string key="empty"></string></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests).isEmpty();
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
    void testBatchSizeExactlyReached() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(2);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(2)));
        // The batch is sent as soon as it is full, without waiting for the end of processing
        assertThat(bulkSizesWhenSent).containsExactly(2);
        filter.endProcessing();

        // No empty batch is sent at the end
        assertThat(bulkSizesWhenSent).containsExactly(2);
        assertThat(harness.getErrors()).isEmpty();
    }

    @Test
    void testBatchSizeExceededByOne() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(2);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(3)));
        assertThat(bulkSizesWhenSent).containsExactly(2);
        filter.endProcessing();

        assertThat(bulkSizesWhenSent).containsExactly(2, 1);
        assertDocuments("""
                {"n":0}""", """
                {"n":1}""", """
                {"n":2}""");
    }

    @Test
    void testFinalPartialBatchFlushedAtEnd() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(3);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(7)));
        assertThat(bulkSizesWhenSent).containsExactly(3, 3);
        filter.endProcessing();

        assertThat(bulkSizesWhenSent).containsExactly(3, 3, 1);
        assertThat(ElasticIndexingFilterTestHarness.documentJsons(bulkRequests)).hasSize(7);
    }

    @Test
    void testNoDocumentsSendsNothing() throws IOException {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(""));

        Mockito.verify(elasticClient, Mockito.never()).bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getErrors()).isEmpty();
    }

    @Test
    void testIngestPipelineApplied() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setIngestPipeline("my-pipeline");

        harness.process(filter, jsonXml(documents(2)));

        assertThat(bulkRequests.getFirst().operations())
                .extracting(operation -> operation.create().pipeline())
                .containsExactly("my-pipeline", "my-pipeline");
    }

    @Test
    void testEmptyIngestPipelineIgnored() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setIngestPipeline("");

        harness.process(filter, jsonXml(documents(1)));

        assertThat(bulkRequests.getFirst().operations().getFirst().create().pipeline()).isNull();
    }

    @Test
    void testRefreshAfterEachBatch() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setRefreshAfterEachBatch(true);
        filter.setBatchSize(1);

        harness.process(filter, jsonXml(documents(2)));

        assertThat(bulkRequests)
                .hasSize(2)
                .extracting(BulkRequest::refresh)
                .containsOnly(Refresh.True);
    }

    @Test
    void testNoRefreshByDefault() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(1)));

        assertThat(bulkRequests.getFirst().refresh()).isEqualTo(Refresh.False);
    }

    @Test
    void testIndexNameVariables() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}-{type}");

        harness.process(filter, jsonXml("""
                <map>
                  <number key="_year">2023</number>
                  <string key="type">auth</string>
                  <string key="user">jbloggs</string>
                </map>
                <map>
                  <string key="type">file</string>
                  <number key="_year">2024</number>
                </map>"""));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkRequests.getFirst().operations())
                .extracting(operation -> operation.create().index())
                .containsExactly("events-2023-auth", "events-2024-file");
        // Variables starting with an underscore are only used in the index name
        assertDocuments("""
                {"type":"auth","user":"jbloggs"}""", """
                {"type":"file"}""");
    }

    @Test
    void testIndexNameVariableFromBoolean() {
        final ElasticIndexingFilter filter = createFilter("flags-{flag}");

        harness.process(filter, jsonXml("""
                <map><boolean key="flag">true</boolean></map>"""));

        assertThat(bulkRequests.getFirst().operations().getFirst().create().index())
                .isEqualTo("flags-true");
    }

    @Test
    void testIndexNameVariableMissing() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map><string key="other">x</string></map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .first()
                .isEqualTo("Field '_year' not found in document when building index name with pattern: "
                           + "'events-{_year}'");
        assertThat(bulkRequests).isEmpty();
    }

    @Test
    void testIndexNameVariableNotCarriedOverToNextDocument() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map><string key="_year">2023</string><string key="a">1</string></map>
                <map><string key="a">2</string></map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .first()
                .asString()
                .startsWith("Field '_year' not found in document");
    }

    @Test
    void testIndexNameVariableWithEmptyOrNullValue() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map><string key="_year"></string><null key="_year"/><string key="a">1</string></map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .first()
                .asString()
                .startsWith("Field '_year' not found in document");
    }

    @Test
    void testIndexNameVariableMissingLoggedOnce() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map><string key="other">x</string></map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Field '_year' not found in document");
    }

    @Test
    void testInvalidJsonStructureLoggedAsFatalError() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        // A keyed value inside an array cannot be written as JSON, so the generator throws a JacksonException
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map><array key="tags"><string key="tag">a</string></array></map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Invalid string value 'a' for property 'tag'");
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
    void testIndexNameVariableOnlyInNestedMapIsMissing() {
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("""
                <map>
                  <map key="nested"><string key="_year">1999</string></map>
                  <string key="a">b</string>
                </map>""")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Field '_year' not found in document");
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
    void testIndexNameWithUnclosedBraceIsUsedLiterally() {
        final ElasticIndexingFilter filter = createFilter("events-{year");

        harness.process(filter, jsonXml(documents(1)));

        assertThat(bulkRequests.getFirst().operations().getFirst().create().index())
                .isEqualTo("events-{year");
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
    void testMissingIndexName() {
        final ElasticIndexingFilter filter = createFilter("");

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .contains("Index name has not been set");
    }

    @Test
    void testNullCluster() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setCluster(null);

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .contains("Elasticsearch cluster ref has not been set");
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

    @Test
    void testMissingClusterDocument() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        Mockito.when(harness.getElasticClusterStore().readDocument(harness.getClusterRef())).thenReturn(null);

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getErrors())
                .isNotEmpty()
                .allSatisfy(error -> assertThat(error.severity()).isEqualTo(Severity.FATAL_ERROR));
    }

    @Test
    void testRootMustBeArray() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter,
                "<map xmlns=\"" + ElasticIndexingFilterTestHarness.JSON_NAMESPACE + "\"/>"))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Expected an array at start of document, got 'map' instead");
    }

    @Test
    void testValueDirectlyInOuterArray() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml("<string>x</string>")))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Expected an end array to close the document, got 'string' instead");
    }

    @Test
    void testMaxNestedElementDepth() {
        final ElasticIndexingFilter filter = harness.createFilter(
                elasticClient,
                ElasticIndexingFilterTestHarness.indexingConfig(2, RETRY_COUNT),
                INDEX_NAME);

        filter.startProcessing();
        // Depth 2 is allowed
        harness.parse(filter, jsonXml("<map><map key=\"a\"><string key=\"b\">c</string></map></map>"));
        assertThat(harness.getErrors()).isEmpty();

        assertThatThrownBy(() -> harness.parse(filter, jsonXml(
                "<map><map key=\"a\"><array key=\"b\"><string>c</string></array></map></map>")))
                .isInstanceOf(LoggedException.class);
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Maximum nested element depth of 2 exceeded");
    }

    @Test
    void testOverloadedExceptionIsRetried() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(elasticsearchException(429))
                .thenAnswer(invocation -> {
                    final BulkRequest bulkRequest = invocation.getArgument(0);
                    recordBulkRequest(bulkRequest);
                    return successResponse(bulkRequest.operations().size());
                });
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(2)));

        Mockito.verify(elasticClient, Mockito.times(2)).bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getErrors()).isEmpty();
        assertThat(ElasticIndexingFilterTestHarness.documentJsons(bulkRequests)).hasSize(2);
    }

    @Test
    void testOverloadedExceptionRetriesExhausted() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(elasticsearchException(429));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        // The first attempt plus the configured number of retries
        Mockito.verify(elasticClient, Mockito.times(RETRY_COUNT + 1))
                .bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Indexing failed to complete after " + RETRY_COUNT + " retries: ")
                .contains("es_rejected_execution_exception");
    }

    @Test
    void testRetryCountResetBetweenBatches() throws IOException {
        // Each batch gets the full number of retries, rather than sharing them
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(elasticsearchException(429))
                .thenThrow(elasticsearchException(429))
                .thenReturn(successResponse(1))
                .thenThrow(elasticsearchException(429))
                .thenThrow(elasticsearchException(429))
                .thenReturn(successResponse(1));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(1);

        harness.process(filter, jsonXml(documents(2)));

        Mockito.verify(elasticClient, Mockito.times(6)).bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getErrors()).isEmpty();
    }

    @Test
    void testOverloadedItemsAreRetried() throws IOException {
        // One document was indexed and the other rejected, so the indexed one is deleted before retrying
        final BulkResponse partialResponse = BulkResponse.of(b -> b
                .errors(true)
                .took(1L)
                .items(
                        BulkResponseItem.of(i -> i
                                .operationType(OperationType.Create)
                                .index(INDEX_NAME)
                                .id("doc-1")
                                .status(201)),
                        BulkResponseItem.of(i -> i
                                .operationType(OperationType.Create)
                                .index(INDEX_NAME)
                                .status(429)
                                .error(e -> e.type("es_rejected_execution_exception").reason("busy")))));
        final List<BulkRequest> requests = new ArrayList<>();
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenAnswer(invocation -> {
                    requests.add(snapshot(invocation.getArgument(0)));
                    return switch (requests.size()) {
                        case 1 -> partialResponse;
                        case 2 -> successResponse(1);
                        default -> successResponse(2);
                    };
                });
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(2)));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(requests).hasSize(3);
        final BulkOperation deleteOperation = requests.get(1).operations().getFirst();
        assertThat(requests.get(1).operations()).hasSize(1);
        assertThat(deleteOperation.isDelete()).isTrue();
        assertThat(deleteOperation.delete().index()).isEqualTo(INDEX_NAME);
        assertThat(deleteOperation.delete().id()).isEqualTo("doc-1");
        // The whole batch is resent
        assertThat(requests.get(2).operations()).hasSize(2);
    }

    @Test
    void testPartialFailureIsFatal() throws IOException {
        final BulkResponse partialResponse = BulkResponse.of(b -> b
                .errors(true)
                .took(1L)
                .items(BulkResponseItem.of(i -> i
                        .operationType(OperationType.Create)
                        .index(INDEX_NAME)
                        .status(400)
                        .error(e -> e.type("mapper_parsing_exception").reason("bad field")))));
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class))).thenReturn(partialResponse);
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        // Not retried
        Mockito.verify(elasticClient, Mockito.times(1)).bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Bulk indexing request failed: ")
                .contains("mapper_parsing_exception");
    }

    @Test
    void testElasticsearchExceptionIsFatal() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(elasticsearchException(500));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        Mockito.verify(elasticClient, Mockito.times(1)).bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.severity()).isEqualTo(Severity.FATAL_ERROR);
                    assertThat(error.message()).startsWith("Indexing failed to complete after 0 retries: ");
                    assertThat(error.throwable()).isInstanceOf(ElasticsearchException.class);
                });
    }

    @Test
    void testIOExceptionIsFatal() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(new IOException("Connection refused"));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        Mockito.verify(elasticClient, Mockito.times(1)).bulk(ArgumentMatchers.any(BulkRequest.class));
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Connection refused");
    }

    @Test
    void testLongErrorMessageIsTruncated() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(new IOException("x".repeat(10_000)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .hasSize(4096);
    }

    @Test
    void testFailureWhenBatchFullLoggedOnce() throws IOException {
        // The batch is sent from endElement as soon as it is full, so the error must not be logged again there
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(new IOException("Connection refused"));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(1);

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml(documents(1))))
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Connection refused");
    }

    @Test
    void testBatchDiscardedAfterFailure() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenThrow(new IOException("failed"));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        // A second end does not resend the failed batch
        filter.endProcessing();
        Mockito.verify(elasticClient, Mockito.times(1)).bulk(ArgumentMatchers.any(BulkRequest.class));
    }

    /// Control case: with no failures, a document from each of two stream parts is sent to Elasticsearch.
    @Test
    void testDocumentFromEachStreamPartIsIndexed() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(1);

        filter.startProcessing();
        parsePart(filter, documents(1));
        parsePart(filter, documents(1));
        filter.endProcessing();

        assertThat(harness.getErrors()).isEmpty();
        assertThat(bulkSizesWhenSent).containsExactly(1, 1);
    }

    /// A bulk request rejected with a 403 (e.g. the account lacks `create_index`/`auto_configure` on the target
    /// data stream) aborts the current stream part with a fatal error, and the pipeline goes on to the stream's
    /// next part with the same filter. The filter resets its parsing state at each part, so later parts are still
    /// indexed (seen: every later part silently dropped, with no request and no error).
    @Test
    void testDocumentsInLaterStreamPartsAreIndexedAfterABulkFailure() throws IOException {
        Mockito.when(elasticClient.bulk(ArgumentMatchers.any(BulkRequest.class)))
                .thenAnswer(invocation -> {
                    final BulkRequest bulkRequest = invocation.getArgument(0);
                    recordBulkRequest(bulkRequest);
                    return bulkSizesWhenSent.size() == 1
                            ? forbiddenResponse()
                            : successResponse(bulkRequest.operations().size());
                });
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(1);

        filter.startProcessing();
        // What AbstractProcessorTaskExecutor.processNestedStreams sees, before it moves on to the next part.
        assertThatThrownBy(() -> parsePart(filter, documents(1)))
                .isInstanceOf(LoggedException.class);
        assertThat(harness.getMessages(Severity.FATAL_ERROR)).isNotEmpty();
        parsePart(filter, documents(1));
        filter.endProcessing();

        // One request for the rejected part 1 document and one for the part 2 document.
        assertThat(bulkSizesWhenSent).containsExactly(1, 1);
    }

    // One part of a stream, as the pipeline gives each part to the filter.
    private void parsePart(final ElasticIndexingFilter filter, final String content) {
        filter.startStream();
        try {
            harness.parse(filter, jsonXml(content));
        } finally {
            filter.endStream();
        }
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

    private static BulkResponse forbiddenResponse() {
        return BulkResponse.of(b -> b
                .errors(true)
                .took(1L)
                .items(BulkResponseItem.of(i -> i
                        .operationType(OperationType.Create)
                        .index(INDEX_NAME)
                        .status(403)
                        .error(e -> e.type("security_exception")
                                .reason("action [indices:admin/auto_create] is unauthorized")))));
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
