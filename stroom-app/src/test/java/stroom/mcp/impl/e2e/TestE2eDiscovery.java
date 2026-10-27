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

package stroom.mcp.impl.e2e;

import stroom.dashboard.impl.DashboardStore;
import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/// A discovery index, as `dev/e2e_discovery.py` runs it against Elasticsearch 9: raw data (JSON, delimited text,
/// XML) indexed as it is, for exploration, with no event-logging translation and nothing surveyed up front.
/// Elasticsearch maps the source's fields dynamically; only StreamId, EventId and @timestamp are mapped explicitly.
///
/// Each scenario is a test: the raw JSON sample (plan, pipeline, the agreed template the cluster admin applies,
/// indexing, the same searches through Stroom and in Elasticsearch, documentation); an existing raw feed whose data
/// drifts, its streams backfilled by id and what is sent later indexed by a feed filter; delimited text and XML; a
/// data stream from the plan alone, with the user choosing @timestamp and reprocessing dropping the stream first;
/// awkward shapes, one record Elasticsearch rejects reported, not lost.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. Elasticsearch keeps what each run indexed, so names carry a run's stamp,
/// and what a test made there is removed after it.
@SuppressWarnings("unchecked")
class TestE2eDiscovery extends AbstractE2eTest {

    private static final String FIXTURE_TEMPLATE = "E2E Raw to Elasticsearch";
    private static final String TEXT_TEMPLATE = "E2E Raw Text to Elasticsearch";
    private static final String XML_TEMPLATE = "E2E Raw XML to Elasticsearch";
    private static final String FIXTURE_CLUSTER = "E2E Live Elasticsearch";
    // Each fixture template's parser: its element id and type.
    private static final Map<String, List<String>> PARSERS = Map.of(
            FIXTURE_TEMPLATE, List.of("jsonParser", "JSONParser"),
            TEXT_TEMPLATE, List.of("dsParser", "DSParser"),
            XML_TEMPLATE, List.of("xmlParser", "XMLParser"));
    private static final List<String> EXPLICIT = List.of("StreamId", "EventId", "@timestamp");
    // Unique to the run: Elasticsearch keeps every run's indices and templates.
    private static final String STAMP = Long.toString(System.currentTimeMillis(), 36);
    private static final String XPATH_FUNCTIONS = "http://www.w3.org/2005/xpath-functions";

    private static final String SAMPLE = pretty(List.of(
            obj("ts", "2026-10-01T09:00:00Z", "host", "web01", "user", obj("name", "alice", "roles",
                    List.of("admin", "ops")), "event", "login", "status", 200, "latency_ms", 12.5,
                    "session_token", "tok-1", "message", compact(obj("action", "login", "client",
                            obj("ip", "10.1.1.1", "agent", "curl/8.9"), "ok", true))),
            obj("ts", "2026-10-01T09:01:30Z", "host", "web02", "user", obj("name", "bob"), "event", "logout",
                    "status", 200, "session_token", "tok-2", "message", "session closed by user"),
            obj("ts", "2026-10-01T09:05:00Z", "host", "web01", "user", obj("name", "carol"), "event", "upload",
                    "status", 500, "tags", List.of("bulk", "retry"), "session_token", "tok-3",
                    "message", compact(obj("action", "upload", "bytes", 1048576, "error",
                            obj("code", "E42", "detail", "disk full"))))));

    // The user's example: a sibling discovery index's template, for its settings and components (not its fields:
    // a discovery index keeps the source's names).
    private static final String EXAMPLE = """
            PUT _index_template/stroom-discovery-proxy-v1
            {"index_patterns": ["stroom-discovery-proxy-v1*"], "priority": 250, \
            "composed_of": ["e2e-discovery-base"], \
            "template": {"settings": {"index": {"number_of_shards": 1, "number_of_replicas": 0}}, \
            "mappings": {"dynamic": true, "properties": {"proxy": {"properties": {"name": {"type": "keyword"}}}}}}}""";
    private static final String COMPONENT = """
            PUT _component_template/e2e-discovery-base
            {"template": {"mappings": {"properties": {"StreamId": {"type": "long"}, \
            "EventId": {"type": "long"}}}}}""";

    // A sibling discovery index's template already on the cluster: standalone, with no component templates, so it
    // maps StreamId, EventId and @timestamp itself.
    private static final Map<String, Object> STANDALONE = obj(
            "index_patterns", List.of("e2e-discovery-legacy-v1*"), "priority", 150, "template", obj(
                    "settings", obj("index", obj("number_of_shards", 1, "number_of_replicas", 0,
                            "refresh_interval", "5s")),
                    "mappings", obj("dynamic", true, "properties", obj("StreamId", obj("type", "long"),
                            "EventId", obj("type", "long"), "@timestamp", obj("type", "date"),
                            "proxy", obj("type", "keyword")))));
    private static final String STANDALONE_EXAMPLE = "PUT _index_template/e2e-discovery-legacy-v1\n"
                                                     + compact(STANDALONE);

    private static final String CSV_SAMPLE = """
            time,user,host,status,latency_ms,msg,secret
            2026-10-02T10:00:00Z,alice,web01,200,12.5,{"action":"login"},s1
            2026-10-02T10:01:00Z,bob,web02,404,n/a,page missing,s2
            2026-10-02T10:02:00Z,carol,web01,500,30,{"action":"upload"},s3
            """;
    private static final String XML_SAMPLE = "<logons xmlns=\"urn:example:logons\">"
            + "<logon id=\"1\"><when>2026-10-02T11:00:00Z</when><who>dave</who><roles><role>admin</role>"
            + "<role>ops</role></roles><client ip=\"10.2.2.1\">laptop</client><status>200</status></logon>"
            + "<logon id=\"2\"><when>2026-10-02T11:05:00Z</when><who>erin</who><roles><role>ops</role></roles>"
            + "<client ip=\"10.2.2.2\">phone</client><status>401</status></logon>"
            + "<logon id=\"3\"><when>2026-10-02T11:09:00Z</when><who>frank</who><roles><role>ops</role></roles>"
            + "<client ip=\"10.2.2.3\">laptop</client><status>500</status><detail><code>E42</code></detail>"
            + "</logon></logons>";

    // Two fields of times: the event's (ts) and when it was received; the user picks @timestamp.
    private static final String TWO_TIMES = pretty(List.of(
            obj("ts", "2026-10-01T09:00:00Z", "received", "2026-10-01T09:00:04Z", "host", "web01", "event", "login",
                    "status", 200),
            obj("ts", "2026-10-01T09:01:30Z", "received", "2026-10-01T09:01:31Z", "host", "web02", "event", "logout",
                    "status", 200),
            obj("ts", "2026-10-01T09:05:00Z", "received", "2026-10-01T09:05:09Z", "host", "web01", "event", "upload",
                    "status", 500)));

    // An existing feed's streams, oldest first, as a sending system sent them over time. The data drifts: the
    // oldest has latency_ms as "n/a" (later ones are numbers), the newest adds a geo object.
    private static final List<List<Map<String, Object>>> FEED_STREAMS = List.of(
            List.of(obj("time", "2026-09-01T08:00:00Z", "app", "billing", "level", "INFO", "latency_ms", "n/a",
                            "msg", "started"),
                    obj("time", "2026-09-01T08:00:05Z", "app", "billing", "level", "WARN", "latency_ms", "n/a",
                            "msg", "slow disk")),
            List.of(obj("time", "2026-09-15T10:00:00Z", "app", "billing", "level", "INFO", "latency_ms", 31,
                            "msg", "invoice 1001"),
                    obj("time", "2026-09-15T10:00:09Z", "app", "billing", "level", "ERROR", "latency_ms", 950,
                            "msg", compact(obj("error", "timeout", "upstream", "payments")))),
            List.of(obj("time", "2026-10-01T12:00:00Z", "app", "billing", "level", "INFO", "latency_ms", 12,
                    "msg", "invoice 1002", "geo", obj("country", "NZ", "city", "Wellington"))));
    private static final List<Map<String, Object>> LATER = List.of(obj("time", "2026-10-04T07:30:00Z", "app",
            "billing", "level", "INFO", "latency_ms", 8, "msg", "invoice 1003", "geo", obj("country", "AU",
                    "city", "Sydney"), "retry", true));

    private static final List<Map<String, Object>> SHAPES = shapes();

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private ElasticClusterStore elasticClusterStore;
    @Inject
    private DashboardStore dashboardStore;

    private ElasticTestNode node;
    // What a test made in Elasticsearch, removed after it.
    private final List<String> indices = new ArrayList<>();
    private final List<String> indexTemplates = new ArrayList<>();

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
    }

    @AfterEach
    void removeWhatWasIndexed() {
        if (node == null) {
            return;
        }
        for (final String index : indices) {
            node.request("DELETE", "/_data_stream/" + index, null);
            node.deleteIndices(index + "*");
        }
        indexTemplates.forEach(node::deleteIndexTemplate);
    }

    /// Raw JSON, from what the user confirms to documents Stroom and Elasticsearch both find.
    @Test
    void rawJsonIsIndexedAsItIs() {
        final String build = "e2e-discovery-" + STAMP;
        final String feed = "E2E-WEB-" + STAMP.toUpperCase();
        final String index = indexName("web");
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final String template = discoveryTemplate(FIXTURE_TEMPLATE);
        assertThat(templates("discovery")).as("the fixture reads as a discovery template")
                .anySatisfy(candidate -> assertThat(candidate)
                        .containsEntry("name", FIXTURE_TEMPLATE)
                        .containsEntry("backend", "elasticsearch"));
        final DocRef cluster = liveCluster();

        // The discovery plan: nothing surveyed, only what the user confirms.
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "discovery", obj("timestamp_field", "ts", "meta", obj("stroom.feed", "Feed"),
                        "drop", List.of("session_token")))), "plan");
        assertThat(fieldNames(plan)).as("only StreamId, EventId and @timestamp are mapped explicitly")
                .containsExactlyElementsOf(EXPLICIT);
        final String pipeline = discoveryPipeline(build, index, plan, template, cluster);
        final Map<String, Object> sample = stepSample(pipeline, List.of(raw));
        assertThat(sample).as("stepped clean: %s", sample.get("groups")).containsEntry("verdict", "clean");
        final List<Map<String, Object>> documents = documents(pipeline, raw, 3);
        final Map<String, Object> first = documents.getFirst();
        assertThat(documents).extracting(document -> document.get("EventId"))
                .as("EventId the record number").containsExactly(number("1"), number("2"), number("3"));
        assertThat(first).as("StreamId is the stream; each record as it is, @timestamp from ts and the feed from "
                             + "stream meta")
                .containsEntry("StreamId", number(String.valueOf(raw)))
                .containsEntry("@timestamp", string("2026-10-01T09:00:00Z"))
                .containsEntry("stroom.feed", string(feed));
        assertThat(map(first, "user").get("roles")).isEqualTo(List.of(string("admin"), string("ops")));
        assertThat(map(map(first, "message_json"), "client")).as("a JSON message unpacked beside its text")
                .containsEntry("ip", string("10.1.1.1"));
        assertThat(documents.get(1)).as("a plain message left as it is")
                .doesNotContainKey("message_json")
                .containsEntry("message", string("session closed by user"));
        assertThat(documents).as("the dropped field left out").noneMatch(doc -> doc.containsKey("session_token"));

        // The index template: refused before it is agreed; permissive, with the example's settings.
        assertThat(replyText("create_processor_filter", args("pipeline_uuid", pipeline, "stream_ids", List.of(raw))))
                .as("refused before a template is agreed")
                .startsWith("No Elasticsearch index template has been agreed");
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(raw), "example_template", EXAMPLE,
                "component_templates", List.of(COMPONENT)));
        final Map<String, Object> body = map(agreedTemplate, "template");
        final Map<String, Object> mappings = map(map(body, "template"), "mappings");
        assertThat(agreedTemplate).containsEntry("agreed", true);
        assertThat(mappings).as("dynamic mapping, no date guessing")
                .containsEntry("dynamic", true)
                .containsEntry("date_detection", false);
        assertThat(jsonOf(mappings.get("dynamic_templates"))).as("strings as keywords")
                .contains("strings_as_keywords").contains("\"keyword\"");
        final Map<String, Object> settings = map(map(map(body, "template"), "settings"), "index");
        assertThat(settings).containsEntry("number_of_shards", 1L);
        assertThat(jsonOf(settings)).as("the discovery guardrails")
                .contains("\"ignore_malformed\":true").contains("\"limit\":2000");
        assertThat((List<Object>) body.get("composed_of")).containsExactly("e2e-discovery-base");
        assertThat(map(mappings, "properties")).as("StreamId and EventId left to the component, @timestamp "
                                                   + "explicit, the example's own fields not copied")
                .isEqualTo(obj("@timestamp", obj("type", "date")));

        // The cluster admin applies it; the raw stream indexed.
        apply(COMPONENT);
        apply((String) agreedTemplate.get("dev_tools"));
        indexStreams(pipeline, List.of(raw));
        node.refresh(index);
        assertThat(node.count(index, null)).as("the index holds the 3 records").isEqualTo(3);
        final Map<String, Object> mapped = mapping(index);
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("StreamId", "long");
        expected.put("EventId", "long");
        expected.put("@timestamp", "date");
        expected.put("ts", "keyword");
        expected.put("user.name", "keyword");
        expected.put("user.roles", "keyword");
        expected.put("status", "long");
        expected.put("latency_ms", "float");
        expected.put("tags", "keyword");
        expected.put("message", "keyword");
        expected.put("message_json.client.ip", "keyword");
        expected.put("message_json.ok", "boolean");
        expected.put("message_json.bytes", "long");
        expected.put("message_json.error.code", "keyword");
        expected.put("stroom.feed", "keyword");
        assertThat(kinds(mapped, expected.keySet())).as("Elasticsearch mapped the fields dynamically, strings as "
                                                       + "keywords").isEqualTo(expected);
        assertThat(kind(mapped, "session_token")).as("the dropped field was never indexed").isNull();
        assertThat(node.count(index, "{\"term\": {\"message_json.error.code\": \"E42\"}}"))
                .as("a field from inside the JSON message is searchable").isEqualTo(1);

        // Searched through Stroom and in Elasticsearch, each hit traced to its record; documented.
        final String indexDoc = indexDoc(build, index, cluster);
        paired(build, index, indexDoc, List.of(raw), 3,
                List.of("StreamId", "EventId", "@timestamp", "user.name", "event"), List.of(
                        check("user.name", "EQUALS", "alice", 1, "{\"term\": {\"user.name\": \"alice\"}}"),
                        // Keywords: exact case.
                        check("user.name", "EQUALS", "Alice", 0, "{\"term\": {\"user.name\": \"Alice\"}}"),
                        check("user.name", "EQUALS", "*aro*", 1, "{\"wildcard\": {\"user.name\": \"*aro*\"}}"),
                        check("user.name", "IN", "alice,bob", 2,
                                "{\"terms\": {\"user.name\": [\"alice\", \"bob\"]}}"),
                        check("user.name", "MATCHES_REGEX", "ca.*", 1, "{\"regexp\": {\"user.name\": \"ca.*\"}}"),
                        // In an array.
                        check("user.roles", "EQUALS", "ops", 1, "{\"term\": {\"user.roles\": \"ops\"}}"),
                        check("tags", "EQUALS", "retry", 1, "{\"term\": {\"tags\": \"retry\"}}"),
                        check("status", "GREATER_THAN", "200", 1, "{\"range\": {\"status\": {\"gt\": 200}}}"),
                        check("status", "BETWEEN", "100,300", 2,
                                "{\"range\": {\"status\": {\"gte\": 100, \"lte\": 300}}}"),
                        check("latency_ms", "LESS_THAN", "20", 1, "{\"range\": {\"latency_ms\": {\"lt\": 20}}}"),
                        check("message", "EQUALS", "*session*", 1,
                                "{\"wildcard\": {\"message\": \"*session*\"}}"),
                        check("message_json.client.ip", "EQUALS", "10.1.1.1", 1,
                                "{\"term\": {\"message_json.client.ip\": \"10.1.1.1\"}}"),
                        check("message_json.ok", "EQUALS", "true", 1,
                                "{\"term\": {\"message_json.ok\": true}}"),
                        check("message_json.bytes", "GREATER_THAN", "1000000", 1,
                                "{\"range\": {\"message_json.bytes\": {\"gt\": 1000000}}}"),
                        check("message_json.error.code", "EQUALS", "*", 1,
                                "{\"exists\": {\"field\": \"message_json.error.code\"}}"),
                        check("@timestamp", "BETWEEN", "2026-10-01T09:00:30.000Z,2026-10-01T09:10:00.000Z", 2,
                                "{\"range\": {\"@timestamp\": {\"gte\": \"2026-10-01T09:00:30.000Z\", "
                                + "\"lte\": \"2026-10-01T09:10:00.000Z\"}}}")),
                pipeline);
        assertThat(replyText("verify_index", args("build", build, "index_uuid", indexDoc, "backend",
                "elasticsearch", "stream_ids", List.of(raw), "expected_documents", 3, "fields", List.of("StreamId"),
                "searches", List.of(obj("field", "user.name", "condition", "STARTS_WITH", "value", "al")))))
                .as("a search Stroom would answer wrongly on Elasticsearch is refused, with what to use")
                .contains("use EQUALS 'al*'");
        final Map<String, Object> written = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "markdown", "## Purpose and data\n\nWeb access logs, indexed as they are for exploration.\n",
                "change", "Created", "stream_ids", List.of(raw)));
        final String section = String.valueOf(written.get("field_mapping"));
        final Map<String, List<String>> rows = fieldRows(section);
        assertThat(section).as("documented as a discovery index, with the agreed template")
                .contains("Elasticsearch, discovery")
                .contains("Elasticsearch index template `" + index + "`, agreed with the user");
        assertThat(rows.get("@timestamp").subList(1, 3)).containsExactly("date", "`ts`");
        assertThat(rows.get("message_json.client.ip").subList(1, rows.get("message_json.client.ip").size()))
                .as("the fields the sample held, with how often and a value")
                .containsExactly("33% of documents", "`10.1.1.1`");
        documentedToTheField(uuidOf(written), List.of("StreamId", "EventId", "@timestamp", "message_json.client.ip"),
                Map.of("message_json.client.ip", "10.1.1.1"));
    }

    /// An existing raw feed, outside the build, already holding three streams whose data drifts: backfilled by id,
    /// and what is sent from now on indexed by a feed filter.
    @Test
    void anExistingRawFeedIsIndexedAsItIs() {
        final String source = "SRC-BILLING-" + STAMP.toUpperCase();
        final String build = "e2e-discovery-existing-" + STAMP;
        final String index = indexName("billing");
        createFeed("src-" + STAMP, source);
        final List<Long> ids = new ArrayList<>();
        for (final List<Map<String, Object>> records : FEED_STREAMS) {
            ids.add(upload(source, compact(records)));
        }

        // The discovery pipeline, stepped on the two newest streams, where they are.
        final String template = discoveryTemplate(FIXTURE_TEMPLATE);
        final DocRef cluster = liveCluster();
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "discovery", obj("timestamp_field", "time"))), "plan");
        final String pipeline = discoveryPipeline(build, index, plan, template, cluster);
        final List<Long> newest = ids.subList(1, 3);
        assertThat(stepSample(pipeline, newest)).as("the newest streams stepped clean, in place")
                .containsEntry("verdict", "clean");

        // The template waits for the user's example, then is built from it and agreed.
        final Map<String, Object> unasked = client.json("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", newest));
        assertThat(unasked).as("without an example, the user is asked for one first")
                .containsEntry("needs", "example_template")
                .doesNotContainKey("dev_tools");
        assertThat((String) unasked.get("hint")).contains("sibling discovery index");
        // What the user pastes: the sibling discovery index's template, as GET returns it. It has no components.
        indexTemplates.add("e2e-discovery-legacy-v1");
        assertThat(node.request("PUT", "/_index_template/e2e-discovery-legacy-v1", compact(STANDALONE)).isSuccess())
                .as("a standalone sibling template on the cluster").isTrue();
        final String example = node.request("GET", "/_index_template/e2e-discovery-legacy-v1", null).body();
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", newest, "example_template", example));
        final Map<String, Object> body = map(agreedTemplate, "template");
        assertThat(agreedTemplate).containsEntry("agreed", true);
        assertThat(body).as("the new index's pattern, the sibling's priority, no component templates")
                .containsEntry("index_patterns", List.of(index + "*"))
                .containsEntry("priority", 150L)
                .doesNotContainKey("composed_of");
        assertThat(map(map(map(body, "template"), "settings"), "index")).containsEntry("refresh_interval", "5s");
        assertThat(map(map(map(body, "template"), "mappings"), "properties"))
                .as("StreamId, EventId and @timestamp mapped in the template itself; nothing of the sibling copied")
                .isEqualTo(obj("StreamId", obj("type", "long"), "EventId", obj("type", "long"),
                        "@timestamp", obj("type", "date")));
        apply((String) agreedTemplate.get("dev_tools"));

        // The existing streams by id, and what is sent from now on by feed.
        indexStreams(pipeline, ids);
        final String since = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS).toString();
        final Map<String, Object> ongoing = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "feed", source, "stream_type", "Raw Events", "created_after", since));
        final long later = upload(source, compact(LATER));
        processAll();
        assertThat(client.json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids", List.of(later),
                "expect_events", false, "filter_id", ongoing.get("filter_id"), "timeout_seconds", 10)))
                .as("stream %s, sent afterwards, indexed by the feed filter", later)
                .containsEntry("gate", "pass");
        agreed("set_processor_filter_enabled", args("filter_id", ongoing.get("filter_id"), "enabled", false));

        // Every record, the drift absorbed.
        node.refresh(index);
        final int total = FEED_STREAMS.stream().mapToInt(List::size).sum() + LATER.size();
        assertThat(node.count(index, null)).as("the index holds all %s records of the four streams", total)
                .isEqualTo(total);
        final Map<String, Object> mapped = mapping(index);
        assertThat(kind(mapped, "geo.country")).as("fields that appeared later mapped as they arrived")
                .isEqualTo("keyword");
        assertThat(kind(mapped, "retry")).isEqualTo("boolean");
        assertThat(kind(mapped, "msg_json")).as("the JSON message unpacked").isNotNull();
        if ("long".equals(kind(mapped, "latency_ms"))) {
            assertThat(node.count(index, "{\"term\": {\"_ignored\": \"latency_ms\"}}"))
                    .as("latency_ms a number; the two 'n/a' values ignored, their records kept").isEqualTo(2);
        } else {
            assertThat(kind(mapped, "latency_ms")).as("latency_ms mapped from 'n/a' first, as a keyword")
                    .isEqualTo("keyword");
        }

        // Searched through Stroom and in Elasticsearch across the four streams; documented.
        final List<Long> all = new ArrayList<>(ids);
        all.add(later);
        final String indexDoc = indexDoc(build, index, cluster);
        paired(build, index, indexDoc, all, total, List.of("StreamId", "EventId", "@timestamp", "app", "level"),
                List.of(
                        check("level", "EQUALS", "ERROR", 1, "{\"term\": {\"level\": \"ERROR\"}}"),
                        check("level", "IN", "WARN,ERROR", 2, "{\"terms\": {\"level\": [\"WARN\", \"ERROR\"]}}"),
                        check("app", "EQUALS", "billing", total, "{\"term\": {\"app\": \"billing\"}}"),
                        // Only in a later stream.
                        check("geo.country", "EQUALS", "NZ", 1, "{\"term\": {\"geo.country\": \"NZ\"}}"),
                        // Only in the last.
                        check("retry", "EQUALS", "true", 1, "{\"term\": {\"retry\": true}}"),
                        check("msg_json.upstream", "EQUALS", "payments", 1,
                                "{\"term\": {\"msg_json.upstream\": \"payments\"}}"),
                        check("@timestamp", "GREATER_THAN", "2026-10-01T00:00:00.000Z", 2,
                                "{\"range\": {\"@timestamp\": {\"gt\": \"2026-10-01T00:00:00.000Z\"}}}")),
                pipeline);
        final Map<String, Object> written = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "markdown", "## Purpose and data\n\nThe " + source + " feed, indexed as it is for "
                                      + "exploration.\n", "change", "Created", "stream_ids", newest));
        final Map<String, List<String>> rows = fieldRows(String.valueOf(written.get("field_mapping")));
        assertThat(rows).as("documented from the streams stepped").containsKey("geo.country");
        assertThat(rows.get("@timestamp").subList(1, 3)).containsExactly("date", "`time`");
    }

    /// Delimited text, through a Data Splitter, indexed as it is.
    @Test
    void delimitedTextIsIndexedAsItIs() {
        textFormat("csv", CSV_SAMPLE, TEXT_TEMPLATE, obj("input", "delimited", "timestamp_field", "time",
                        "drop", List.of("secret")),
                List.of(
                        check("user", "EQUALS", "bob", 1, "{\"term\": {\"user\": \"bob\"}}"),
                        // A number, from text.
                        check("status", "GREATER_THAN", "400", 2, "{\"range\": {\"status\": {\"gt\": 400}}}"),
                        // Its 'n/a' ignored.
                        check("latency_ms", "LESS_THAN", "20", 1, "{\"range\": {\"latency_ms\": {\"lt\": 20}}}"),
                        check("msg_json.action", "EQUALS", "upload", 1,
                                "{\"term\": {\"msg_json.action\": \"upload\"}}"),
                        check("msg", "EQUALS", "page*", 1, "{\"wildcard\": {\"msg\": \"page*\"}}")),
                List.of("user", "host", "status", "latency_ms", "msg", "msg_json.action"),
                Map.of("user", "alice", "host", "web01", "status", "200", "msg_json.action", "login"));
    }

    /// XML, one element a record, indexed as it is: repeated elements, attributes and text.
    @Test
    void xmlIsIndexedAsItIs() {
        textFormat("xml", XML_SAMPLE, XML_TEMPLATE, obj("input", "xml", "record", "logon", "timestamp_field",
                        "when"),
                List.of(
                        check("who", "EQUALS", "erin", 1, "{\"term\": {\"who\": \"erin\"}}"),
                        // Repeated elements.
                        check("roles.role", "EQUALS", "ops", 3, "{\"term\": {\"roles.role\": \"ops\"}}"),
                        // An attribute.
                        check("client.ip", "EQUALS", "10.2.2.1", 1, "{\"term\": {\"client.ip\": \"10.2.2.1\"}}"),
                        check("client.value", "EQUALS", "laptop", 2,
                                "{\"term\": {\"client.value\": \"laptop\"}}"),
                        check("status", "GREATER_THAN", "400", 2, "{\"range\": {\"status\": {\"gt\": 400}}}"),
                        check("detail.code", "EQUALS", "E42", 1, "{\"term\": {\"detail.code\": \"E42\"}}")),
                List.of("who", "roles.role", "client.ip", "client.value", "status", "detail.code"),
                Map.of("who", "dave", "roles.role", "admin", "client.ip", "10.2.2.1", "client.value", "laptop",
                        "detail.code", "E42"));
    }

    /// With no example template, a discovery index whose records all have their time is a data stream: the user
    /// picks @timestamp from the two fields of times; Stroom indexes into it and searches it; reprocessing drops
    /// it first, from a button.
    @Test
    void aDataStreamFromThePlanAlone() {
        final String build = "e2e-discovery-ds-" + STAMP;
        final String feed = "E2E-DS-RAW-" + STAMP.toUpperCase();
        final String index = indexName("ds");
        createFeed(build, feed);
        final long raw = upload(feed, TWO_TIMES);
        final String template = discoveryTemplate(FIXTURE_TEMPLATE);
        final DocRef cluster = liveCluster();
        // Two fields of times: the user is asked which is @timestamp, in a form, and picks ts.
        final int before = client.forms().size();
        client.answeringWith(choosing("ts (proposed)"));
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "events_stream_ids", List.of(raw), "discovery", obj("timestamp_field", "ts"))),
                "plan");
        assertThat(formsText(formsSince(before))).as("two fields of times: the user is asked which is @timestamp")
                .contains("Which field holds each record's time?")
                .contains("ts (proposed)")
                .contains("received");
        assertThat(map(plan, "discovery")).as("the user's choice, ts, is @timestamp")
                .containsEntry("timestamp_field", "ts");
        final String pipeline = discoveryPipeline(build, index, plan, template, cluster);
        assertThat(stepSample(pipeline, List.of(raw))).containsEntry("verdict", "clean");
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(raw), "without_example", true));
        assertThat(map(agreedTemplate, "template")).as("no example, and every record has its time: a data stream")
                .containsEntry("data_stream", Map.of());
        assertThat(String.valueOf(agreedTemplate.get("data_stream"))).startsWith("a data stream");
        apply((String) agreedTemplate.get("dev_tools"));
        indexStreams(pipeline, List.of(raw));
        final Map<String, Object> found = JsonUtil.readValue(node.request("GET", "/_data_stream/" + index, null)
                .body(), Map.class);
        final List<Map<String, Object>> streams = (List<Map<String, Object>>) found.get("data_streams");
        assertThat(streams).as("%s is a data stream in Elasticsearch", index).hasSize(1);
        assertThat(String.valueOf(((List<Map<String, Object>>) streams.getFirst().get("indices")).getFirst()
                .get("index_name"))).as("backed by a .ds- index").startsWith(".ds-");
        node.refresh(index);
        assertThat(node.count(index, null)).as("all three records in the data stream").isEqualTo(3);
        final String indexDoc = indexDoc(build, index, cluster);
        paired(build, index, indexDoc, List.of(raw), 3, EXPLICIT, List.of(
                check("host", "EQUALS", "web01", 2, "{\"term\": {\"host\": \"web01\"}}"),
                check("status", "GREATER_THAN", "400", 1, "{\"range\": {\"status\": {\"gt\": 400}}}")), pipeline);

        // Reprocessing: a data stream keeps what was indexed, so the user drops it first and it starts fresh, from a
        // button before the approval; after it, three again, not six.
        final Map<String, Object> first = client.json("reprocess_streams", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw)));
        assertThat(first).as("the data stream is dropped first").containsEntry("status", "drop_first");
        assertThat(list(first, "user_actions")).extracting(action -> action.get("request"))
                .as("from a button").containsExactly("DELETE _data_stream/" + index);
        assertThat(node.request("DELETE", "/_data_stream/" + index, null).isSuccess())
                .as("the cluster admin drops the data stream; the template stays and makes it again").isTrue();
        final Map<String, Object> again = agreed("reprocess_streams", args("pipeline_uuid", pipeline, "stream_ids",
                List.of(raw), "index_dropped", true));
        processAll();
        assertThat(client.json("wait_for_processing", args("pipeline_uuid", pipeline, "stream_ids", List.of(raw),
                "expect_events", false, "filter_id", again.get("filter_id"), "timeout_seconds", 10)))
                .as("reprocessed into the data stream with no Error stream").containsEntry("gate", "pass");
        node.refresh(index);
        assertThat(node.count(index, null)).as("three records again, not six").isEqualTo(3);
    }

    /// Awkward shapes: arrays of objects (flattened), of numbers and of arrays, null and empty values, deep
    /// nesting, keys Elasticsearch refuses or that repeat ours (renamed or repaired); one record with a value where
    /// an earlier one had an object, which Elasticsearch rejects: reported, while the others are indexed.
    @Test
    void aDiscoveryBuildIsLedFromTheStartAndMadeWithNoTemplate() {
        // Asked for by the user: discovery named at the start leads the build; with no discovery template in this
        // Stroom, a pipeline of its own as the last resort, confirmed by the user.
        final String build = "e2e-discovery-own-" + STAMP;
        final String feed = "E2E-OWN-RAW-" + STAMP.toUpperCase();
        final String index = indexName("own");
        final Map<String, Object> started = json("start_build", args("build", build, "kind", "discovery"));
        assertThat(started).containsEntry("kind", "discovery");
        assertThat(list(started, "plan")).extracting(step -> step.get("step")).as("no events pipeline")
                .contains("index", "indexing_pipeline")
                .doesNotContain("translation", "pipeline");
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final Map<String, Object> status = json("build_status", args("build", build));
        assertThat(nextStep(status)).as("the discovery plan next, not a translation").isEqualTo("index");
        assertThat(nextTool(status)).isEqualTo("draft_index_mapping");
        assertThat(map(map(next(status), "call"), "arguments")).containsEntry("backend", "elasticsearch")
                .containsKey("discovery");

        final Map<String, Object> found = json("find_pipeline_templates", args("stage", "discovery"));
        assertThat(list(found, "candidates")).as("no discovery template in this Stroom").isEmpty();
        assertThat(String.valueOf(found.get("hint"))).contains("create_indexing_pipeline standalone='discovery'");
        final DocRef cluster = liveCluster();
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "discovery", obj("timestamp_field", "ts"))), "plan");
        final String xslt = uuidOf(client.json("save_xslt", args("build", build, "name", index + "-XSLT",
                "index_plan", plan)));
        final int before = client.forms().size();
        final Map<String, Object> made = agreed("create_indexing_pipeline", args("build", build, "name",
                index + " - Discovery", "standalone", "discovery", "xslt_uuid", xslt, "index_name", index,
                "cluster_uuid", cluster.getUuid()));
        assertThat(formsText(formsSince(before))).as("the user confirms a pipeline with no template")
                .contains("with no template");
        assertThat(String.valueOf(made.get("chain"))).isEqualTo("Source -> jsonParser -> readRecordCountFilter -> "
                                                                 + "splitFilter -> xsltFilter -> schemaFilter -> "
                                                                 + "elasticIndexingFilter");
        final String pipeline = uuidOf(made);
        assertThat(templates("discovery")).as("its own pipeline is in the build, not a template")
                .noneMatch(candidate -> pipeline.equals(candidate.get("uuid")));

        // It indexes as a template's child would.
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped).as("stepped clean: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        assertThat(documents(pipeline, raw, 3)).extracting(document -> document.get("EventId"))
                .containsExactly(number("1"), number("2"), number("3"));
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(raw), "example_template", STANDALONE_EXAMPLE));
        apply((String) agreedTemplate.get("dev_tools"));
        indexStreams(pipeline, List.of(raw));
        node.refresh(index);
        assertThat(node.count(index, null)).as("the index holds the 3 records").isEqualTo(3);
        assertThat(nextStep(json("build_status", args("build", build)))).as("past the pipeline")
                .isNotIn("index", "indexing_pipeline", "translation");
    }

    @Test
    void awkwardShapesAreIndexedAndARejectedRecordReported() {
        final String build = "e2e-discovery-shapes-" + STAMP;
        final String feed = "E2E-SHAPES-" + STAMP.toUpperCase();
        final String index = indexName("shapes");
        createFeed(build, feed);
        final long raw = upload(feed, compact(SHAPES));
        final String template = discoveryTemplate(FIXTURE_TEMPLATE);
        final DocRef cluster = liveCluster();
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "discovery", obj("timestamp_field", "ts"))), "plan");
        final String pipeline = discoveryPipeline(build, index, plan, template, cluster);
        assertThat(stepSample(pipeline, List.of(raw))).containsEntry("verdict", "clean");
        final Map<String, Object> first = documents(pipeline, raw, 1).getFirst();
        assertThat(first).as("keys Elasticsearch refuses, or that repeat ours, renamed; keys it cannot take repaired")
                .containsEntry("id_original", string("src-1"))
                .containsEntry("other_original", string("kept"))
                .containsEntry("@timestamp_original", string("theirs"))
                .containsEntry("@timestamp", string("2026-10-02T08:00:00Z"))
                .containsEntry("empty_key", string("blank"))
                .containsEntry("a.b", string("two dots"))
                .doesNotContainKey("_id")
                .doesNotContainKey("");
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(raw), "example_template", STANDALONE_EXAMPLE));
        apply((String) agreedTemplate.get("dev_tools"));
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw)));
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(raw), "expect_events", false, "filter_id", started.get("filter_id"),
                "timeout_seconds", 10));
        assertThat(done).as("the rejected record is reported (an Error stream), not lost silently: %s",
                done.get("problems")).containsEntry("gate", "fail");
        assertThat((List<?>) list(done, "streams").getFirst().get("errors")).isNotEmpty();
        assertThat(nextStep(done)).as("a discovery build is not sent to write a translation")
                .isNotEqualTo("translation");
        final String why = jsonOf(client.json("summarise_streams", args("stream_ids", List.of(raw), "kind",
                "errors")));
        assertThat(why).as("triage names the document and why")
                .contains("Elasticsearch rejected document 2 of 3")
                .contains("A field is an object in some records");
        node.refresh(index);
        assertThat(node.count(index, null)).as("the other two records are indexed").isEqualTo(2);
        final Map<String, Object> mapped = mapping(index);
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("items.sku", "keyword");
        expected.put("items.qty", "long");
        expected.put("scores", "long");
        expected.put("matrix", "long");
        expected.put("deep.l1.l2.l3.l4", "keyword");
        expected.put("key with space", "keyword");
        expected.put("embedded_list", "keyword");
        expected.put("id_original", "keyword");
        expected.put("other_original", "keyword");
        expected.put("deep.inner_original", "keyword");
        expected.put("@timestamp_original", "keyword");
        expected.put("empty_key", "keyword");
        expected.put("a.b", "keyword");
        expected.put("mixed", "long");
        expected.put("user.name", "keyword");
        expected.put("@timestamp", "date");
        assertThat(kinds(mapped, expected.keySet())).as("each shape mapped as expected").isEqualTo(expected);
        assertThat(kind(mapped, "nothing")).as("null maps nothing, and its record still indexes").isNull();
        assertThat(kind(mapped, "empty_list")).as("nor does an empty array").isNull();
        assertThat(node.count(index, "{\"term\": {\"_ignored\": \"mixed\"}}"))
                .as("the string in a number array was ignored, its record kept").isEqualTo(1);
        final Map<String, Object> written = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "markdown", "## Purpose and data\n\nShapes, indexed as they are.\n", "change", "Created",
                "stream_ids", List.of(raw)));
        assertThat(String.valueOf(written.get("field_mapping")))
                .as("documented: arrays of objects flattened, renamed keys")
                .contains("Arrays of objects are indexed flattened")
                .contains("`items`")
                .contains("kept as `<field>_original`");

        // The shapes searched through Stroom and in Elasticsearch.
        final String indexDoc = indexDoc(build, index, cluster);
        paired(build, index, indexDoc, List.of(raw), 2, List.of("StreamId", "EventId", "@timestamp", "kind"),
                List.of(
                        // Inside an array of objects.
                        check("items.sku", "EQUALS", "B2", 1, "{\"term\": {\"items.sku\": \"B2\"}}"),
                        check("items.qty", "GREATER_THAN", "1", 1, "{\"range\": {\"items.qty\": {\"gt\": 1}}}"),
                        // An array of numbers, and of arrays.
                        check("scores", "EQUALS", "5", 1, "{\"term\": {\"scores\": 5}}"),
                        check("matrix", "EQUALS", "4", 1, "{\"term\": {\"matrix\": 4}}"),
                        check("deep.l1.l2.l3.l4", "EQUALS", "bottom", 1,
                                "{\"term\": {\"deep.l1.l2.l3.l4\": \"bottom\"}}"),
                        // Was _id.
                        check("id_original", "EQUALS", "src-1", 1, "{\"term\": {\"id_original\": \"src-1\"}}"),
                        check("deep.inner_original", "EQUALS", "in", 1,
                                "{\"term\": {\"deep.inner_original\": \"in\"}}"),
                        // Was a..b.
                        check("a.b", "EQUALS", "two dots", 1, "{\"term\": {\"a.b\": \"two dots\"}}"),
                        check("embedded_list", "EQUALS", "[1, 2, 3]", 1,
                                "{\"term\": {\"embedded_list\": \"[1, 2, 3]\"}}"),
                        // Its odd value ignored.
                        check("mixed", "EQUALS", "3", 1, "{\"term\": {\"mixed\": 3}}"),
                        // Over ignore_above.
                        check("long_text", "EQUALS", "*", 0, "{\"exists\": {\"field\": \"long_text\"}}")),
                pipeline);
        final Map<String, Object> hits = JsonUtil.readValue(node.search(index,
                "{\"query\": {\"term\": {\"kind\": \"order\"}}}").toString(), Map.class);
        final Map<String, Object> source = map((Map<String, Object>) ((List<Object>) map(hits, "hits").get("hits"))
                .getFirst(), "_source");
        assertThat(String.valueOf(source.get("long_text"))).as("the over-long string is in the stored document, "
                                                               + "though not searchable").hasSize(1100);
        assertThat(node.count(index, "{\"bool\": {\"must\": [{\"term\": {\"items.sku\": \"A1\"}}, "
                                     + "{\"term\": {\"items.qty\": 1}}]}}"))
                .as("flattened: sku A1 with qty 1 matches, though A1 had qty 2 (as the documentation warns)")
                .isEqualTo(1);
    }

    // Delimited text or XML, indexed as it is (the script's text_formats).
    private void textFormat(final String kind,
                            final String sample,
                            final String templateName,
                            final Map<String, Object> discovery,
                            final List<Map<String, Object>> checks,
                            final List<String> fields,
                            final Map<String, String> values) {
        final String build = "e2e-discovery-" + kind + "-" + STAMP;
        final String feed = "E2E-" + kind.toUpperCase() + "-RAW-" + STAMP.toUpperCase();
        final String index = indexName(kind);
        createFeed(build, feed);
        final long raw = upload(feed, sample);
        if ("csv".equals(kind)) {
            saveTextConverter(build, feed, textResource("csv_splitter.xml"));
        }
        final String template = discoveryTemplate(templateName);
        final DocRef cluster = liveCluster();
        final Map<String, Object> plan = map(client.json("draft_index_mapping", args("backend", "elasticsearch",
                "index_name", index, "discovery", discovery)), "plan");
        final String pipeline = discoveryPipeline(build, index, plan, template, cluster);
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped).as("stepped the %s records clean: %s", kind, stepped.get("groups"))
                .containsEntry("verdict", "clean")
                .containsEntry("records_stepped", 3);
        assertThat(documents(pipeline, raw, 3)).extracting(document -> document.get("EventId"))
                .as("one document per record, EventId the record number")
                .containsExactly(number("1"), number("2"), number("3"));
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(raw), "example_template", STANDALONE_EXAMPLE));
        assertThat(map(map(agreedTemplate, "template"), "template").get("mappings"))
                .as("text holds numbers as text, so numeric detection is on")
                .satisfies(mappings -> assertThat((Map<String, Object>) mappings)
                        .containsEntry("numeric_detection", true));
        apply((String) agreedTemplate.get("dev_tools"));
        indexStreams(pipeline, List.of(raw));
        node.refresh(index);
        if (!"xml".equals(kind)) {
            final Map<String, Object> mapped = mapping(index);
            assertThat(kind(mapped, "secret")).as("the dropped column was never indexed").isNull();
            assertThat(kind(mapped, "time")).isNotNull();
        }
        final String indexDoc = indexDoc(build, index, cluster);
        paired(build, index, indexDoc, List.of(raw), 3, EXPLICIT, checks, pipeline);
        final Map<String, Object> written = agreed("write_documentation", args("build", build, "pipeline_uuid",
                pipeline, "markdown", "## Purpose and data\n\n" + kind.toUpperCase() + " records, indexed as they are "
                                      + "for exploration.\n", "change", "Created", "stream_ids", List.of(raw)));
        final List<String> documented = new ArrayList<>(EXPLICIT);
        documented.addAll(fields);
        documentedToTheField(uuidOf(written), documented, values);
        assertThat(fieldRows(String.valueOf(written.get("field_mapping")))).as("the dropped column not documented")
                .doesNotContainKey("secret");
    }

    // The discovery XSLT saved from the plan, and the indexing pipeline from the template, as the user agrees it.
    private String discoveryPipeline(final String build,
                                     final String index,
                                     final Map<String, Object> plan,
                                     final String template,
                                     final DocRef cluster) {
        final String xslt = uuidOf(client.json("save_xslt", args("build", build, "name", index + "-XSLT",
                "index_plan", plan)));
        return uuidOf(agreed("create_indexing_pipeline", args("build", build, "name", index + " - Discovery",
                "template_uuid", template, "xslt_uuid", xslt, "index_name", index, "cluster_uuid",
                cluster.getUuid())));
    }

    // The streams indexed by id, as the user approves it, processed, and passed with no Error stream.
    private void indexStreams(final String pipeline, final List<Long> streams) {
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", streams));
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", streams, "expect_events", false, "filter_id", started.get("filter_id"),
                "timeout_seconds", 10));
        assertThat(done).as("indexed with no Error stream: %s", done.get("streams")).containsEntry("gate", "pass");
    }

    // The index doc a discovery build makes once documents are in its index.
    private String indexDoc(final String build, final String index, final DocRef cluster) {
        return uuidOf(agreed("create_index_doc", args("build", build, "backend", "elasticsearch", "name", index,
                "time_field", "@timestamp", "index_name", index, "cluster_uuid", cluster.getUuid())));
    }

    // The same searches through Stroom (verify_index, each hit traced to its record) and directly in Elasticsearch
    // (the script's searching.paired), and the verification dashboard as Stroom kept it.
    private void paired(final String build,
                        final String index,
                        final String indexDoc,
                        final List<Long> streams,
                        final int total,
                        final List<String> fields,
                        final List<Map<String, Object>> checks,
                        final String pipeline) {
        node.refresh(index);
        final List<Map<String, Object>> searches = checks.stream()
                .map(check -> obj("field", check.get("field"), "condition", check.get("condition"), "value",
                        check.get("value"), "expected", check.get("expected")))
                .toList();
        final Map<String, Object> verified = agreed("verify_index", args("build", build, "index_uuid", indexDoc,
                "backend", "elasticsearch", "stream_ids", streams, "expected_documents", total, "fields", fields,
                "searches", searches, "pipeline_uuid", pipeline));
        dashboardDesigned(map(verified, "dashboard"), fields, total);
        final List<Map<String, Object>> results = list(verified, "checks");
        assertThat(results.getFirst()).as("Stroom finds all %s documents of streams %s", total, streams)
                .containsEntry("pass", true);
        for (int i = 0; i < checks.size(); i++) {
            final Map<String, Object> check = checks.get(i);
            final Map<String, Object> result = results.get(i + 1);
            final long direct = node.count(index, (String) check.get("query"));
            assertThat(List.of(number(result.get("returned")), direct))
                    .as("%s %s %s: Stroom %s, Elasticsearch %s, expected %s; trace %s; errors %s",
                            check.get("field"), check.get("condition"), check.get("value"), result.get("returned"),
                            direct, check.get("expected"), result.get("trace"), result.get("errors"))
                    .containsExactly(((Number) check.get("expected")).longValue(),
                            ((Number) check.get("expected")).longValue());
            assertThat(result).containsEntry("pass", true);
        }
    }

    // The verification dashboard as Stroom kept it (the script's dashboard_designed): the user's columns, with
    // StreamId and EventId hidden; newest first; a text pane on the selected row's record, with stepping.
    private void dashboardDesigned(final Map<String, Object> dashboard, final List<String> fields, final int total) {
        if (!Boolean.TRUE.equals(dashboard.get("saved"))) {
            return;
        }
        final DashboardDoc doc = asUser(admin, () -> dashboardStore.readDocument(new DocRef(DashboardDoc.TYPE,
                (String) dashboard.get("uuid"))));
        final Map<String, Object> config = JsonUtil.readValue(JsonUtil.writeValueAsString(doc.getDashboardConfig(),
                false), Map.class);
        final Map<String, Map<String, Object>> components = new LinkedHashMap<>();
        for (final Map<String, Object> component : (List<Map<String, Object>>) config.get("components")) {
            components.put(String.valueOf(component.get("type")), map(component, "settings"));
        }
        final String text = jsonOf(components.get("text"));
        assertThat(components).as("a query, a table and a text pane").containsKeys("query", "table", "text");
        assertThat(text).as("the text pane steps the selected row's record, with no extraction pipeline")
                .contains("\"showStepping\":true");
        final String table = jsonOf(components.get("table"));
        for (final String field : fields) {
            assertThat(table).as("the table holds %s", field).contains("\"" + field + "\"");
        }
        assertThat(total).isPositive();
    }

    // A discovery template (the script's fixture_template, with_json_schema_filter): a parser (JSON, a Data Splitter
    // for delimited text, or XML), one record a split, then an XSLT, a schema filter of group JSON (as the live one
    // validates), and the Elasticsearch indexing filter.
    private String discoveryTemplate(final String name) {
        final ExplorerNode created = create(PipelineDoc.TYPE, name, templateFolder());
        final List<String> parser = PARSERS.get(name);
        final List<List<String>> elements = List.of(parser, List.of("readRecordCountFilter", "RecordCountFilter"),
                List.of("splitFilter", "SplitFilter"), List.of("xsltFilter", "XSLTFilter"),
                List.of("schemaFilter", "SchemaFilter"), List.of("elasticIndexingFilter", "ElasticIndexingFilter"));
        final PipelineDataBuilder data = new PipelineDataBuilder();
        elements.forEach(element -> data.addElement(new PipelineElement(element.getFirst(), element.getLast())));
        for (int i = 1; i < elements.size(); i++) {
            data.addLink(elements.get(i - 1).getFirst(), elements.get(i).getFirst());
        }
        final List<PipelineProperty> properties = new ArrayList<>(List.of(
                property("readRecordCountFilter", "countRead", new PipelinePropertyValue(true)),
                // One record per split, so the record number (EventId) finds exactly one record again.
                property("splitFilter", "splitDepth", new PipelinePropertyValue(1)),
                property("splitFilter", "splitCount", new PipelinePropertyValue(1)),
                property("schemaFilter", "schemaGroup", new PipelinePropertyValue("JSON"))));
        if ("JSONParser".equals(parser.getLast())) {
            properties.addFirst(property("jsonParser", "addRootObject", new PipelinePropertyValue(false)));
        }
        data.addProperties(properties);
        asUser(admin, () -> pipelineStore.writeDocument(pipelineStore.readDocument(created.getDocRef()).copy()
                .pipelineData(data.build())
                .description("Fixture for the ported dev/e2e_discovery.py")
                .build()));
        return created.getUuid();
    }

    // The Elastic Cluster doc for the test node (the script's live_cluster).
    private DocRef liveCluster() {
        final ExplorerNode created = create(ElasticClusterDoc.TYPE, FIXTURE_CLUSTER, templateFolder());
        final ElasticConnectionConfig connection = new ElasticConnectionConfig();
        connection.setConnectionUrls(List.of(node.getUrl()));
        asUser(admin, () -> elasticClusterStore.writeDocument(elasticClusterStore.readDocument(created.getDocRef())
                .copy()
                .connection(connection)
                .build()));
        return created.getDocRef();
    }

    // The cluster admin applies a Dev Tools request the user was given.
    private void apply(final String devTools) {
        final int newline = devTools.indexOf('\n');
        final String[] line = devTools.substring(0, newline).strip().split(" ", 2);
        final String path = line[1].strip();
        if (path.startsWith("_index_template/")) {
            indexTemplates.add(path.substring("_index_template/".length()));
        }
        final ElasticTestNode.Response response = node.request(line[0], "/" + path,
                devTools.substring(newline + 1));
        assertThat(response.isSuccess()).as("%s %s: %s %s", line[0], path, response.status(), response.body())
                .isTrue();
    }

    // An index name of this run's, removed after the test.
    private String indexName(final String purpose) {
        final String index = "stroom-discovery-" + purpose + "-" + STAMP + "-v1";
        indices.add(index);
        return index;
    }

    // An index's mapped properties, as Elasticsearch holds them.
    private Map<String, Object> mapping(final String index) {
        final Map<String, Object> all = JsonUtil.readValue(node.request("GET", "/" + index + "/_mapping", null)
                .body(), Map.class);
        // A data stream's mapping is under its backing index's name.
        final Map<String, Object> found = (Map<String, Object>) all.values().iterator().next();
        return map(map(found, "mappings"), "properties");
    }

    // The documents the pipeline writes for the stream's first records, read from its XSLT's output when stepped
    // (the script's indexing._documents): each value as its kind and text, e.g. string("alice").
    private List<Map<String, Object>> documents(final String pipeline, final long stream, final int count) {
        final List<Map<String, Object>> documents = new ArrayList<>();
        for (int record = 0; record < count; record++) {
            documents.add(jsonFunctions(stepOutput(pipeline, stream, record, "xsltFilter")));
        }
        return documents;
    }

    // The first map of an xpath-functions JSON document (an array of maps).
    private static Map<String, Object> jsonFunctions(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            final Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
                    .getDocumentElement();
            final Object value = "map".equals(root.getLocalName())
                    ? jsonValue(root)
                    : ((List<Object>) jsonValue(root)).getFirst();
            return (Map<String, Object>) value;
        } catch (final Exception e) {
            throw new AssertionError("Not an xpath-functions JSON document: " + xml, e);
        }
    }

    private static Object jsonValue(final Element element) {
        return switch (element.getLocalName()) {
            case "map" -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                children(element).forEach(child -> map.put(child.getAttribute("key"), jsonValue(child)));
                yield map;
            }
            case "array" -> children(element).stream().map(TestE2eDiscovery::jsonValue).toList();
            case "null" -> null;
            default -> List.of(element.getLocalName(), element.getTextContent());
        };
    }

    private static List<Element> children(final Element element) {
        final List<Element> children = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof final Element found && XPATH_FUNCTIONS.equals(found.getNamespaceURI())) {
                children.add(found);
            }
        }
        return children;
    }

    // A string value of a document, as documents() gives it.
    private static List<String> string(final String text) {
        return List.of("string", text);
    }

    // A number value of a document, as documents() gives it.
    private static List<String> number(final String text) {
        return List.of("number", text);
    }

    // The type Elasticsearch mapped a dotted field as ("object" for one with properties), or null if unmapped.
    private static String kind(final Map<String, Object> mapped, final String path) {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("properties", mapped);
        for (final String part : path.split("\\.")) {
            final Object properties = current.get("properties");
            if (!(properties instanceof final Map<?, ?> props) || !(props.get(part) instanceof final Map<?, ?> next)) {
                // A key holding dots (a.b) is mapped under its own name.
                final Object whole = mapped.get(path);
                return whole instanceof final Map<?, ?> field
                        ? String.valueOf(((Map<String, Object>) field).getOrDefault("type", "object"))
                        : null;
            }
            current = (Map<String, Object>) next;
        }
        return String.valueOf(current.getOrDefault("type", "object"));
    }

    private static Map<String, String> kinds(final Map<String, Object> mapped, final Iterable<String> paths) {
        final Map<String, String> kinds = new LinkedHashMap<>();
        paths.forEach(path -> kinds.put(path, kind(mapped, path)));
        return kinds;
    }

    // A search to run both ways: through Stroom, and as an Elasticsearch query.
    private static Map<String, Object> check(final String field,
                                             final String condition,
                                             final String value,
                                             final int expected,
                                             final String query) {
        return obj("field", field, "condition", condition, "value", value, "expected", expected, "query", query);
    }

    private static PipelineProperty property(final String element,
                                             final String name,
                                             final PipelinePropertyValue value) {
        return PipelineProperty.builder().element(element).name(name).value(value).build();
    }

    private static List<String> fieldNames(final Map<String, Object> plan) {
        return ((List<Map<String, Object>>) plan.get("fields")).stream()
                .map(field -> (String) field.get("name"))
                .toList();
    }

    // A JSON object, its keys in the order given.
    private static Map<String, Object> obj(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static String compact(final Object value) {
        return JsonUtil.writeValueAsString(value, false);
    }

    private static String pretty(final Object value) {
        return JsonUtil.writeValueAsString(value, true);
    }

    // The awkward shapes: one record with a value where an earlier one had an object (user), which Elasticsearch
    // must reject, and that must not go unnoticed.
    private static List<Map<String, Object>> shapes() {
        final Map<String, Object> first = obj("ts", "2026-10-02T08:00:00Z", "kind", "order", "user",
                obj("name", "alice"), "items", List.of(obj("sku", "A1", "qty", 2), obj("sku", "B2", "qty", 1)),
                "scores", List.of(3, 5, 8), "matrix", List.of(List.of(1, 2), List.of(3, 4)));
        first.put("nothing", null);
        first.put("empty_list", List.of());
        first.put("empty_obj", Map.of());
        first.put("deep", obj("l1", obj("l2", obj("l3", obj("l4", "bottom"))), "_inner", "in"));
        first.put("key with space", "spaced");
        first.put("embedded_list", "[1, 2, 3]");
        first.put("_id", "src-1");
        first.put("_other", "kept");
        first.put("@timestamp", "theirs");
        first.put("long_text", "x".repeat(1100));
        first.put("", "blank");
        first.put("a..b", "two dots");
        return List.of(first,
                obj("ts", "2026-10-02T08:01:00Z", "kind", "order", "user", "bob"),
                obj("ts", "2026-10-02T08:02:00Z", "kind", "misc", "mixed", List.of(1, "two", 3), "user",
                        obj("name", "carol")));
    }
}
