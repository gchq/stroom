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
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.catalogue.TimelineRoles;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.test.ConfigMapperSpy;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PropertyPath;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` reading many feeds (decided with the user 2026-11-25), over `/mcp` on a real Stroom and
/// Elasticsearch: an index fed by two feeds through two versions of its indexing pipeline whose index plans put the
/// host in different fields (one timeline, each event's host from its own version's field, each stream traced to its
/// version by the processor filters, a feed neither selects read from its Events XML, the roles from the AGENTS
/// doc's `## Event types`); a source whose types the catalogue of its streams' translation gives roles (no typeRoles,
/// no model, no Events XML read); and a period with more events than a timeline reads, given a day at a time with
/// none read.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. The indexes made there are removed after each test.
@SuppressWarnings("unchecked")
class TestE2eTimelineManyFeeds extends AbstractE2eTest {

    private static final String DAY = "2026-11-10";
    // An indexing XSLT, never run here: its plan is what the timeline reads.
    private static final String INDEXING = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                           + "version=\"3.0\"><xsl:template match=\"/\"/></xsl:stylesheet>";
    // One Events record, whose stream the documents name.
    private static final String EVENT = "<Event><EventTime><TimeCreated>2026-11-10T09:00:00.000Z</TimeCreated>"
                                        + "</EventTime><EventDetail><TypeId>web-get</TypeId><View><Resource><URL>"
                                        + "https://intranet.example.org/</URL></Resource></View></EventDetail>"
                                        + "</Event>";

    // The hand-written translation of a VPN's JSON audit, and its records.
    private static final String VPN_XSLT = textResource("hand_written_vpn.xsl");
    private static final String VPN_SAMPLE = "["
            + "{\"ts\": \"2026-11-03T08:00:01.000Z\", \"action\": \"login\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\", \"result\": \"success\"}, "
            + "{\"ts\": \"2026-11-03T12:10:00.000Z\", \"action\": \"logout\", \"user\": \"jbloggs\", "
            + "\"src_ip\": \"198.51.100.7\", \"host\": \"vpn-gw-1\"}]";

    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ConfigMapperSpy configMapperSpy;

    private ElasticTestNode node;
    private final List<String> indexNames = new ArrayList<>();

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
    }

    @AfterEach
    void removeTheIndexes() {
        configMapperSpy.setConfigValueMappers(Map.of());
        if (node != null) {
            indexNames.forEach(node::deleteIndices);
        }
    }

    @Test
    void twoFeedsThroughTwoPipelineVersionsGiveOneTimelineEachEventWithItsOwnVersionsField() {
        final ExplorerNode folder = folder("System/Feeds/TlTwo");
        final String indexName = index("tl-two");
        final DocRef cluster = liveElasticCluster(node, "E2E timeline cluster " + indexName);
        final DocRef index = elasticIndex(folder, "TLTWO-INDEX", cluster, indexName);
        final String template = elasticEventsTemplate("TLTWO Elastic indexing", "");
        final String v10 = indexing(folder, "TLTWO-V1.0 - Indexing", template, cluster, indexName, "HostName");
        final String v11 = indexing(folder, "TLTWO-V1.1 - Indexing", template, cluster, indexName, "host.name");
        // Each version indexes its own feed's Events streams, by its processor filter.
        feedFilter("TLTWO-A", "Events", v10);
        feedFilter("TLTWO-B", "Events", v11);
        documentationDoc(folder, "AGENTS", "# Standing instructions\n\n## Event types\n- web-get: action\n");
        final long older = storeEvents("TLTWO-A", List.of(EVENT));
        final long newer = storeEvents("TLTWO-B", List.of(EVENT));
        // A feed neither filter selects: its events' host read from their Events XML.
        final List<String> withHosts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            withHosts.add(EVENT.replace("<EventDetail>", "<EventSource><Device><HostName>vm-c</HostName></Device>"
                                                       + "</EventSource><EventDetail>"));
        }
        final long unfiltered = storeEvents("TLTWO-C", withHosts);
        final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
        // The older version's feed, then the newer's: each document holds its own version's host field.
        for (int i = 0; i < 6; i++) {
            final ObjectNode doc = document(documents, i, "09:0" + i, older, "pat", "web-get",
                    "https://intranet.example.org/" + i);
            doc.put("HostName", "ws-a");
        }
        for (int i = 0; i < 6; i++) {
            final ObjectNode doc = document(documents, 10 + i, "11:0" + i, newer, "pat", "web-get",
                    "https://supplier.example.net/" + i);
            doc.putObject("host").put("name", "lt-b");
            // The newer version's documents hold HostName too, meaning something else there.
            doc.put("HostName", "relay-01");
        }
        // Records 1 to 3 of the unfiltered stream; their documents' HostName a relay's, never taken as the host.
        for (int i = 0; i < 3; i++) {
            document(documents, 20 + i, "13:0" + i, unfiltered, "pat", "web-get", null)
                    .put("EventId", (long) i + 1).put("HostName", "relay-02");
        }
        node.bulkIndex(indexName, documents);

        final Map<String, Object> result = json("activity_timeline", args("entity", "pat", "sources",
                List.of(index.getUuid()), "timeFrom", DAY + "T00:00:00Z", "timeTo", DAY + "T18:00:00Z",
                "timeZone", "UTC"));
        dump("activity_timeline, two versions", result);

        assertThat((List<String>) result.get("span_hosts")).containsExactlyInAnyOrder("ws-a", "lt-b", "vm-c");
        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat(source).containsEntry("user_field", "UserId");
        // Each stream traced once to its version by the filters; the unfiltered feed's events read from their XML.
        assertThat((Map<String, Object>) ((Map<String, Object>) source.get("fields_by_pipeline")).get("host"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("HostName (traced by the processor filters)", 6L,
                        "host.name (traced by the processor filters)", 6L,
                        "EventSource/Device/HostName (the Events XML)", 3L));
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys("AGENTS doc");
        // One scan of the source; its types settled by the AGENTS doc, so no Events XML read for them.
        assertThat(result).containsEntry("scans", 1).containsEntry("events_read", 15);
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("read", 0);
    }

    @Test
    void aSourceWhoseTranslationsCatalogueGivesItsTypesRolesNeedsNoTypeRolesNorItsXml() {
        final String folderPath = "System/Feeds/TlVpn";
        final HandWritten vpn = handWritten(folderPath, "TLVPN", VPN_XSLT, "TLVPN-EVENTS", "Event Data (JSON)",
                List.of(PipelineProperty.builder()
                        .element("jsonParser")
                        .name("addRootObject")
                        .value(new PipelinePropertyValue(false))
                        .build()), "Raw Events", VPN_SAMPLE);
        // The translation's catalogue, in the source notes doc its XSLT links to, as the tools keep them.
        final Catalogue catalogue = TimelineRoles.filled(new Catalogue(List.of(
                type("vpn-login", "Authenticate", "Logon"),
                type("vpn-logout", "Authenticate", "Logoff"),
                type("vpn-config-change", "Unknown", null).withTimelineRole("action", TimelineRoles.FROM_GIVEN)),
                null, null));
        final String block = SourceNotes.block(new LinkedHashMap<>(catalogue.toJson()));
        final String notes = documentationDoc(folderPath, "TLVPN source notes", "# TLVPN source notes\n\n" + block
                                                                                 + "\n");
        editXslt(vpn.xslt(), doc -> doc.copy().description(KeptMapping.withSourceNotes("Hand-written.",
                new DocRef(DocumentationDoc.TYPE, notes, "TLVPN source notes"))).build());
        processAll();
        final long events = streamsOf("TLVPN").stream()
                .filter(meta -> "Events".equals(meta.getTypeName()))
                .map(Meta::getId)
                .findFirst()
                .orElseThrow();
        documentationDoc(folderPath, "AGENTS", "# Standing instructions\n\n## Fields\n- event type: TypeId\n"
                                               + "- host: HostName\n");
        final String indexName = index("tl-vpn");
        final DocRef cluster = liveElasticCluster(node, "E2E timeline cluster " + indexName);
        final DocRef index = elasticIndex(folder(folderPath), "TLVPN-INDEX", cluster, indexName);
        final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
        document(documents, 0, "08:00", events, "jbloggs", "vpn-login", null).put("HostName", "vpn-gw-1");
        document(documents, 1, "09:30", events, "jbloggs", "vpn-config-change", null).put("HostName", "vpn-gw-1");
        document(documents, 2, "12:10", events, "jbloggs", "vpn-logout", null).put("HostName", "vpn-gw-1");
        node.bulkIndex(indexName, documents);

        final Map<String, Object> result = json("activity_timeline", args("entity", "jbloggs", "sources",
                List.of(index.getUuid()), "timeFrom", DAY + "T00:00:00Z", "timeTo", DAY + "T18:00:00Z",
                "timeZone", "UTC"));
        dump("activity_timeline, catalogue roles", result);

        final Map<String, Object> source = ((List<Map<String, Object>>) result.get("sources")).getFirst();
        assertThat((Map<String, Object>) source.get("roles_from")).containsOnlyKeys("catalogue");
        assertThat(source).containsEntry("classified_by", Map.of("event type field", 3));
        assertThat((Map<String, Object>) result.get("events_xml")).containsEntry("read", 0);
        assertThat(result).doesNotContainKey("event_types_lines_to_offer").containsEntry("events_read", 3);
    }

    @Test
    void aPeriodWithMoreEventsThanATimelineReadsIsGivenADayAtATimeWithNoneRead() {
        maxEvents(20);
        final ExplorerNode folder = folder("System/Feeds/TlLong");
        final String indexName = index("tl-long");
        final DocRef cluster = liveElasticCluster(node, "E2E timeline cluster " + indexName);
        final DocRef index = elasticIndex(folder, "TLLONG-INDEX", cluster, indexName);
        final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
        int id = 0;
        for (final String day : List.of("2026-11-09", "2026-11-10", "2026-11-12")) {
            for (int i = 0; i < 15; i++) {
                final ObjectNode doc = documents.addObject();
                doc.put("_id", String.valueOf(++id));
                doc.put("EventTime", day + "T09:" + (10 + i) + ":00.000Z");
                doc.put("UserId", "pat");
                doc.put("TypeId", i % 3 == 0
                        ? "web-post"
                        : "web-get");
                doc.put("HostName", "ws-a");
            }
        }
        node.bulkIndex(indexName, documents);

        final Map<String, Object> result = json("activity_timeline", args("entity", "pat", "sources",
                List.of(Map.of("index", index.getUuid(), "user", "UserId", "eventTypeField", "TypeId")),
                "timeFrom", "2026-11-09", "timeTo", "2026-11-12", "timeZone", "UTC"));
        dump("activity_timeline, overview", result);

        assertThat(result).containsEntry("overview", true).containsEntry("active_days", 3)
                .doesNotContainKey("events_read");
        assertThat((List<Map<String, Object>>) result.get("days")).extracting(day -> day.get("day"))
                .containsExactly("2026-11-09", "2026-11-10", "2026-11-12");
        assertThat((List<Map<String, Object>>) result.get("days")).allSatisfy(day ->
                assertThat(((Number) day.get("events")).intValue()).isEqualTo(15));
        final String overview = McpTestClient.text(client.call("read_file", args("path", result.get("file"),
                "lineCount", 100)));
        assertThat(overview).contains("2026-11-10,TLLONG-INDEX,web-get,10").contains("2026-11-10,TLLONG-INDEX,"
                                                                                       + "web-post,5");
        assertThat(node.getOpenSearchContexts(indexName)).isZero();
    }

    // ------------------------------------------------------------------------------------------------

    // A test index with the fields of every test here, removed afterwards.
    private String index(final String prefix) {
        final String indexName = ElasticTestNode.createIndexName(prefix);
        indexNames.add(indexName);
        node.createIndex(indexName, """
                {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
                 "mappings": {"properties": {
                  "EventTime": {"type": "date"},
                  "UserId": {"type": "keyword"},
                  "HostName": {"type": "keyword"},
                  "host": {"properties": {"name": {"type": "keyword"}}},
                  "TypeId": {"type": "keyword"},
                  "url": {"type": "keyword"},
                  "StreamId": {"type": "long"},
                  "EventId": {"type": "long"}}}}""");
        return indexName;
    }

    private DocRef elasticIndex(final ExplorerNode folder,
                                final String name,
                                final DocRef cluster,
                                final String indexName) {
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, name, folder);
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("EventTime")
                .build()));
        return created.getDocRef();
    }

    // An indexing pipeline writing the index, its XSLT keeping a plan that puts the host in the field given: its
    // UUID.
    private String indexing(final ExplorerNode folder,
                          final String name,
                          final String template,
                          final DocRef cluster,
                          final String indexName,
                          final String hostField) {
        final String xslt = create(XsltDoc.TYPE, name, folder).getUuid();
        final Map<String, Object> plan = Map.of("backend", FieldPlan.ELASTICSEARCH, "index_name", indexName,
                "time_field", "EventTime", "fields", List.of(
                        Map.of("name", "StreamId", "type", "long", "source", "@StreamId"),
                        Map.of("name", "EventId", "type", "long", "source", "@EventId"),
                        Map.of("name", "EventTime", "type", "date", "source", "EventTime/TimeCreated"),
                        Map.of("name", "UserId", "type", "keyword", "source", "EventSource/User/Id"),
                        Map.of("name", hostField, "type", "keyword", "source", "EventSource/Device/HostName"),
                        Map.of("name", "TypeId", "type", "keyword", "source", "EventDetail/TypeId"),
                        Map.of("name", "url", "type", "keyword", "source", "EventDetail/View/Resource/URL")));
        editXslt(xslt, doc -> doc.copy().data(INDEXING).description(KeptMapping.withMapping("", "index", plan))
                .build());
        return childPipeline(folder, name, template, List.of(
                property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, xslt)),
                property("elasticIndexingFilter", "cluster", cluster),
                PipelineProperty.builder()
                        .element("elasticIndexingFilter")
                        .name("indexName")
                        .value(new PipelinePropertyValue(indexName))
                        .build()));
    }

    private static ObjectNode document(final ArrayNode documents,
                                       final int id,
                                       final String time,
                                       final long stream,
                                       final String user,
                                       final String typeId,
                                       final String url) {
        final ObjectNode doc = documents.addObject();
        doc.put("_id", String.valueOf(id + 1));
        doc.put("EventTime", Instant.parse(DAY + "T" + time + ":00Z").toString());
        doc.put("UserId", user);
        doc.put("TypeId", typeId);
        if (url != null) {
            doc.put("url", url);
        }
        doc.put("StreamId", stream);
        // A record each, as an event counted once by its stream and record.
        doc.put("EventId", (long) id + 1);
        return doc;
    }

    private static EventType type(final String typeId, final String element, final String action) {
        return new EventType(typeId, typeId, null, element, action, null, null, null, null, List.of(), false,
                EventType.AGENT_KNOWLEDGE, "high", null, EventType.CONFIRMED);
    }

    // The most events a timeline reads, as an administrator would set it.
    private void maxEvents(final int most) {
        configMapperSpy.setConfigValueMapper(AiConfig.class, config -> {
            final PropertyPath path = Objects.requireNonNullElse(config.getBasePath(),
                    PropertyPath.fromParts("stroom", "ai"));
            return AbstractConfigUtil.mutateTree(config, path, Map.of(path.merge("workspace"),
                    new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                            null, null, null, null, null, null, null, null, most, null, null, null)));
        });
    }
}
