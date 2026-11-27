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

import stroom.ai.api.AiService;
import stroom.ai.api.ElasticKeyStreams.KeyField;
import stroom.ai.api.ElasticKeyStreams.KeyPage;
import stroom.ai.api.ElasticKeyStreams.KeyRange;
import stroom.ai.api.ElasticKeyStreams.KeyRequest;
import stroom.ai.api.ElasticKeyStreams.NextPage;
import stroom.ai.api.ElasticKeyStreams.Reason;
import stroom.ai.api.ElasticKeyStreams.Refusal;
import stroom.ai.api.ElasticKeyStreams.Sample;
import stroom.ai.api.ElasticKeyStreams.SampleRequest;
import stroom.ai.api.ElasticKeyStreams.Scan;
import stroom.ai.api.ElasticKeyStreams.ScanPage;
import stroom.ai.api.ElasticKeyStreams.ScanRequest;
import stroom.ai.api.ElasticKeyStreams.Side;
import stroom.ai.api.ElasticKeyStreams.Target;
import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.IndexFieldCache;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticWireMock;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/// Tests [ElasticKeyStreamsService] with the real Elasticsearch Java client against a WireMock server standing in
/// for an Elasticsearch cluster.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestElasticKeyStreamsService {

    private static final DocRef CLUSTER_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-uuid", "Cluster");
    private static final DocRef OTHER_CLUSTER = new DocRef(ElasticClusterDoc.TYPE, "other-uuid", "Other");
    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    private static final DocRef USERS = new DocRef(ElasticIndexDoc.TYPE, "users-uuid", "Users");
    private static final DocRef ELSEWHERE = new DocRef(ElasticIndexDoc.TYPE, "elsewhere-uuid", "Elsewhere");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROXY_SEARCH = "/proxy/_search";
    private static final String KEYS_PAGE_1 = """
            {"took": 3, "timed_out": false, "aggregations": {"keys": {
              "after_key": {"k0": "bob"},
              "buckets": [{"key": {"k0": "alice"}, "doc_count": 4}, {"key": {"k0": "bob"}, "doc_count": 2}]}}}""";
    private static final String KEYS_PAGE_2 = """
            {"took": 2, "timed_out": false, "aggregations": {"keys": {
              "after_key": {"k0": "carol"},
              "buckets": [{"key": {"k0": "carol"}, "doc_count": 5}]}}}""";
    private static final String KEYS_END = """
            {"took": 1, "timed_out": false, "aggregations": {"keys": {"buckets": []}}}""";

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
    @Mock
    private AiService aiService;
    @Mock
    private IndexFieldCache indexFieldCache;
    @Mock
    private WordListProvider wordListProvider;

    private ElasticsearchClient client;
    private ElasticKeyStreamsService service;

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
        doAnswer(invocation -> {
            invocation.<Consumer<ElasticsearchClient>>getArgument(1).accept(client);
            return null;
        }).when(elasticClientCache).context(eq(cluster.getConnection()), any());
        when(securityContext.hasDocumentPermission(any(DocRef.class), eq(DocumentPermission.USE))).thenReturn(true);
        when(securityContext.useAsReadResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(securityContext).useAsRead(any());
        when(elasticIndexStore.readDocument(PROXY)).thenReturn(index(PROXY, "proxy", "@timestamp", CLUSTER_REF,
                List.of(field("user", "keyword"), field("url", "keyword"), field("message", "text"),
                        field("bytes", "long"), field("@timestamp", "date"))));
        when(elasticIndexStore.readDocument(USERS)).thenReturn(index(USERS, "users", null, CLUSTER_REF,
                List.of(field("sAMAccountName", "keyword"), field("team", "keyword"))));
        when(elasticIndexStore.readDocument(ELSEWHERE)).thenReturn(index(ELSEWHERE, "elsewhere", "@timestamp",
                OTHER_CLUSTER, List.of()));
        service = new ElasticKeyStreamsService(securityContext, elasticIndexStore, elasticClusterStore,
                elasticClientCache, () -> aiService, indexFieldCache, wordListProvider);
        stubVersion("9.5.3");
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void theTargetGivesEachDocsTimeFieldWhichMayDifferOrBeMissing() {
        final Target target = service.target(List.of(PROXY, USERS));

        assertThat(target.version()).isEqualTo("9.5.3");
        assertThat(target.docs()).extracting(doc -> doc.timeField()).containsExactly("@timestamp", null);
        assertThat(target.docs().getFirst().indexName()).isEqualTo("proxy");
        assertThat(target.docs().getFirst().fields().get("user").nativeType()).isEqualTo("keyword");
    }

    @Test
    void docsOnTwoClustersAreRefusedNamingEach() {
        assertThatThrownBy(() -> service.target(List.of(PROXY, ELSEWHERE)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("different clusters")
                .hasMessageContaining("a join's sides are read from one cluster")
                .satisfies(e -> assertThat(((Refusal) e).names()).hasSize(2));
    }

    @Test
    void aDocTheUserCantUseIsRefusedOnEveryCall() {
        when(securityContext.hasDocumentPermission(PROXY, DocumentPermission.USE)).thenReturn(false);

        assertThatThrownBy(() -> service.target(List.of(PROXY))).isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> service.keyPage(new KeyRequest(side(PROXY, "user"), null, 10, null, null)))
                .isInstanceOf(PermissionException.class);
        assertThatThrownBy(() -> service.scanPage(new NextPage(new Scan(new ScanRequest(side(PROXY, "user"),
                List.of(), 10, null, null), "pit-1", null), null)))
                .isInstanceOf(PermissionException.class);
        ELASTIC.verify(0, WireMock.postRequestedFor(WireMock.anyUrl()));
    }

    @Test
    void aPageOfKeysIsACompositeAggregationOnTheKeyWithTheRangeAndTermsAsFilters() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_PAGE_1)));

        final Side side = new Side(PROXY, List.of(new KeyField("user")), null,
                new TimeRange(null, "2026-01-01T00:00:00.000Z", null), new KeyRange("aaron", "bob"),
                List.of(List.of("alice", "bob")));
        final KeyPage page = service.keyPage(new KeyRequest(side, List.of("aaron"), 2, Duration.ofSeconds(10), null));

        assertThat(page.keys()).extracting(key -> key.key().getFirst()).containsExactly("alice", "bob");
        assertThat(page.keys()).extracting(key -> key.count()).containsExactly(4L, 2L);
        assertThat(page.after()).containsExactly("bob");
        assertThat(page.tookMs()).isEqualTo(3);

        final JsonNode body = onlyBody(PROXY_SEARCH);
        assertThat(body.path("size").asInt()).isZero();
        final JsonNode composite = body.path("aggs").path("keys").path("composite");
        assertThat(composite.path("size").asInt()).isEqualTo(2);
        assertThat(composite.path("sources").toString()).isEqualTo("[{\"k0\":{\"terms\":{\"field\":\"user\"}}}]");
        assertThat(composite.path("after").toString()).isEqualTo("{\"k0\":\"aaron\"}");
        final String filters = body.path("query").path("bool").path("filter").toString();
        assertThat(filters).contains("{\"range\":{\"user\":{\"gt\":\"aaron\",\"lte\":\"bob\"}}}")
                .contains("{\"terms\":{\"user\":[\"alice\",\"bob\"]}}")
                .contains("{\"exists\":{\"field\":\"user\"}}")
                .contains("@timestamp");
        assertThat(onlyRequest(PROXY_SEARCH).queryParameter("allow_partial_search_results").firstValue())
                .isEqualTo("false");
    }

    @Test
    void aTransformedKeyIsReadAsARuntimeFieldThatSortsAndFilters() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_END)));

        final Side side = new Side(PROXY, List.of(new KeyField("user", List.of(Transform.STRIP_DOMAIN,
                Transform.LOWERCASE))), null, null, new KeyRange(null, "m"), List.of());
        final KeyPage page = service.keyPage(new KeyRequest(side, null, 10, null, null));

        assertThat(page.keys()).isEmpty();
        assertThat(page.after()).isNull();
        final JsonNode body = onlyBody(PROXY_SEARCH);
        final JsonNode runtime = body.path("runtime_mappings").path("stroom_join_key_0");
        assertThat(runtime.path("type").asString()).isEqualTo("keyword");
        assertThat(runtime.path("script").path("params").path("f").asString()).isEqualTo("user");
        assertThat(runtime.path("script").path("source").asString())
                .contains("lastIndexOf('\\\\')").contains("toLowerCase()").contains("emit(v)");
        assertThat(body.path("aggs").path("keys").path("composite").path("sources").toString())
                .contains("stroom_join_key_0");
        assertThat(body.path("query").toString()).contains("{\"range\":{\"stroom_join_key_0\":{\"lte\":\"m\"}}}");
    }

    @Test
    void aTransformOnAFieldThatIsntTextOrAKeyOnATextFieldIsRefused() {
        assertThatThrownBy(() -> service.keyPage(new KeyRequest(new Side(PROXY, List.of(new KeyField("bytes",
                List.of(Transform.LOWERCASE))), null, null, null, null), null, 10, null, null)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.KEY_NOT_USABLE));
        assertThatThrownBy(() -> service.keyPage(new KeyRequest(side(PROXY, "message"), null, 10, null, null)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("keyword subfield");
        assertThatThrownBy(() -> service.keyPage(new KeyRequest(side(PROXY, "nope"), null, 10, null, null)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.UNKNOWN_FIELD));
    }

    @Test
    void tooManyTermsAreRefusedBeforeAnythingIsSent() {
        final List<Object> many = new ArrayList<>();
        for (int i = 0; i <= 65_536; i++) {
            many.add("u" + i);
        }
        assertThatThrownBy(() -> service.keyPage(new KeyRequest(new Side(PROXY, List.of(new KeyField("user")), null,
                null, null, List.of(many)), null, 10, null, null)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("at most 65536")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.INVALID));
    }

    @Test
    void aScanOpensAPointInTimeAndPagesByTheKeyThenTheTimeUntilAShortPageClosesIt() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo("/proxy/_pit"))
                .willReturn(ElasticWireMock.elasticResponse(200, "{\"id\": \"pit-1\"}")));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo("/_search"))
                .inScenario("scan").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"took": 4, "pit_id": "pit-2", "hits": {"hits": [
                          {"_index": "proxy-1", "_id": "a", "sort": ["alice", 1000, 7],
                           "fields": {"url": ["abc.com"]}},
                          {"_index": "proxy-1", "_id": "b", "sort": ["alice", 2000, 8], "fields": {}}]}}"""))
                .willSetStateTo("second"));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo("/_search"))
                .inScenario("scan").whenScenarioStateIs("second")
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"took": 1, "pit_id": "pit-2", "hits": {"hits": [
                          {"_index": "proxy-1", "_id": "c", "sort": ["bob", 500, 9], "fields": {}}]}}""")));
        ELASTIC.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/_pit"))
                .willReturn(ElasticWireMock.elasticResponse(200, "{\"succeeded\": true, \"num_freed\": 1}")));

        final Scan scan = service.openScan(new ScanRequest(side(PROXY, "user"), List.of("url"), 2,
                Duration.ofSeconds(10), null));
        assertThat(scan.pointInTime()).isEqualTo("pit-1");
        assertThat(onlyRequest("/proxy/_pit").queryParameter("keep_alive").firstValue()).isEqualTo("2m");

        final ScanPage first = service.scanPage(new NextPage(scan, Duration.ofSeconds(10)));
        assertThat(first.rows()).extracting(row -> row.id()).containsExactly("a", "b");
        assertThat(first.rows().getFirst().key()).containsExactly("alice");
        assertThat(first.rows().getFirst().time()).isEqualTo(1000L);
        assertThat(first.rows().getFirst().fields().get("url")).containsExactly("abc.com");
        assertThat(first.next().pointInTime()).isEqualTo("pit-2");
        assertThat(first.next().searchAfter()).containsExactly("alice", 2000L, 8L);

        final ScanPage second = service.scanPage(new NextPage(first.next(), Duration.ofSeconds(10)));
        assertThat(second.rows()).extracting(row -> row.id()).containsExactly("c");
        assertThat(second.next()).isNull();

        final List<LoggedRequest> searches = ELASTIC.findAll(WireMock.postRequestedFor(
                WireMock.urlPathEqualTo("/_search")));
        final JsonNode firstBody = MAPPER.readTree(searches.getFirst().getBodyAsString());
        assertThat(firstBody.path("sort").toString()).isEqualTo(
                "[{\"user\":{\"order\":\"asc\"}},{\"@timestamp\":{\"order\":\"asc\",\"numeric_type\":\"date\"}}]");
        assertThat(firstBody.path("pit").path("id").asString()).isEqualTo("pit-1");
        assertThat(firstBody.path("_source").asBoolean(true)).isFalse();
        assertThat(firstBody.path("fields").toString()).isEqualTo("[\"url\"]");
        assertThat(firstBody.has("search_after")).isFalse();
        assertThat(firstBody.path("query").toString()).contains("{\"exists\":{\"field\":\"@timestamp\"}}");
        final JsonNode secondBody = MAPPER.readTree(searches.getLast().getBodyAsString());
        assertThat(secondBody.path("search_after").toString()).isEqualTo("[\"alice\",2000,8]");
        assertThat(secondBody.path("pit").path("id").asString()).isEqualTo("pit-2");
        // The short page was the last, so its point in time was closed.
        ELASTIC.verify(WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/_pit"))
                .withRequestBody(WireMock.containing("pit-2")));
    }

    @Test
    void aDocWithNoTimeFieldCantBeScanned() {
        assertThatThrownBy(() -> service.openScan(new ScanRequest(side(USERS, "sAMAccountName"), List.of(), 10, null,
                null)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("has no time field")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.NO_TIME_FIELD));
    }

    @Test
    void anExpiredPointInTimeIsToldApart() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo("/_search"))
                .willReturn(ElasticWireMock.elasticResponse(404, """
                        {"error": {"root_cause": [{"type": "search_context_missing_exception",
                          "reason": "No search context found for id [1]"}],
                          "type": "search_phase_execution_exception", "reason": "all shards failed"},
                         "status": 404}""")));

        assertThatThrownBy(() -> service.scanPage(new NextPage(new Scan(new ScanRequest(side(PROXY, "user"),
                List.of(), 10, null, null), "pit-1", null), null)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.SCAN_EXPIRED));
    }

    @Test
    void aReadStoppedByItsCallerIsCancelled() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_END).withFixedDelay(3_000)));
        final AtomicBoolean stop = new AtomicBoolean();
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stop.set(true);
        }).start();

        assertThatThrownBy(() -> service.keyPage(new KeyRequest(side(PROXY, "user"), null, 10,
                Duration.ofSeconds(30), stop::get)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.STOPPED));
    }

    @Test
    void aReadPastItsTimeLimitTimesOut() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_END).withFixedDelay(3_000)));

        assertThatThrownBy(() -> service.keyPage(new KeyRequest(side(PROXY, "user"), null, 10,
                Duration.ofMillis(400), null)))
                .isInstanceOf(Refusal.class)
                .hasMessage("The read took longer than 400 ms, so it was cancelled")
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    void aSampleCutsTheKeysWhereTheRunningCountPassesEachMultiple() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .inScenario("sample").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_PAGE_1)).willSetStateTo("2"));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .inScenario("sample").whenScenarioStateIs("2")
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_PAGE_2)).willSetStateTo("3"));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(PROXY_SEARCH))
                .inScenario("sample").whenScenarioStateIs("3")
                .willReturn(ElasticWireMock.elasticResponse(200, KEYS_END)));

        // alice 4 (4), bob 2 (6), carol 5 (11): every 4 events cuts after alice (4) and after carol (11 passes 8).
        final Sample sample = service.sampleKeys(new SampleRequest(side(PROXY, "user"), 4, 10, null, null));

        assertThat(sample.boundaries()).containsExactly("alice", "carol");
        assertThat(sample.keys()).isEqualTo(3);
        assertThat(sample.rows()).isEqualTo(11);
    }

    @Test
    void theTransformsInJavaMatchWhatTheScriptDoes() {
        assertThat(Transform.STRIP_DOMAIN.apply("CORP\\jsmith")).isEqualTo("jsmith");
        assertThat(Transform.STRIP_DOMAIN.apply("jsmith@corp.example")).isEqualTo("jsmith");
        assertThat(Transform.STRIP_DOMAIN.apply("@odd")).isEqualTo("@odd");
        assertThat(Transform.FILE_NAME.apply("C:\\Users\\j\\report.docx")).isEqualTo("report.docx");
        assertThat(Transform.FILE_NAME.apply("/home/j/report.docx")).isEqualTo("report.docx");
        assertThat(Transform.applyAll(List.of(Transform.STRIP_DOMAIN, Transform.LOWERCASE), "CORP\\JSmith"))
                .isEqualTo("jsmith");
        assertThat(ElasticKeyStreamsService.script(new KeyField("user", List.of(Transform.FILE_NAME))))
                .contains("Math.max(v.lastIndexOf('/'), v.lastIndexOf('\\\\'))");
    }

    @Test
    void anIndexPatternIsEncodedNameByName() {
        assertThat(ElasticKeyStreamsService.indexPath("logs-*, proxy")).isEqualTo("/logs-*,proxy");
    }

    // --------------------------------------------------------------------------------

    private static Side side(final DocRef doc, final String key) {
        return new Side(doc, List.of(new KeyField(key)), null, null, null, null);
    }

    private static JsonNode onlyBody(final String path) {
        return MAPPER.readTree(onlyRequest(path).getBodyAsString());
    }

    private static LoggedRequest onlyRequest(final String path) {
        final List<LoggedRequest> requests = ELASTIC.findAll(WireMock.anyRequestedFor(WireMock.urlPathEqualTo(path)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private static ElasticIndexField field(final String name, final String nativeType) {
        return ElasticIndexField.builder()
                .fldName(name)
                .fldType("long".equals(nativeType)
                        ? FieldType.LONG
                        : "date".equals(nativeType)
                                ? FieldType.DATE
                                : "text".equals(nativeType)
                                        ? FieldType.TEXT
                                        : FieldType.KEYWORD)
                .nativeType(nativeType)
                .build();
    }

    private static ElasticIndexDoc index(final DocRef doc,
                                         final String indexName,
                                         final String timeField,
                                         final DocRef cluster,
                                         final List<ElasticIndexField> fields) {
        return ElasticIndexDoc.builder()
                .uuid(doc.getUuid())
                .name(doc.getName())
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField(timeField)
                .fields(fields)
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
}
