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

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` from any source (part 2c) over `/mcp` on a real Stroom and Elasticsearch, on the generated
/// two days of `aggregated/activity-timeline/any-source.csv` (made by `aggregated-feeds-generator.py`): each row an
/// event in an Elasticsearch index (the user as the indexing normalised it, its host, time, StreamId and EventId, and
/// nothing that says what it is) and in an Events stream in Stroom (its EventDetail element, Action and TypeId, a key
/// under the element, the user as the source wrote it, and a wrong time). With no roles given, the events are
/// classified from their Events XML, read by StreamId and EventId: a user seen only in web proxy events (chat 190)
/// gets activity periods, where they used to get nothing; a user with endpoint, proxy and VPN events gets the schema's
/// pairs (a lock to its unlock, a process from its start to its end) and a VPN tunnel's up and down, under names of
/// its own, inferred from their alternation. The user and the time are the index's, never the XML's.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. The index made there is removed after each test.
@SuppressWarnings("unchecked")
class TestE2eActivityTimelineAnySource extends AbstractE2eTest {

    private static final String DATA = "aggregated/activity-timeline/any-source.csv";
    private static final String EXPECTED = "aggregated/activity-timeline/any-source-expected.json";

    @Inject
    private ElasticIndexStore elasticIndexStore;

    private ElasticTestNode node;
    private String indexName;
    private DocRef index;
    private Map<String, Object> expected;

    @BeforeEach
    void seed() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("any-source");
        final List<Map<String, String>> records = EvalCases.records(DATA);
        final List<String> events = new ArrayList<>();
        records.forEach(record -> events.add(EvalQuestionRecords.anySourceEventXml(record)));
        final long stream = storeEvents("ANY-SOURCE-" + indexName.toUpperCase(Locale.ROOT), events);

        final ObjectNode body = JsonUtil.getMapper().createObjectNode();
        body.putObject("settings").put("number_of_shards", 1).put("number_of_replicas", 0);
        final ObjectNode properties = body.putObject("mappings").putObject("properties");
        properties.putObject("EventTime").put("type", "date");
        properties.putObject("UserName").put("type", "keyword");
        properties.putObject("Host").put("type", "keyword");
        properties.putObject("StreamId").put("type", "long");
        properties.putObject("EventId").put("type", "long");
        node.createIndex(indexName, JsonUtil.writeValueAsString(body, false));
        final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
        for (int i = 0; i < records.size(); i++) {
            final ObjectNode document = documents.addObject();
            document.put("_id", String.valueOf(i + 1));
            document.put("EventTime", records.get(i).get("EventTime"));
            document.put("UserName", records.get(i).get("UserName"));
            document.put("Host", records.get(i).get("Host"));
            document.put("StreamId", stream);
            document.put("EventId", i + 1L);
        }
        node.bulkIndex(indexName, documents);
        final DocRef cluster = liveElasticCluster(node, "E2E any-source cluster " + indexName);
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, "E2E any source " + indexName, templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("EventTime")
                .build()));
        index = created.getDocRef();
        expected = expected();
    }

    @AfterEach
    void removeTheIndex() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void aSourceWhoseClusterCantBeReachedIsLeftOutWithItsReasonAndTheOthersAreRead() {
        // Chat 195: one source whose cluster refused ("missing authentication credentials") failed the timeline.
        final DocRef unreachable = elasticCluster("E2E unreachable cluster " + indexName, "http://localhost:1");
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, "E2E unreachable " + indexName, templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(unreachable)
                .indexName(indexName)
                .timeField("EventTime")
                .build()));
        final Map<String, Object> arguments = timeline("pnguyen", Map.of());
        arguments.put("sources", List.of(Map.of("index", index.getUuid(), "user", "UserName", "host", "Host"),
                Map.of("index", created.getDocRef().getUuid(), "user", "UserName", "host", "Host")));

        final Map<String, Object> result = json("activity_timeline", arguments);
        dump("activity_timeline, one source unreachable", result);

        assertThat((List<Map<String, Object>>) result.get("sources")).extracting(source -> source.get("index"))
                .containsExactly(index.getName());
        assertThat((List<Map<String, Object>>) result.get("left_out")).singleElement().satisfies(left -> {
            assertThat(left).containsEntry("index", created.getDocRef().getName());
            assertThat(String.valueOf(left.get("why"))).startsWith("it can't be read: ");
        });
        assertThat(number(result.get("events_read"))).isPositive();
    }

    @Test
    void aUserSeenOnlyInWebProxyEventsGetsActivityPeriodsFromTheirEventsXml() {
        final Map<String, Object> result = json("activity_timeline", timeline("pnguyen", Map.of()));
        dump("activity_timeline, proxy only", result);

        final List<List<String>> periods = (List<List<String>>) expected.get("proxy_only_periods");
        assertThat(result).containsEntry("entity", "pnguyen").containsEntry("session_count", 0)
                .containsEntry("unattached_actions", 0);
        assertThat((Map<String, Object>) result.get("spans")).containsEntry("activity", periods.size());
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("classified_by", Map.of("Events XML",
                ((Map<String, Object>) expected.get("events")).get("pnguyen")));
        // Each planted browsing block is one period, by host and site, from its first request to its last.
        final String spans = read(timelineFolder("pnguyen") + "spans.csv");
        for (final List<String> period : periods) {
            assertThat(spans).containsPattern("," + Pattern.quote(period.get(2).replace(".000Z", "Z") + ","
                                              + period.get(3).replace(".000Z", "Z") + ",") + "[^,]*"
                                              + Pattern.quote(period.get(1)) + ",[^,]*,"
                                              + Pattern.quote(period.get(0) + ",activity,"));
        }
        // The user and the time are the index's: the XML's own (CORP\PNGUYEN, 2001) are never the timeline's.
        final String events = read(timelineFolder("pnguyen") + "events.csv");
        assertThat(events).doesNotContain("PNGUYEN").doesNotContain("2001-").contains(",web-get,Events XML");
        assertThat(node.getOpenSearchContexts(indexName)).isZero();
    }

    @Test
    void aMixedUsersPairsAreTheSchemasAndAVendorsTunnelsInferredFromTheirAlternation() {
        final Map<String, Object> result = json("activity_timeline", timeline("mlee", Map.of("view", "both")));
        dump("activity_timeline, mixed", result);

        final Map<String, Object> mixed = (Map<String, Object>) expected.get("mixed");
        final List<Map<String, Object>> pairs = (List<Map<String, Object>>) result.get("pairs");
        assertThat(pairs).anySatisfy(pair -> assertThat(pair).containsEntry("start", "Authenticate/ScreenLock")
                .containsEntry("origin", "schema").containsEntry("spans", mixed.get("locked")));
        assertThat(pairs).anySatisfy(pair -> assertThat(pair).containsEntry("start", "Process/Execute")
                .containsEntry("origin", "schema").containsEntry("spans", mixed.get("processes")));
        final List<String> inferred = (List<String>) mixed.get("inferred_pair");
        assertThat(pairs).anySatisfy(pair -> assertThat(pair).containsEntry("start", inferred.get(0))
                .containsEntry("end", inferred.get(1)).containsEntry("by", inferred.get(2))
                .containsEntry("origin", "inferred").containsEntry("alternating_percent", 100)
                .containsEntry("spans", mixed.get("tunnels")));
        // Learned, for the skill: offered as a new skill, as the user may create one beside the index.
        final Map<String, Object> learned = (Map<String, Object>) result.get("learned");
        assertThat((List<String>) learned.get("event_chains")).contains("- " + index.getName() + ": pair "
                                                                        + inferred.get(0) + " -> " + inferred.get(1)
                                                                        + " by " + inferred.get(2));
        assertThat(learned).containsEntry("can_record", true);
        // Both levels written; the view's charts given.
        final Map<String, Object> view = (Map<String, Object>) result.get("view");
        assertThat((List<Map<String, Object>>) view.get("gantt_charts")).hasSize(2);
        // Each span's label leads with what happened, its action column saying it whole, by the source's own type
        // names (its TypeIds) where it has them.
        assertThat(read(timelineFolder("mlee") + "spans.csv"))
                .contains(",locked,logon-screenlock to logon-screenunlock,ws-mlee,pair,")
                .contains(",proc-start · notepad.exe,proc-start to proc-end,ws-mlee,pair,")
                .contains(",proc-start · putty.exe,proc-start to proc-end,ws-mlee,pair,")
                .contains(",Tun-Ascend · network,Tun-Ascend to Tun-Recede,ws-mlee,pair,");
        assertThat(read(timelineFolder("mlee") + "activities.csv").lines().findFirst().orElseThrow())
                .isEqualTo("activity,activity_label,day,start,end,active_minutes,spans,actions,lanes,evidence,"
                           + "start_event,end_event");
    }

    // ------------------------------------------------------------------------------------------------

    private static String timelineFolder(final String user) {
        return "/outputs/timelines/" + user + "/";
    }

    private Map<String, Object> timeline(final String user, final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("entity", user);
        arguments.put("timeFrom", expected.get("time_from"));
        arguments.put("timeTo", expected.get("time_to"));
        arguments.put("timeZone", "UTC");
        arguments.put("sources", List.of(Map.of("index", index.getUuid(), "user", "UserName", "host", "Host")));
        arguments.putAll(more);
        return arguments;
    }

    // A workspace file of the session, read a page at a time as an agent would, without its line numbers.
    private String read(final String path) {
        final StringBuilder sb = new StringBuilder();
        int start = 1;
        while (true) {
            final String page = McpTestClient.text(client.call("read_file", Map.of("path", path,
                    "startLine", start, "lineCount", 500)));
            int last = -1;
            for (final String line : page.split("\n")) {
                final int tab = line.indexOf('\t');
                if (tab > 0 && line.substring(0, tab).strip().matches("\\d+")) {
                    last = Integer.parseInt(line.substring(0, tab).strip());
                    sb.append(line.substring(tab + 1)).append('\n');
                }
            }
            if (last < start) {
                return sb.toString();
            }
            start = last + 1;
        }
    }

    private Map<String, Object> expected() {
        try (final InputStream in = TestE2eActivityTimelineAnySource.class.getResourceAsStream(EXPECTED)) {
            return JsonUtil.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), Map.class);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
