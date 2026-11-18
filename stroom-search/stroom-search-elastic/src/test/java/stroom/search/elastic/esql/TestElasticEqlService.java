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

package stroom.search.elastic.esql;

import stroom.ai.api.ElasticEql.Event;
import stroom.ai.api.ElasticEql.Reason;
import stroom.ai.api.ElasticEql.Refusal;
import stroom.ai.api.ElasticEql.Request;
import stroom.ai.api.ElasticEql.Result;
import stroom.ai.api.ElasticEql.Sequence;
import stroom.ai.api.ElasticEql.Target;
import stroom.docref.DocRef;
import stroom.query.api.TimeRange;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticWireMock;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/// Tests [ElasticEqlService] with the real Elasticsearch Java client against a WireMock server standing in for an
/// Elasticsearch cluster.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestElasticEqlService {

    private static final DocRef CLUSTER_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-uuid", "Cluster");
    private static final DocRef OTHER_CLUSTER = new DocRef(ElasticClusterDoc.TYPE, "other-uuid", "Other");
    private static final DocRef DEVICES = new DocRef(ElasticIndexDoc.TYPE, "devices-uuid", "Devices");
    private static final DocRef SYSMON = new DocRef(ElasticIndexDoc.TYPE, "sysmon-uuid", "Sysmon");
    private static final String EQL_PATH = "/devices,sysmon/_eql/search";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String QUERY = "sequence with maxspan=2m [any where stroom_eql_doc == \"1\"] by user "
                                        + "[any where stroom_eql_doc == \"2\"] by SubjectUserName";
    private static final String FOUND = """
            {"is_partial": false, "is_running": false, "took": 7, "timed_out": false,
             "hits": {"total": {"value": 1, "relation": "eq"}, "sequences": [
               {"join_keys": ["alice"], "events": [
                 {"_index": "devices-000001", "_id": "a1",
                  "fields": {"@timestamp": ["1767225600000"], "StreamId": [1], "EventId": [2],
                             "stroom_eql_doc": ["1"]}},
                 {"_index": "", "_id": "", "_source": {}, "missing": true},
                 {"_index": "sysmon", "_id": "b1",
                  "fields": {"@timestamp": ["1767225640000"], "StreamId": [3], "EventId": [4],
                             "Image": ["powershell.exe"], "stroom_eql_doc": ["2"]}}]}]}}""";

    private static final String RUNNING = """
            {"id": "search-1", "is_partial": true, "is_running": true, "took": 100,
             "timed_out": false, "hits": {}}""";

    @RegisterExtension
    static final WireMockExtension ELASTIC = ElasticWireMock.createExtension();

    @Mock
    private SecurityContext securityContext;
    @Mock
    private ElasticIndexStore elasticIndexStore;
    @Mock
    private ElasticClusterStore elasticClusterStore;
    @Mock
    private ElasticClientCache elasticClientCache;

    private ElasticsearchClient client;
    private ElasticEqlService service;

    @BeforeEach
    void setUp() {
        client = ElasticWireMock.createClient(ELASTIC);
        final ElasticClusterDoc cluster = ElasticClusterDoc.builder()
                .uuid(CLUSTER_REF.getUuid())
                .connection(new ElasticConnectionConfig(List.of("http://localhost:9200"), null, null, null))
                .build();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(cluster);
        when(elasticClientCache.contextResult(eq(cluster.getConnection()), any()))
                .thenAnswer(invocation -> invocation
                        .<Function<ElasticsearchClient, ?>>getArgument(1)
                        .apply(client));
        when(securityContext.hasDocumentPermission(any(DocRef.class), eq(DocumentPermission.USE))).thenReturn(true);
        when(securityContext.useAsReadResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        when(elasticIndexStore.readDocument(DEVICES)).thenReturn(index(DEVICES, "devices", "@timestamp",
                CLUSTER_REF));
        when(elasticIndexStore.readDocument(SYSMON)).thenReturn(index(SYSMON, "sysmon", "@timestamp", CLUSTER_REF));
        service = new ElasticEqlService(securityContext, elasticIndexStore, elasticClusterStore, elasticClientCache);
        stubVersion("9.5.3");
        stubResolve("devices", """
                {"indices": [{"name": "devices-000001", "aliases": ["devices"], "attributes": ["open"]}],
                 "aliases": [{"name": "devices", "indices": ["devices-000001"]}], "data_streams": []}""");
        stubResolve("sysmon", """
                {"indices": [{"name": "sysmon", "attributes": ["open"]}], "aliases": [], "data_streams": []}""");
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void aSequenceIsSearchedWithEachDocsIndicesInTheRuntimeField() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, FOUND)));

        final Result result = service.search(new Request(List.of(DEVICES, SYSMON), QUERY,
                new TimeRange(null, "2026-01-01T00:00:00.000Z", null), List.of("StreamId", "EventId", "Image"), 50,
                Duration.ofSeconds(30)));

        assertThat(result.version()).isEqualTo("9.5.3");
        assertThat(result.timeField()).isEqualTo("@timestamp");
        assertThat(result.tookMs()).isEqualTo(7);
        assertThat(result.sequences()).singleElement().satisfies(sequence -> {
            assertThat(sequence.joinKeys()).containsExactly("alice");
            assertThat(sequence.events()).hasSize(3);
            final Event first = sequence.events().getFirst();
            assertThat(first.index()).isEqualTo("devices-000001");
            assertThat(first.fields().get("@timestamp")).containsExactly("1767225600000");
            assertThat(first.fields().get("StreamId")).containsExactly(1L);
            assertThat(sequence.events().get(1).missing()).isTrue();
            assertThat(sequence.events().get(2).fields().get("Image")).containsExactly("powershell.exe");
        });

        // Only what is read comes back: not each event's whole _source.
        assertThat(ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(EQL_PATH))).getFirst()
                .queryParameter("filter_path").firstValue())
                .contains("hits.sequences.events.fields").doesNotContain("_source");
        final JsonNode body = onlyBody();
        assertThat(body.path("query").asString()).isEqualTo(QUERY);
        assertThat(body.path("timestamp_field").asString()).isEqualTo("@timestamp");
        assertThat(body.path("size").asInt()).isEqualTo(50);
        assertThat(body.path("keep_on_completion").asBoolean()).isFalse();
        assertThat(body.path("wait_for_completion_timeout").asString()).endsWith("ms");
        assertThat(body.path("allow_partial_search_results").asBoolean(true)).isFalse();
        assertThat(body.path("fields").toString()).contains("{\"field\":\"@timestamp\",\"format\":\"epoch_millis\"}")
                .contains("\"Image\"").doesNotContain("stroom_eql_doc");
        final JsonNode runtime = body.path("runtime_mappings").path("stroom_eql_doc");
        assertThat(runtime.path("type").asString()).isEqualTo("keyword");
        assertThat(runtime.path("script").path("params").path("docs").toString())
                .isEqualTo("{\"devices-000001\":[\"1\"],\"sysmon\":[\"2\"]}");
        // One query, not a list, which Elasticsearch's EQL refuses.
        assertThat(body.path("filter").isObject()).isTrue();
        assertThat(body.path("filter").toString()).contains("@timestamp").contains("epoch_millis");
    }

    @Test
    void aQueryThatDoesntNameTheDocFieldHasNoScriptAndItsEventsDocsComeFromTheirIndex() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, FOUND.replace(", \"stroom_eql_doc\": [\"1\"]", "")
                        .replace(", \"stroom_eql_doc\": [\"2\"]", ""))));

        final String query = "sequence with maxspan=2m [any where action == \"x\"] by user "
                             + "[any where Image == \"y\"] by SubjectUserName";
        final Result result = service.search(new Request(List.of(DEVICES, SYSMON), query, null, List.of(), 10,
                Duration.ofSeconds(30)));

        assertThat(onlyBody().has("runtime_mappings")).isFalse();
        final List<Event> events = result.sequences().getFirst().events();
        // From _index: devices-000001 is Devices' (1), sysmon Sysmon's (2); the missing event has none.
        assertThat(events.get(0).fields().get("stroom_eql_doc")).containsExactly("1");
        assertThat(events.get(1).fields()).doesNotContainKey("stroom_eql_doc");
        assertThat(events.get(2).fields().get("stroom_eql_doc")).containsExactly("2");
        assertThat(ElasticEqlService.usesDocField("[any where stroom_eql_doc == \"1\"]")).isTrue();
        assertThat(ElasticEqlService.usesDocField("[any where my_stroom_eql_doc_x == \"1\"]")).isFalse();
    }

    @Test
    void theDocsAreDescribedForTheCaller() {
        final Target target = service.target(List.of(DEVICES, SYSMON));

        assertThat(target.version()).isEqualTo("9.5.3");
        assertThat(target.timeField()).isEqualTo("@timestamp");
        assertThat(target.indexNames()).containsExactly("devices", "sysmon");
    }

    @Test
    void aSearchStillRunningAtTheTimeLimitIsDeletedAndTimesOut() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        stubPoll(RUNNING, 200);
        stubDelete();

        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                Duration.ofMillis(500))))
                .isInstanceOf(Refusal.class)
                .hasMessage("The search took longer than 500 ms, so it was stopped")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.TIMEOUT));
        ELASTIC.verify(WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/_eql/search/search-1")));
    }

    @Test
    void aSearchIsSubmittedWaitingOnlyAShortWhile() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, FOUND)));

        service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10, Duration.ofMinutes(5)));

        // So a stop is seen within that while, rather than only when the search ends.
        assertThat(onlyBody().path("wait_for_completion_timeout").asString()).isEqualTo("250ms");
    }

    @Test
    void aSearchStillRunningIsAskedForAgainUntilItCompletesThenDeleted() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .inScenario("poll").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("done")
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .inScenario("poll").whenScenarioStateIs("done")
                .willReturn(ElasticWireMock.elasticResponse(200, FOUND.replace("{\"is_partial\"",
                        "{\"id\": \"search-1\", \"is_partial\""))));
        stubDelete();

        final Result result = service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                Duration.ofSeconds(30)));

        assertThat(result.sequences()).hasSize(1);
        final List<LoggedRequest> polls = ELASTIC.findAll(WireMock.getRequestedFor(
                WireMock.urlPathEqualTo("/_eql/search/search-1")));
        assertThat(polls).hasSize(2);
        assertThat(polls.getFirst().queryParameter("wait_for_completion_timeout").firstValue()).isEqualTo("250ms");
        assertThat(polls.getFirst().queryParameter("keep_alive").firstValue()).isEqualTo("1m");
        assertThat(polls.getFirst().queryParameter("filter_path").firstValue())
                .contains("hits.sequences.events.fields");
        // Its results aren't kept once read.
        ELASTIC.verify(WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/_eql/search/search-1")));
    }

    @Test
    void aSearchCompletedDuringAPollIsReadAgainWhenItsEventsHaveNoFields() {
        // Elasticsearch 9.5.3 leaves the events' fields out of the answer to a poll the search completes during
        // (seen: 13 of 38 eql_sequence calls over wide windows had no times, so failed); the stored result has them.
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .inScenario("poll").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("stored")
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"id": "search-1", "is_partial": false, "is_running": false, "took": 7, "timed_out": false,
                         "hits": {"sequences": [
                           {"join_keys": ["alice"], "events": [
                             {"_index": "devices-000001", "_id": "a1"},
                             {"_index": "", "_id": "", "missing": true},
                             {"_index": "sysmon", "_id": "b1"}]}]}}""")));
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .inScenario("poll").whenScenarioStateIs("stored")
                .willReturn(ElasticWireMock.elasticResponse(200, FOUND.replace("{\"is_partial\"",
                        "{\"id\": \"search-1\", \"is_partial\""))));
        stubDelete();

        final Result result = service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                Duration.ofSeconds(30)));

        assertThat(result.sequences()).singleElement().satisfies(sequence -> {
            assertThat(sequence.events().getFirst().fields().get("@timestamp")).containsExactly("1767225600000");
            assertThat(sequence.events().getLast().fields().get("@timestamp")).containsExactly("1767225640000");
        });
        final List<LoggedRequest> polls = ELASTIC.findAll(WireMock.getRequestedFor(
                WireMock.urlPathEqualTo("/_eql/search/search-1")));
        assertThat(polls).hasSize(2);
        assertThat(polls.getLast().queryParameter("wait_for_completion_timeout").firstValue()).isEqualTo("0ms");
        ELASTIC.verify(WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/_eql/search/search-1")));
    }

    @Test
    void aSearchCompletedDuringAPollWithItsFieldsIsReadOnce() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        stubPoll(FOUND.replace("{\"is_partial\"", "{\"id\": \"search-1\", \"is_partial\""), 0);
        stubDelete();

        service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10, Duration.ofSeconds(30)));

        assertThat(ELASTIC.findAll(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/_eql/search/search-1"))))
                .hasSize(1);
    }

    @Test
    void aStopDuringTheSearchDeletesItPromptly() {
        // Before, a stop was seen only once the search ended or reached its time limit, while Elasticsearch kept
        // working.
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        stubPoll(RUNNING, 200);
        stubDelete();
        final long startNs = System.nanoTime();
        final BooleanSupplier stopped = () -> System.nanoTime() - startNs > Duration.ofMillis(300).toNanos();

        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                Duration.ofMinutes(2), stopped)))
                .isInstanceOf(Refusal.class)
                .hasMessage("The search was stopped, so it was deleted in Elasticsearch")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.STOPPED));
        assertThat(Duration.ofNanos(System.nanoTime() - startNs)).isLessThan(Duration.ofSeconds(3));
        ELASTIC.verify(WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/_eql/search/search-1")));
    }

    @Test
    void aSearchStoppedBeforeItStartsIsNotSent() {
        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                Duration.ofMinutes(2), () -> true)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.STOPPED));
        assertThat(ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(EQL_PATH)))).isEmpty();
    }

    @Test
    void theSequenceBreakerIsTheSearchNeedingTooMuchMemory() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(429, TestElasticEsqlService.breaker("eql_sequence",
                        "TRANSIENT", 429))));

        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                null)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.TOO_MUCH_MEMORY))
                .hasMessageStartingWith("The search needs more memory than Elasticsearch allows it: narrow the "
                                        + "time window, shorten maxspan, or put the more selective step first. "
                                        + "Elasticsearch said: [eql_sequence] Data too large");
    }

    @Test
    void aTransientParentBreakerSaysToTryAgainShortly() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(429, TestElasticEsqlService.breaker("parent",
                        "TRANSIENT", 429))));

        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                null)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.CLUSTER_BUSY))
                .hasMessageStartingWith("Elasticsearch is short of memory right now, so it refused the search: try "
                                        + "again shortly. Elasticsearch said: [parent] Data too large");
    }

    @Test
    void aPermanentBreakerWhilePollingSaysToAskForLessAndDeletesTheSearch() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, RUNNING)));
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .willReturn(ElasticWireMock.elasticResponse(500, TestElasticEsqlService.breaker("parent",
                        "PERMANENT", 500))));
        stubDelete();

        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                Duration.ofSeconds(30))))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.TOO_MUCH_MEMORY));
        ELASTIC.verify(WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/_eql/search/search-1")));
    }

    @Test
    void anUnknownFieldIsNamed() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(EQL_PATH))
                .willReturn(ElasticWireMock.elasticResponse(400, """
                        {"error": {"root_cause": [{"type": "verification_exception",
                          "reason": "Found 1 problem\\nline 1:37: Unknown column [usr]"}],
                         "type": "verification_exception",
                         "reason": "Found 1 problem\\nline 1:37: Unknown column [usr]"}, "status": 400}""")));

        assertThatThrownBy(() -> service.search(new Request(List.of(DEVICES, SYSMON), QUERY, null, List.of(), 10,
                null)))
                .isInstanceOf(Refusal.class)
                .hasMessage("Elasticsearch refused the search: line 1:37: Unknown column [usr]")
                .satisfies(e -> assertThat(((Refusal) e).names()).containsExactly("usr"));
    }

    @Test
    void docsOnDifferentClustersAreRefusedNamingEach() {
        when(elasticIndexStore.readDocument(SYSMON)).thenReturn(index(SYSMON, "sysmon", "@timestamp",
                OTHER_CLUSTER));

        assertThatThrownBy(() -> service.target(List.of(DEVICES, SYSMON)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("'Devices' on cluster 'Cluster', 'Sysmon' on cluster 'Other'")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.DIFFERENT_CLUSTERS));
    }

    @Test
    void docsWithDifferentTimeFieldsAreRefusedNamingEach() {
        when(elasticIndexStore.readDocument(SYSMON)).thenReturn(index(SYSMON, "sysmon", "EventTime", CLUSTER_REF));

        assertThatThrownBy(() -> service.target(List.of(DEVICES, SYSMON)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("'Devices' orders by '@timestamp', 'Sysmon' orders by 'EventTime'")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.DIFFERENT_TIME_FIELDS));
    }

    @Test
    void aDocWithoutATimeFieldIsRefused() {
        when(elasticIndexStore.readDocument(SYSMON)).thenReturn(index(SYSMON, "sysmon", "  ", CLUSTER_REF));

        assertThatThrownBy(() -> service.target(List.of(DEVICES, SYSMON)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("'Sysmon' has no time field");
    }

    @Test
    void aDocTheUserCantUseIsRefused() {
        when(securityContext.hasDocumentPermission(SYSMON, DocumentPermission.USE)).thenReturn(false);

        assertThatThrownBy(() -> service.target(List.of(DEVICES, SYSMON)))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("sysmon-uuid");
    }

    @Test
    void aClusterTooOldForEqlIsRefused() {
        stubVersion("7.9.3");

        assertThatThrownBy(() -> service.target(List.of(DEVICES)))
                .isInstanceOf(Refusal.class)
                .hasMessage("The cluster is Elasticsearch 7.9.3: EQL sequences need 7.10 or later");
    }

    @Test
    void eachConcreteIndexKnowsItsDocs() {
        assertThat(ElasticEqlService.docsByIndex(List.of("logs-*", "logs-2026"),
                List.of(Set.of("logs-2026"), Set.of("logs-2026")))).isEqualTo(Map.of("logs-2026", List.of("1", "2")));
    }

    private static ElasticIndexDoc index(final DocRef doc,
                                         final String indexName,
                                         final String timeField,
                                         final DocRef cluster) {
        return ElasticIndexDoc.builder()
                .uuid(doc.getUuid())
                .name(doc.getName())
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField(timeField)
                .build();
    }

    private void stubVersion(final String version) {
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/"))
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"name": "node", "cluster_name": "c", "cluster_uuid": "u",
                         "version": {"number": "%s", "build_flavor": "default", "build_type": "docker",
                          "build_hash": "h", "build_date": "2026-01-01T00:00:00Z", "build_snapshot": false,
                          "lucene_version": "10", "minimum_wire_compatibility_version": "8.0.0",
                          "minimum_index_compatibility_version": "8.0.0"},
                         "tagline": "You Know, for Search"}""".formatted(version))));
    }

    private void stubPoll(final String body, final int delayMs) {
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .willReturn(ElasticWireMock.elasticResponse(200, body).withFixedDelay(delayMs)));
    }

    private void stubDelete() {
        ELASTIC.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/_eql/search/search-1"))
                .willReturn(ElasticWireMock.elasticResponse(200, "{\"acknowledged\": true}")));
    }

    private void stubResolve(final String name, final String body) {
        ELASTIC.stubFor(WireMock.any(WireMock.urlPathEqualTo("/_resolve/index/" + name))
                .willReturn(ElasticWireMock.elasticResponse(200, body)));
    }

    private JsonNode onlyBody() {
        final List<LoggedRequest> requests = ELASTIC.findAll(WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(EQL_PATH)));
        assertThat(requests).hasSize(1);
        return MAPPER.readTree(requests.getFirst().getBodyAsString());
    }
}
