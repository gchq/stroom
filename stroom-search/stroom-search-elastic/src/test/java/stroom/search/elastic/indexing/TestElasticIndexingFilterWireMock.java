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
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.search.elastic.ElasticWireMock;
import stroom.util.shared.Severity;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    void testBulkRequestBody() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(2)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setIngestPipeline("my-pipeline");
        filter.setRefreshAfterEachBatch(true);

        harness.process(filter, jsonXml("""
                <map><string key="user">jbloggs</string><number key="n">1</number></map>
                <map><boolean key="ok">true</boolean><array key="tags"><string>a</string></array></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        final List<LoggedRequest> requests = ELASTIC.findAll(createBulkRequests());
        assertThat(requests).hasSize(1);
        final LoggedRequest request = requests.getFirst();
        assertThat(request.queryParameter("refresh").firstValue()).isEqualTo("true");
        assertThat(ndjsonLines(request)).containsExactly(
                json("""
                        {"create":{"_index":"test-index","pipeline":"my-pipeline"}}"""),
                json("""
                        {"user":"jbloggs","n":1}"""),
                json("""
                        {"create":{"_index":"test-index","pipeline":"my-pipeline"}}"""),
                json("""
                        {"ok":true,"tags":["a"]}"""));
    }

    @Test
    void testBulkRequestNoRefreshOrPipeline() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        harness.process(filter, jsonXml("""
                <map><string key="_year">2023</string><string key="a">b</string></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        final LoggedRequest request = ELASTIC.findAll(createBulkRequests()).getFirst();
        assertThat(request.queryParameter("refresh").firstValue()).isEqualTo("false");
        assertThat(ndjsonLines(request)).containsExactly(
                json("""
                        {"create":{"_index":"events-2023"}}"""),
                json("""
                        {"a":"b"}"""));
    }

    @Test
    void testBatchesSentAsSeparateRequests() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(2)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(2);

        harness.process(filter, jsonXml(documents(5)));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(ELASTIC.findAll(createBulkRequests()))
                .extracting(request -> ndjsonLines(request).size() / 2)
                .containsExactly(2, 2, 1);
    }

    @Test
    void testBatchesSplitByMaxBatchSize() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(2)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        // Each document is `{"n":<0-9>}`, 7 or 8 bytes (with the space Jackson writes between root values), so a
        // batch is sent after every second document
        filter.setMaxBatchSize("14");

        harness.process(filter, jsonXml(documents(5)));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(ELASTIC.findAll(createBulkRequests()))
                .extracting(request -> ndjsonLines(request).size() / 2)
                .containsExactly(2, 2, 1);
    }

    @Test
    void testMaxBatchSizeWithUnit() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setMaxBatchSize("1K");

        harness.process(filter, jsonXml(documents(200)));

        // Document 0 is 7 bytes. Each later document has the space Jackson writes between root values, so
        // 1-9 are 8 bytes, 10-99 are 9 and 100-199 are 10, and the first 114 come to 1,029 bytes, the first to
        // reach 1,024
        assertThat(harness.getErrors()).isEmpty();
        assertThat(ELASTIC.findAll(createBulkRequests()))
                .extracting(request -> ndjsonLines(request).size() / 2)
                .containsExactly(114, 86);
    }

    @Test
    void testDocumentLargerThanMaxBatchSizeSentAlone() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setMaxBatchSize("5");

        harness.process(filter, jsonXml(documents(3)));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(ELASTIC.findAll(createBulkRequests()))
                .extracting(request -> ndjsonLines(request).size() / 2)
                .containsExactly(1, 1, 1);
    }

    @Test
    void testBlankMaxBatchSizeKeepsDefault() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setMaxBatchSize(" ");

        harness.process(filter, jsonXml(documents(3)));

        assertThat(ELASTIC.findAll(createBulkRequests()))
                .extracting(request -> ndjsonLines(request).size() / 2)
                .containsExactly(3);
    }

    @Test
    void testInvalidMaxBatchSize() {
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        assertThatThrownBy(() -> filter.setMaxBatchSize("ten"))
                .isInstanceOf(PipelineFactoryException.class)
                .hasMessage("Incorrect value for max batch size: ten");
        assertThatThrownBy(() -> filter.setMaxBatchSize("0"))
                .isInstanceOf(PipelineFactoryException.class)
                .hasMessage("Incorrect value for max batch size: 0");
        assertThatThrownBy(() -> filter.setMaxBatchSize("-1M"))
                .isInstanceOf(PipelineFactoryException.class);
    }

    @Test
    void testPurgeOnReprocess() {
        harness.setReprocess(true);
        // Two pages of index names, then an empty page to end the composite aggregation
        ELASTIC.stubFor(search()
                .inScenario("pages")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("page2")
                .willReturn(searchResponse("events-2023", "events-2024")));
        ELASTIC.stubFor(search()
                .inScenario("pages")
                .whenScenarioStateIs("page2")
                .willSetStateTo("page3")
                .willReturn(searchResponse("events-2025")));
        ELASTIC.stubFor(search()
                .inScenario("pages")
                .whenScenarioStateIs("page3")
                .willReturn(searchResponse()));
        ELASTIC.stubFor(deleteByQuery().willReturn(ElasticWireMock.elasticResponse(200, """
                {"took":1500,"timed_out":false,"total":3,"deleted":3,"batches":1,"version_conflicts":0,
                 "noops":0,"retries":{"bulk":0,"search":0},"throttled_millis":0,"requests_per_second":-1.0,
                 "throttled_until_millis":0,"failures":[]}""")));
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter("events-{_year}");
        filter.setBatchSize(500);

        harness.process(filter, jsonXml("""
                <map><string key="_year">2026</string><string key="a">b</string></map>"""));

        assertThat(harness.getErrors()).isEmpty();

        // The search for the indices holding the stream's documents
        final List<LoggedRequest> searches = ELASTIC.findAll(
                WireMock.postRequestedFor(WireMock.urlPathMatching(SEARCH_PATH_REGEX)));
        assertThat(searches).hasSize(3);
        final JsonNode firstSearch = JSON_MAPPER.readTree(searches.getFirst().getBodyAsString());
        assertThat(firstSearch.at("/query/term/StreamId/value").asLong()).isEqualTo(STREAM_ID);
        assertThat(firstSearch.at("/aggregations/indices/composite/sources/0/index_name/terms/field").asString())
                .isEqualTo("_index");
        assertThat(firstSearch.at("/aggregations/indices/composite/after").isMissingNode()).isTrue();
        // Subsequent pages start after the last key of the previous page
        assertThat(JSON_MAPPER.readTree(searches.get(1).getBodyAsString())
                .at("/aggregations/indices/composite/after/index_name").asString())
                .isEqualTo("events-2024");

        // A delete for each index
        final List<LoggedRequest> deletes = ELASTIC.findAll(
                WireMock.postRequestedFor(WireMock.urlPathMatching("/[^/]+/_delete_by_query")));
        assertThat(deletes)
                .extracting(LoggedRequest::getUrl)
                .extracting(url -> url.substring(0, url.indexOf('?')))
                .containsExactly(
                        "/events-2023/_delete_by_query",
                        "/events-2024/_delete_by_query",
                        "/events-2025/_delete_by_query");
        assertThat(deletes).allSatisfy(request -> {
            assertThat(request.queryParameter("refresh").firstValue()).isEqualTo("true");
            assertThat(request.queryParameter("scroll_size").firstValue()).isEqualTo("500");
            assertThat(JSON_MAPPER.readTree(request.getBodyAsString()).at("/query/term/StreamId/value").asLong())
                    .isEqualTo(STREAM_ID);
        });

        // The purge happens before indexing
        final List<String> urlsInOrder = ELASTIC.getAllServeEvents().reversed().stream()
                .map(serveEvent -> serveEvent.getRequest().getUrl())
                .toList();
        assertThat(urlsInOrder.getLast()).startsWith(BULK_PATH);
        assertThat(urlsInOrder.subList(0, urlsInOrder.size() - 1))
                .noneMatch(url -> url.startsWith(BULK_PATH));
    }

    @Test
    void testPurgeWithNoMatchingIndices() {
        harness.setReprocess(true);
        ELASTIC.stubFor(search().willReturn(searchResponse()));
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        harness.process(filter, jsonXml("""
                <map><string key="_year">2026</string><string key="a">b</string></map>"""));

        assertThat(harness.getErrors()).isEmpty();
        ELASTIC.verify(0, WireMock.postRequestedFor(WireMock.urlPathMatching(".*/_delete_by_query")));
        ELASTIC.verify(1, createBulkRequests());
    }

    @Test
    void testNoPurgeWhenDisabled() {
        harness.setReprocess(true);
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setPurgeOnReprocess(false);

        harness.process(filter, jsonXml(documents(1)));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(ELASTIC.getAllServeEvents())
                .extracting(serveEvent -> serveEvent.getRequest().getUrl())
                .allMatch(url -> url.startsWith(BULK_PATH));
    }

    @Test
    void testNoPurgeWhenNotReprocessing() {
        ELASTIC.stubFor(createBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(1)));

        assertThat(harness.getErrors()).isEmpty();
        assertThat(ELASTIC.getAllServeEvents())
                .extracting(serveEvent -> serveEvent.getRequest().getUrl())
                .allMatch(url -> url.startsWith(BULK_PATH));
    }

    @Test
    void testPurgeSearchFailure() {
        harness.setReprocess(true);
        ELASTIC.stubFor(search().willReturn(errorResponse(500, "internal_server_error")));
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .first()
                .isEqualTo("Failed to list indices for reindex purge. StreamId: " + STREAM_ID
                           + ". Base name: 'events-{_year}'");
        ELASTIC.verify(0, WireMock.postRequestedFor(WireMock.urlPathMatching(".*/_delete_by_query")));
    }

    @Test
    void testPurgeDeleteFailure() {
        harness.setReprocess(true);
        ELASTIC.stubFor(search()
                .inScenario("pages")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("done")
                .willReturn(searchResponse("events-2023")));
        ELASTIC.stubFor(search()
                .inScenario("pages")
                .whenScenarioStateIs("done")
                .willReturn(searchResponse()));
        ELASTIC.stubFor(deleteByQuery().willReturn(errorResponse(500, "search_phase_execution_exception")));
        final ElasticIndexingFilter filter = createFilter("events-{_year}");

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .first()
                .isEqualTo("Failed to purge documents for StreamId: " + STREAM_ID);
        ELASTIC.verify(0, createBulkRequests());
    }

    @Test
    void testPurgeIndexNameWithoutBaseName() {
        harness.setReprocess(true);
        final ElasticIndexingFilter filter = createFilter("{_year}");

        assertThatThrownBy(filter::startProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .first()
                .isEqualTo("Expected one or more characters in the index name before the first curly brace.");
        assertThat(ELASTIC.getAllServeEvents()).isEmpty();
    }

    @Test
    void testPartialBulkFailure() {
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse(
                createdItem(0),
                rejectedItem("mapper_parsing_exception", "failed to parse field [n]"))));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(2)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        // Not retried
        ELASTIC.verify(1, createBulkRequests());
        // The rejected document is reported as its own error, with Elasticsearch's reason
        assertThat(harness.getMessages(Severity.ERROR))
                .containsExactly("Elasticsearch rejected document 2 of the stream (mapper_parsing_exception): " +
                                 "failed to parse field [n]");
        // The fatal error summarises, rather than holding the whole response
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Bulk indexing request failed: 1 of 2 documents rejected by Elasticsearch");
    }

    @Test
    void testRejectionCauseIncluded() {
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse("""
                {"create":{"_index":"test-index","status":400,
                  "error":{"type":"document_parsing_exception",
                    "reason":"[1:6] failed to parse field [n] of type [long]",
                    "caused_by":{"type":"illegal_argument_exception","reason":"For input string: \\"abc\\""}}}}""")));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.ERROR))
                .containsExactly("Elasticsearch rejected document 1 of the stream (document_parsing_exception): " +
                                 "[1:6] failed to parse field [n] of type [long]. " +
                                 "Caused by (illegal_argument_exception): For input string: \"abc\"");
    }

    @Test
    void testRejectionWithoutReason() {
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse("""
                {"create":{"_index":"test-index","status":400,
                  "error":{"type":"version_conflict_engine_exception"}}}""")));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.ERROR))
                .containsExactly("Elasticsearch rejected document 1 of the stream " +
                                 "(version_conflict_engine_exception)");
    }

    @Test
    void testLongRejectionsReportedInFull() {
        // Together, these reasons are far longer than the 4,096 characters a single message used to be cut to
        final int count = 50;
        final String reason = "failed to parse field [n] " + "x".repeat(200);
        final String[] items = new String[count];
        for (int i = 0; i < count; i++) {
            items[i] = rejectedItem("mapper_parsing_exception", reason);
        }
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse(items)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(count)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        final List<String> errors = harness.getMessages(Severity.ERROR);
        assertThat(errors).hasSize(count);
        for (int i = 0; i < count; i++) {
            assertThat(errors.get(i))
                    .startsWith("Elasticsearch rejected document " + (i + 1) + " of the stream ")
                    .endsWith(reason);
        }
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Bulk indexing request failed: 50 of 50 documents rejected by Elasticsearch");
    }

    @Test
    void testVeryLongRejectionTruncated() {
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse(
                rejectedItem("mapper_parsing_exception", "x".repeat(5000)))));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.ERROR))
                .singleElement()
                .satisfies(message -> assertThat(message).hasSize(4096));
    }

    @Test
    void testRejectionsOverCapSummarised() {
        final int count = 150;
        final String[] items = new String[count];
        for (int i = 0; i < count; i++) {
            items[i] = rejectedItem("mapper_parsing_exception", "failed to parse field [n]");
        }
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse(items)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(count)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        final List<String> errors = harness.getMessages(Severity.ERROR);
        assertThat(errors).hasSize(101);
        assertThat(errors.subList(0, 100))
                .allMatch(message -> message.startsWith("Elasticsearch rejected document "));
        assertThat(errors.getLast())
                .isEqualTo("Elasticsearch rejected 150 documents in this batch; only the first 100 are reported " +
                           "individually");
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Bulk indexing request failed: 150 of 150 documents rejected by Elasticsearch");
    }

    @Test
    void testRejectedDocumentNamedByEventId() {
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse(
                rejectedItem("mapper_parsing_exception", "failed to parse field [a]"),
                rejectedItem("mapper_parsing_exception", "failed to parse field [a]"),
                rejectedItem("mapper_parsing_exception", "failed to parse field [a]"))));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        // The second document only has an EventId in a nested map, which doesn't name the document
        harness.parse(filter, jsonXml("""
                <map><number key="EventId">17</number><string key="a">b</string></map>
                <map><map key="inner"><number key="EventId">99</number></map><string key="a">b</string></map>
                <map><string key="EventId">42</string><string key="a">b</string></map>"""));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getMessages(Severity.ERROR))
                .extracting(message -> message.substring(0, message.indexOf(" (")))
                .containsExactly(
                        "Elasticsearch rejected the document with EventId 17",
                        "Elasticsearch rejected document 2 of the stream",
                        "Elasticsearch rejected the document with EventId 42");
    }

    @Test
    void testRejectedDocumentNamedAcrossBatches() {
        // The first batch is indexed, then the second document of the second batch is rejected
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(RETRIED_STATE)
                .willReturn(bulkSuccess(2)));
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(RETRIED_STATE)
                .willReturn(bulkResponse(
                        createdItem(2),
                        rejectedItem("mapper_parsing_exception", "failed to parse field [n]"))));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);
        filter.setBatchSize(2);

        filter.startProcessing();
        assertThatThrownBy(() -> harness.parse(filter, jsonXml(documents(5))))
                .isInstanceOf(LoggedException.class);

        ELASTIC.verify(2, createBulkRequests());
        assertThat(harness.getMessages(Severity.ERROR))
                .singleElement()
                .asString()
                .startsWith("Elasticsearch rejected document 4 of the stream ");
    }

    @Test
    void testOverloadRetriesExhaustedReportsOtherRejections() {
        // Every attempt has one document rejected as Elasticsearch is overloaded, and one rejected outright
        ELASTIC.stubFor(createBulk().willReturn(bulkResponse(
                """
                        {"create":{"_index":"test-index","status":429,
                          "error":{"type":"es_rejected_execution_exception","reason":"rejected execution"}}}""",
                rejectedItem("mapper_parsing_exception", "failed to parse field [n]"))));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(2)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        ELASTIC.verify(RETRY_COUNT + 1, createBulkRequests());
        // Only the document rejected outright is reported, and only once, when the retries run out
        assertThat(harness.getMessages(Severity.ERROR))
                .containsExactly("Elasticsearch rejected document 2 of the stream (mapper_parsing_exception): " +
                                 "failed to parse field [n]");
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .containsExactly("Indexing failed to complete after " + RETRY_COUNT + " retries: Elasticsearch " +
                                 "was overloaded and rejected 1 of 2 documents, and rejected 1 for other reasons");
    }

    @Test
    void testOverloadedItemsRetried() {
        // The first attempt has one document indexed and one rejected, the retry succeeds
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(RETRIED_STATE)
                .willReturn(partiallyRejected()));
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(RETRIED_STATE)
                .willReturn(bulkSuccess(2)));
        ELASTIC.stubFor(deleteBulk().willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(2)));

        assertThat(harness.getErrors()).isEmpty();
        // The document indexed by the failed request is deleted, to avoid duplicates
        final List<LoggedRequest> deletes = ELASTIC.findAll(deleteBulkRequests());
        assertThat(deletes).hasSize(1);
        assertThat(ndjsonLines(deletes.getFirst())).containsExactly(json("""
                {"delete":{"_id":"id-0","_index":"test-index"}}"""));
        // The whole batch is resent
        final List<LoggedRequest> creates = ELASTIC.findAll(createBulkRequests());
        assertThat(creates).hasSize(2);
        assertThat(ndjsonLines(creates.get(1))).isEqualTo(ndjsonLines(creates.getFirst()));
    }

    @Test
    void testTooManyRequestsRetried() {
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(RETRIED_STATE)
                .willReturn(errorResponse(429, "es_rejected_execution_exception")));
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(RETRIED_STATE)
                .willReturn(bulkSuccess(1)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(1)));

        assertThat(harness.getErrors()).isEmpty();
        ELASTIC.verify(2, createBulkRequests());
    }

    @Test
    void testTooManyRequestsRetriesExhausted() {
        ELASTIC.stubFor(createBulk().willReturn(errorResponse(429, "es_rejected_execution_exception")));
        final ElasticIndexingFilter filter = harness.createFilter(
                elasticClient,
                ElasticIndexingFilterTestHarness.indexingConfig(10, 1),
                INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        // The first attempt plus the single retry by the filter
        ELASTIC.verify(2, createBulkRequests());
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Indexing failed to complete after 1 retries: ")
                .contains("es_rejected_execution_exception");
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

    @Test
    void testServiceUnavailableIsFatal() {
        // Only a 429 is treated as Elasticsearch being overloaded, so a 503 is not retried
        ELASTIC.stubFor(createBulk().willReturn(errorResponse(503, "unavailable_shards_exception")));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        // Sent once, as the HTTP client does not retry a 503 itself
        ELASTIC.verify(1, createBulkRequests());
        assertThat(harness.getMessages(Severity.FATAL_ERROR))
                .singleElement()
                .asString()
                .startsWith("Indexing failed to complete after 0 retries: ")
                .contains("unavailable_shards_exception");
    }

    @Test
    void testServerErrorWithoutElasticBodyIsFatal() {
        ELASTIC.stubFor(createBulk().willReturn(WireMock.aResponse()
                .withStatus(502)
                .withHeader("Content-Type", "text/plain")
                .withBody("Bad Gateway")));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.severity()).isEqualTo(Severity.FATAL_ERROR));
    }

    @Test
    void testConnectionFailureIsFatal() {
        ELASTIC.stubFor(createBulk().willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        filter.startProcessing();
        harness.parse(filter, jsonXml(documents(1)));
        assertThatThrownBy(filter::endProcessing)
                .isInstanceOf(LoggedException.class);

        assertThat(harness.getErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.severity()).isEqualTo(Severity.FATAL_ERROR));
    }

    @Test
    void testDeleteOfIndexedDocumentsFailsWithConnectionError() {
        // A failure to delete the documents indexed by a rejected request is only a warning, and the batch is
        // still retried
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(RETRIED_STATE)
                .willReturn(partiallyRejected()));
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(RETRIED_STATE)
                .willReturn(bulkSuccess(2)));
        ELASTIC.stubFor(deleteBulk().willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(2)));

        assertThat(harness.getErrors()).isEmpty();
        ELASTIC.verify(1, deleteBulkRequests());
        ELASTIC.verify(2, createBulkRequests());
    }

    @Test
    void testDeleteOfIndexedDocumentsFailsWithErrorResponse() {
        // An error response is also only a warning
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo(RETRIED_STATE)
                .willReturn(partiallyRejected()));
        ELASTIC.stubFor(createBulk()
                .inScenario(RETRY_SCENARIO)
                .whenScenarioStateIs(RETRIED_STATE)
                .willReturn(bulkSuccess(2)));
        ELASTIC.stubFor(deleteBulk().willReturn(errorResponse(500, "internal_server_error")));
        final ElasticIndexingFilter filter = createFilter(INDEX_NAME);

        harness.process(filter, jsonXml(documents(2)));

        assertThat(harness.getErrors()).isEmpty();
        ELASTIC.verify(1, deleteBulkRequests());
        ELASTIC.verify(2, createBulkRequests());
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

    private static ResponseDefinitionBuilder bulkResponse(final String... items) {
        return ElasticWireMock.elasticResponse(200,
                "{\"took\":3,\"errors\":true,\"items\":[" + String.join(",", items) + "]}");
    }

    private static String createdItem(final int index) {
        return "{\"create\":{\"_index\":\"test-index\",\"_id\":\"id-" + index + "\",\"status\":201}}";
    }

    private static String rejectedItem(final String type, final String reason) {
        return """
                {"create":{"_index":"test-index","status":400,"error":{"type":"%s","reason":"%s"}}}"""
                .formatted(type, reason);
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
