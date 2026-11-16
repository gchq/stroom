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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Status;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.test.ConfigMapperSpy;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// `eql_sequence` over `/mcp` on a real Stroom and Elasticsearch: two Elastic Index docs with different field names
/// for the same things (a devices index's `user`/`host`, a Sysmon-like index's `SubjectUserName`/`Computer`), seeded
/// with a few users' events, so the hunts have exact answers: a clipboard copy then PowerShell within 2 minutes by
/// the same user on the same host; the same with "and then not cancelled" (a missing step); 3 failed logons then a
/// success (runs). Each review query is run through a Stroom search and must give exactly the hit events. Two more
/// docs, not ECS at all (time field `EventTime`, a hyphenated join field, EQL keywords as field names, no
/// `event.category`), prove the backquoting and the time field against Elasticsearch. A tiny query time limit gives
/// the clear timeout message.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. The indexes made there are removed after each test.
@SuppressWarnings("unchecked")
class TestE2eEqlSequence extends AbstractE2eTest {

    private static final String T0 = "2026-01-01T10:00:";
    private static final String DEVICES_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "@timestamp": {"type": "date"}, "user": {"type": "keyword"}, "host": {"type": "keyword"},
               "action": {"type": "keyword"}, "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
    private static final String SYSMON_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "@timestamp": {"type": "date"}, "SubjectUserName": {"type": "keyword"},
               "Computer": {"type": "keyword"}, "Image": {"type": "keyword"},
               "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
    // Not ECS: another time field, a hyphenated name and EQL keywords (sequence, by) as names; no event.category.
    private static final String PROXY_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "EventTime": {"type": "date"}, "cs-username": {"type": "keyword"},
               "sequence": {"type": "keyword"}, "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
    private static final String AUTH_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "EventTime": {"type": "date"}, "cs-username": {"type": "keyword"},
               "by": {"type": "keyword"}, "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
    private static final Pattern RESULTS_PATH = Pattern.compile("The results are in (\\S+\\.md)\\.");

    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private MetaService metaService;
    @Inject
    private ConfigMapperSpy configMapperSpy;

    private ElasticTestNode node;
    private String devicesIndex;
    private String sysmonIndex;
    private DocRef devices;
    private DocRef sysmon;
    private long devicesStream;
    private long sysmonStream;

    @BeforeEach
    void seed() {
        node = ElasticTestNode.getOrSkip();
        devicesIndex = ElasticTestNode.createIndexName("eql-devices");
        sysmonIndex = ElasticTestNode.createIndexName("eql-sysmon");
        devicesStream = stream();
        sysmonStream = stream();
        node.createIndex(devicesIndex, DEVICES_BODY);
        node.createIndex(sysmonIndex, SYSMON_BODY);
        final ArrayNode deviceEvents = JsonUtil.getMapper().createArrayNode();
        deviceEvents.add(device(1, "00", "alice", "H1", "Copy"));
        deviceEvents.add(device(2, "00", "bob", "H2", "Copy"));
        deviceEvents.add(device(3, "00", "carol", "H3", "Copy"));
        deviceEvents.add(device(4, "00", "frank", "H4", "Copy"));
        deviceEvents.add(device(5, "10", "frank", "H4", "Cancel"));
        deviceEvents.add(device(6, "00", "dave", "H5", "Fail"));
        deviceEvents.add(device(7, "01", "dave", "H5", "Fail"));
        deviceEvents.add(device(8, "02", "dave", "H5", "Fail"));
        deviceEvents.add(device(9, "03", "dave", "H5", "Ok"));
        deviceEvents.add(device(10, "00", "erin", "H6", "Fail"));
        deviceEvents.add(device(11, "01", "erin", "H6", "Fail"));
        deviceEvents.add(device(12, "02", "erin", "H6", "Ok"));
        node.bulkIndex(devicesIndex, deviceEvents);
        final ArrayNode sysmonEvents = JsonUtil.getMapper().createArrayNode();
        sysmonEvents.add(sysmon(1, "2026-01-01T10:00:30Z", "alice", "H1"));
        // Five minutes on: outside a 2-minute window.
        sysmonEvents.add(sysmon(2, "2026-01-01T10:05:00Z", "bob", "H2"));
        sysmonEvents.add(sysmon(3, "2026-01-01T10:00:20Z", "frank", "H4"));
        node.bulkIndex(sysmonIndex, sysmonEvents);
        final DocRef cluster = liveElasticCluster(node, "E2E EQL cluster " + devicesIndex);
        devices = elasticIndex("E2E devices " + devicesIndex, devicesIndex, cluster);
        sysmon = elasticIndex("E2E sysmon " + sysmonIndex, sysmonIndex, cluster);
    }

    @AfterEach
    void removeTheIndexes() {
        configMapperSpy.setConfigValueMappers(Map.of());
        if (node != null) {
            node.deleteIndices(devicesIndex);
            node.deleteIndices(sysmonIndex);
        }
    }

    @Test
    void aCopyThenPowerShellAcrossTwoDocsFindsExactlyItsSequences() {
        final Map<String, Object> result = json("eql_sequence", copyThenPowerShell(false));
        dump("eql_sequence", result);

        assertThat(result).containsEntry("maxspan", "2m").containsEntry("sequence_count", 2)
                .containsEntry("truncated", false);
        // action and Image are each mapped in one doc only, so no step needs the doc condition or its script.
        assertThat((String) result.get("query")).contains("[any where action : \"copy\"]")
                .contains("by SubjectUserName, Computer").doesNotContain("stroom_eql_doc");
        assertThat((String) result.get("note")).contains("at least the sequences there are");
        assertThat(keys(result)).containsExactlyInAnyOrder(List.of("alice", "H1"), List.of("frank", "H4"));
        assertThat((List<Object>) result.get("step_counts")).containsExactly(4L, 3L);
        assertThat((String) result.get("caveat")).contains("straight from Elasticsearch");

        // Each review query, through a Stroom search, finds exactly the hit events.
        final Map<String, Map<String, Object>> reviews = reviews(result);
        assertThat(reviews).hasSize(2);
        assertThat(reviews.values()).allSatisfy(review -> assertThat(review).containsEntry("exact", true)
                .containsEntry("valid", true));
        assertThat((String) reviews.get(devices.getUuid()).get("query"))
                .contains("(StreamId = " + devicesStream + " and EventId in (");
        assertThat(stroomQlIds((String) reviews.get(devices.getUuid()).get("query")))
                .containsExactlyInAnyOrder(devicesStream + ":1", devicesStream + ":4");
        assertThat(stroomQlIds((String) reviews.get(sysmon.getUuid()).get("query")))
                .containsExactlyInAnyOrder(sysmonStream + ":1", sysmonStream + ":3");
        assertThat((String) result.get("review_hint")).contains("ask the user which doc to review");
    }

    @Test
    void aMissingStepLeavesOutTheCancelledCopy() {
        final Map<String, Object> result = json("eql_sequence", copyThenPowerShell(true));

        assertThat(keys(result)).containsExactly(List.of("alice", "H1"));
        assertThat((String) result.get("query")).contains("![any where action == \"Cancel\"] by user, host");
    }

    @Test
    void threeFailuresThenASuccess() {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("maxspan", "1m");
        args.put("steps", List.of(
                Map.of("index", devices.getUuid(), "by", List.of("user"), "runs", 3,
                        "where", List.of(Map.of("field", "action", "value", "Fail"))),
                Map.of("index", devices.getUuid(), "by", List.of("user"),
                        "where", List.of(Map.of("field", "action", "value", "Ok")))));

        final Map<String, Object> result = json("eql_sequence", args);

        assertThat(keys(result)).containsExactly(List.of("dave"));
        assertThat((String) result.get("query")).contains("with runs=3").doesNotContain("stroom_eql_doc");
        final Map<String, Object> review = reviews(result).get(devices.getUuid());
        assertThat(stroomQlIds((String) review.get("query"))).containsExactlyInAnyOrder(devicesStream + ":6",
                devicesStream + ":7", devicesStream + ":8", devicesStream + ":9");
    }

    @Test
    void nonEcsNamesAndAnotherTimeFieldWork() {
        final String proxyIndex = ElasticTestNode.createIndexName("eql-proxy");
        final String authIndex = ElasticTestNode.createIndexName("eql-auth");
        try {
            node.createIndex(proxyIndex, PROXY_BODY);
            node.createIndex(authIndex, AUTH_BODY);
            final ArrayNode proxyEvents = JsonUtil.getMapper().createArrayNode();
            proxyEvents.add(nonEcs(1, "2026-01-01T10:00:00Z", "alice", "sequence", "upload", devicesStream));
            proxyEvents.add(nonEcs(2, "2026-01-01T10:00:00Z", "bob", "sequence", "upload", devicesStream));
            node.bulkIndex(proxyIndex, proxyEvents);
            final ArrayNode authEvents = JsonUtil.getMapper().createArrayNode();
            authEvents.add(nonEcs(1, "2026-01-01T10:00:45Z", "alice", "by", "su", sysmonStream));
            authEvents.add(nonEcs(2, "2026-01-01T10:09:00Z", "bob", "by", "su", sysmonStream));
            node.bulkIndex(authIndex, authEvents);
            // Neither index maps event.category, nor any other ECS field.
            assertThat(node.request("GET", "/" + proxyIndex + "," + authIndex + "/_mapping", null).body())
                    .doesNotContain("event").doesNotContain("@timestamp");
            final DocRef cluster = liveElasticCluster(node, "E2E EQL non-ECS cluster " + proxyIndex);
            final DocRef proxy = elasticIndex("E2E proxy " + proxyIndex, proxyIndex, cluster, "EventTime");
            final DocRef auth = elasticIndex("E2E auth " + authIndex, authIndex, cluster, "EventTime");

            final Map<String, Object> args = new LinkedHashMap<>();
            args.put("maxspan", "2m");
            args.put("steps", List.of(
                    Map.of("index", proxy.getUuid(), "by", List.of("cs-username"), "fields", List.of("sequence"),
                            "where", List.of(Map.of("field", "sequence", "value", "upload"))),
                    Map.of("index", auth.getUuid(), "by", List.of("cs-username"), "fields", List.of("by"),
                            "where", List.of(Map.of("field", "by", "value", "SU", "caseInsensitive", true)))));
            final Map<String, Object> result = json("eql_sequence", args);
            dump("eql_sequence non-ECS", result);

            final String eql = (String) result.get("query");
            assertThat(eql).isEqualTo("""
                    sequence with maxspan=2m
                      [any where `sequence` == "upload"] by `cs-username`
                      [any where `by` : "SU"] by `cs-username`""");
            assertThat(eql).doesNotContain("event.").doesNotContain("@timestamp");
            assertThat(result).containsEntry("time_field", "EventTime").containsEntry("sequence_count", 1);
            assertThat(keys(result)).containsExactly(List.of("alice"));
            final List<String> columns = (List<String>) result.get("columns");
            final List<String> row = ((List<List<String>>) result.get("rows")).getFirst();
            assertThat(row.get(columns.indexOf("s1_time"))).isEqualTo("2026-01-01T10:00:00.000Z");
            assertThat(row.get(columns.indexOf("s2_time"))).isEqualTo("2026-01-01T10:00:45.000Z");
            assertThat(row.get(columns.indexOf("gap_1_2_s"))).isEqualTo("45");
            assertThat(row.get(columns.indexOf("s1_sequence"))).isEqualTo("upload");
            assertThat(row.get(columns.indexOf("s2_by"))).isEqualTo("su");

            final Map<String, Map<String, Object>> reviews = reviews(result);
            assertThat(reviews.values()).allSatisfy(review -> assertThat(review).containsEntry("valid", true)
                    .containsEntry("exact", true));
            final String proxyReview = (String) reviews.get(proxy.getUuid()).get("query");
            assertThat(proxyReview).contains("sort by EventTime").doesNotContain("@timestamp");
            assertThat(stroomQlIds(proxyReview)).containsExactly(devicesStream + ":1");
            assertThat(stroomQlIds((String) reviews.get(auth.getUuid()).get("query")))
                    .containsExactly(sysmonStream + ":1");
        } finally {
            node.deleteIndices(proxyIndex);
            node.deleteIndices(authIndex);
        }
    }

    @Test
    void aTextFieldIsSearchedAsItsDifferentlyNamedKeywordSubfield() {
        // CommandLine is text, its keyword subfield is CommandLine.raw (not .keyword), and Notes is text with no
        // keyword subfield. Also any_of, NOT_IN and length() against the real Elasticsearch.
        final String shellIndex = ElasticTestNode.createIndexName("eql-shell");
        try {
            node.createIndex(shellIndex, """
                    {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
                     "mappings": {"properties": {
                       "@timestamp": {"type": "date"}, "user": {"type": "keyword"},
                       "CommandLine": {"type": "text", "fields": {"raw": {"type": "keyword"}}},
                       "Notes": {"type": "text"},
                       "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""");
            final ArrayNode events = JsonUtil.getMapper().createArrayNode();
            events.add(shell(1, "00", "alice", "Get-Clipboard"));
            events.add(shell(2, "30", "alice", "powershell -enc " + "A".repeat(40)));
            events.add(shell(3, "00", "bob", "Get-Clipboard"));
            events.add(shell(4, "30", "bob", "powershell -c dir"));
            events.add(shell(5, "00", "svc", "Get-Clipboard"));
            events.add(shell(6, "30", "svc", "powershell -enc " + "B".repeat(40)));
            node.bulkIndex(shellIndex, events);
            final DocRef cluster = liveElasticCluster(node, "E2E EQL text cluster " + shellIndex);
            final DocRef shells = elasticIndex("E2E shells " + shellIndex, shellIndex, cluster);

            final Map<String, Object> args = new LinkedHashMap<>();
            args.put("maxspan", "2m");
            args.put("steps", List.of(
                    Map.of("index", shells.getUuid(), "by", List.of("user"),
                            "where", List.of(Map.of("field", "CommandLine", "value", "get-clipboard",
                                    "caseInsensitive", true))),
                    Map.of("index", shells.getUuid(), "by", List.of("user"),
                            "where", List.of(
                                    Map.of("field", "CommandLine", "condition", "STARTS_WITH",
                                            "value", "powershell"),
                                    Map.of("field", "CommandLine", "condition", "GREATER_THAN", "value", "30",
                                            "length", true),
                                    Map.of("field", "user", "condition", "NOT_IN", "value", "svc,system")),
                            "any_of", List.of(
                                    Map.of("field", "CommandLine", "condition", "CONTAINS", "value", "-enc"),
                                    Map.of("field", "CommandLine", "condition", "CONTAINS", "value", "-e ")))));
            final Map<String, Object> result = json("eql_sequence", args);
            dump("eql_sequence text field", result);

            assertThat((String) result.get("query"))
                    .contains("CommandLine.raw : \"get-clipboard\"")
                    .contains("CommandLine.raw like \"powershell*\" and length(CommandLine.raw) > 30 and not user "
                              + "in (\"svc\", \"system\") and (CommandLine.raw like \"*-enc*\" or "
                              + "CommandLine.raw like \"*-e *\")");
            assertThat((List<String>) result.get("field_notes")).singleElement().asString()
                    .contains("its subfield 'CommandLine.raw' (keyword) was searched instead");
            assertThat(keys(result)).containsExactly(List.of("alice"));

            args.put("steps", List.of(
                    Map.of("index", shells.getUuid(), "by", List.of("user"),
                            "where", List.of(Map.of("field", "Notes", "value", "x"))),
                    Map.of("index", shells.getUuid(), "by", List.of("user"))));
            assertThat(McpTestClient.text(client.call("eql_sequence", args)))
                    .contains("'Notes' in Elastic Index 'E2E shells " + shellIndex + "' is a text field, which EQL "
                              + "can't search, and it has no subfields");
        } finally {
            node.deleteIndices(shellIndex);
        }
    }

    @Test
    void stepsOnAFieldBothDocsMapAreKeptToTheirOwnDoc() {
        // Both indexes map action: without the doc condition, step 1 would match the second index's Login too, and
        // bob (whose Logins are both in the second) would be found.
        final String vpnIndex = ElasticTestNode.createIndexName("eql-vpn");
        final String appIndex = ElasticTestNode.createIndexName("eql-app");
        final String body = """
                {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
                 "mappings": {"properties": {
                   "@timestamp": {"type": "date"}, "user": {"type": "keyword"}, "action": {"type": "keyword"},
                   "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
        try {
            node.createIndex(vpnIndex, body);
            node.createIndex(appIndex, body);
            final ArrayNode vpn = JsonUtil.getMapper().createArrayNode();
            vpn.add(login(1, "00", "alice", devicesStream));
            node.bulkIndex(vpnIndex, vpn);
            final ArrayNode app = JsonUtil.getMapper().createArrayNode();
            app.add(login(1, "30", "alice", sysmonStream));
            app.add(login(2, "00", "bob", sysmonStream));
            app.add(login(3, "30", "bob", sysmonStream));
            node.bulkIndex(appIndex, app);
            final DocRef cluster = liveElasticCluster(node, "E2E EQL shared cluster " + vpnIndex);
            final DocRef vpnDoc = elasticIndex("E2E vpn " + vpnIndex, vpnIndex, cluster);
            final DocRef appDoc = elasticIndex("E2E app " + appIndex, appIndex, cluster);

            final Map<String, Object> args = new LinkedHashMap<>();
            args.put("maxspan", "2m");
            args.put("steps", List.of(
                    Map.of("index", vpnDoc.getUuid(), "by", List.of("user"),
                            "where", List.of(Map.of("field", "action", "value", "Login"))),
                    Map.of("index", appDoc.getUuid(), "by", List.of("user"),
                            "where", List.of(Map.of("field", "action", "value", "Login")))));
            final Map<String, Object> result = json("eql_sequence", args);

            assertThat((String) result.get("query")).isEqualTo("""
                    sequence with maxspan=2m
                      [any where stroom_eql_doc == "1" and action == "Login"] by user
                      [any where stroom_eql_doc == "2" and action == "Login"] by user""");
            assertThat(keys(result)).containsExactly(List.of("alice"));
        } finally {
            node.deleteIndices(vpnIndex);
            node.deleteIndices(appIndex);
        }
    }

    @Test
    void withoutAWindowOverMcpItAsksForOne() {
        final Map<String, Object> args = copyThenPowerShell(false);
        args.remove("maxspan");

        assertThat(McpTestClient.text(client.call("eql_sequence", args)))
                .contains("Give maxspan")
                .contains("Within what time of each other must the steps happen?")
                .contains("Suggest 5m (the default)");
    }

    @Test
    void aTinyTimeLimitSaysToNarrowTheWindowOrTheFirstStep() {
        configMapperSpy.setConfigValueMapper(AiConfig.class, config -> {
            final PropertyPath path = Objects.requireNonNullElse(config.getBasePath(),
                    PropertyPath.fromParts("stroom", "ai"));
            return AbstractConfigUtil.mutateTree(config, path, Map.of(path.merge("workspace"),
                    new AiWorkspaceConfig(null, null, null, null, null, null, StroomDuration.ofMillis(1), null,
                            null, null, null, null, null)));
        });
        final Map<String, Object> args = copyThenPowerShell(false);
        args.put("runAnyway", true);

        assertThat(McpTestClient.text(client.call("eql_sequence", args)))
                .contains("narrow the window or the first step");
    }

    // The clipboard-copy-then-PowerShell hunt, optionally with "and then not cancelled".
    private Map<String, Object> copyThenPowerShell(final boolean notCancelled) {
        final List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(Map.of("index", devices.getName(), "by", List.of("user", "host"), "fields", List.of("action"),
                "where", List.of(Map.of("field", "action", "value", "copy", "caseInsensitive", true))));
        if (notCancelled) {
            steps.add(Map.of("index", devices.getUuid(), "by", List.of("user", "host"), "missing", true,
                    "where", List.of(Map.of("field", "action", "value", "Cancel"))));
        }
        steps.add(Map.of("index", sysmon.getUuid(), "by", List.of("SubjectUserName", "Computer"),
                "fields", List.of("Image"),
                "where", List.of(Map.of("field", "Image", "condition", "ENDS_WITH", "value", "POWERSHELL.EXE",
                        "caseInsensitive", true))));
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("steps", steps);
        args.put("maxspan", "2m");
        args.put("timeFrom", "2026-01-01T00:00:00.000Z");
        args.put("timeTo", "2026-01-02T00:00:00.000Z");
        return args;
    }

    // The join keys of each entity found.
    private static List<List<String>> keys(final Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("entities")).stream()
                .map(entity -> (List<String>) entity.get("keys"))
                .toList();
    }

    // The review query of each doc, by its uuid.
    private static Map<String, Map<String, Object>> reviews(final Map<String, Object> result) {
        final Map<String, Map<String, Object>> reviews = new LinkedHashMap<>();
        ((List<Map<String, Object>>) result.get("review_queries"))
                .forEach(review -> reviews.put((String) review.get("uuid"), review));
        return reviews;
    }

    // The StreamId:EventId of each row a StroomQL query gives, through run_stroomql.
    private Set<String> stroomQlIds(final String query) {
        final String text = McpTestClient.text(client.call("run_stroomql", args("query", query)));
        final Matcher matcher = RESULTS_PATH.matcher(text);
        assertThat(matcher.find()).as("run_stroomql wrote its results: %s", text).isTrue();
        final String file = McpTestClient.text(client.call("read_file", args("path", matcher.group(1),
                "count", 1_000)));
        final Set<String> ids = new LinkedHashSet<>();
        List<String> header = null;
        for (final String line : file.split("\n")) {
            final int tab = line.indexOf('\t');
            final String row = tab < 0
                    ? line
                    : line.substring(tab + 1);
            if (!row.startsWith("|") || row.startsWith("| ---") || row.startsWith("|---")) {
                continue;
            }
            final List<String> cells = new ArrayList<>();
            for (final String cell : row.substring(1, row.lastIndexOf('|')).split("\\|")) {
                cells.add(cell.strip());
            }
            if (header == null) {
                header = cells;
            } else {
                ids.add(cells.get(header.indexOf("StreamId")) + ":" + cells.get(header.indexOf("EventId")));
            }
        }
        assertThat(header).as("run_stroomql's table: %s", file).isNotNull();
        return ids;
    }

    private long stream() {
        final List<Long> id = new ArrayList<>();
        asUser(admin, () -> id.add(metaService.create(MetaProperties.builder()
                .feedName("EQL-E2E-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT))
                .typeName(StreamTypeNames.EVENTS)
                .createMs(Instant.now().toEpochMilli())
                .build(), Status.UNLOCKED).getId()));
        return id.getFirst();
    }

    private ObjectNode device(final long eventId,
                              final String second,
                              final String user,
                              final String host,
                              final String action) {
        final ObjectNode event = JsonUtil.getMapper().createObjectNode();
        event.put("_id", "d" + eventId);
        event.put("@timestamp", T0 + second + "Z");
        event.put("user", user);
        event.put("host", host);
        event.put("action", action);
        event.put("StreamId", devicesStream);
        event.put("EventId", eventId);
        return event;
    }

    private ObjectNode sysmon(final long eventId, final String time, final String user, final String host) {
        final ObjectNode event = JsonUtil.getMapper().createObjectNode();
        event.put("_id", "s" + eventId);
        event.put("@timestamp", time);
        event.put("SubjectUserName", user);
        event.put("Computer", host);
        event.put("Image", "C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
        event.put("StreamId", sysmonStream);
        event.put("EventId", eventId);
        return event;
    }

    private ObjectNode login(final long eventId, final String second, final String user, final long streamId) {
        final ObjectNode event = JsonUtil.getMapper().createObjectNode();
        event.put("_id", "l" + eventId);
        event.put("@timestamp", T0 + second + "Z");
        event.put("user", user);
        event.put("action", "Login");
        event.put("StreamId", streamId);
        event.put("EventId", eventId);
        return event;
    }

    private ObjectNode shell(final long eventId, final String second, final String user, final String commandLine) {
        final ObjectNode event = JsonUtil.getMapper().createObjectNode();
        event.put("_id", "c" + eventId);
        event.put("@timestamp", T0 + second + "Z");
        event.put("user", user);
        event.put("CommandLine", commandLine);
        event.put("Notes", "note");
        event.put("StreamId", devicesStream);
        event.put("EventId", eventId);
        return event;
    }

    private DocRef elasticIndex(final String name, final String indexName, final DocRef cluster) {
        return elasticIndex(name, indexName, cluster, "@timestamp");
    }

    private ObjectNode nonEcs(final long eventId,
                              final String time,
                              final String user,
                              final String field,
                              final String value,
                              final long streamId) {
        final ObjectNode event = JsonUtil.getMapper().createObjectNode();
        event.put("_id", field + eventId);
        event.put("EventTime", time);
        event.put("cs-username", user);
        event.put(field, value);
        event.put("StreamId", streamId);
        event.put("EventId", eventId);
        return event;
    }

    private DocRef elasticIndex(final String name,
                                final String indexName,
                                final DocRef cluster,
                                final String timeField) {
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, name, templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField(timeField)
                .build()));
        return created.getDocRef();
    }
}
