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

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexStore;
import stroom.index.impl.Indexer;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Status;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.extraction.FieldValue;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/// `compare_activity` over `/mcp` on a real Stroom and Elasticsearch: one person's working days over 21 weeks among
/// colleagues and an always-on service account, their events quadrupling and three new destinations a day from a
/// known date; the same events in an Elastic Index (aggregated by ES|QL) and a Lucene index (by StroomQL), so both
/// query languages' aggregates are run as the tool writes them. Each finds the step within a few days of its date,
/// counts the new values, and both give the same daily profile. Peers need the people's Event XML, which the
/// timeline dataset generator writes; they are left out here (the index has no stream XML to read).
@SuppressWarnings("unchecked")
class TestE2eCompareActivity extends AbstractE2eTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestE2eCompareActivity.class);
    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 5, 4);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 6, 1);
    private static final LocalDate STEP = LocalDate.of(2026, 5, 11);
    private static final List<String> USUAL = List.of("intranet.example", "mail.example", "wiki.example");
    private static final int NEW_A_DAY = 3;

    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private IndexStore indexStore;
    @Inject
    private Indexer indexer;
    @Inject
    private MetaService metaService;
    @Inject
    private SecurityContext securityContext;

    private ElasticTestNode node;
    private String indexName;

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("compare-activity");
    }

    @AfterEach
    void removeTheIndex() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void aStepAndNewValuesAreFoundByBothQueryLanguagesAlike() {
        final List<Event> events = events();
        final long stream = stream();
        final DocRef elastic = elasticIndex(events, stream);
        final DocRef lucene = luceneIndex(events, stream);

        // Each call writes the same person's files: each is read before the next call.
        final Map<String, Object> viaEsql = compare(elastic, "user.id", "destination.domain");
        final Map<String, String> esqlDays = eventsByDay(viaEsql);
        final String esqlValues = file(viaEsql, 3);
        final Map<String, Object> viaStroomQl = compare(lucene, "UserId", "Destination");
        final Map<String, String> stroomQlDays = eventsByDay(viaStroomQl);
        final String stroomQlValues = file(viaStroomQl, 3);
        for (final Map<String, Object> result : List.of(viaEsql, viaStroomQl)) {
            LOGGER.info(() -> "compare_activity: " + JsonUtil.writeValueAsString(result, true));
            final List<Map<String, Object>> findings = (List<Map<String, Object>>) result.get("findings");
            assertThat(findings).as("findings").anySatisfy(finding -> {
                assertThat(finding).containsEntry("kind", "change").containsEntry("feature", "events");
                assertThat(Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(String.valueOf(finding.get("window"))),
                        STEP))).isLessThanOrEqualTo(3);
            });
            assertThat(findings).anySatisfy(finding -> assertThat(String.valueOf(finding.get("feature")))
                    .startsWith("new:"));
            assertThat(String.valueOf(result.get("note"))).contains("never a conclusion about the person");
            assertThat((List<String>) result.get("flagged_days")).isNotEmpty();
        }
        assertThat(esqlDays).hasSize((int) ChronoUnit.DAYS.between(FROM, WINDOW_TO));
        assertThat(stroomQlDays).isEqualTo(esqlDays);
        // The planted numbers, by day in the person's zone: 2 an hour for 8 hours, 8 an hour from the step.
        assertThat(esqlDays).containsEntry("2026-05-08", "16").containsEntry("2026-05-12", String.valueOf(64
                                                                                                          + NEW_A_DAY))
                .containsEntry("2026-05-09", "0");
        assertThat(esqlValues).contains("destination.domain,48,45,0,new-2026-05-11-0.example; ");
        assertThat(stroomQlValues).contains("Destination,48,45,0,new-2026-05-11-0.example; ");
    }

    private Map<String, Object> compare(final DocRef index, final String personField, final String breadthField) {
        return json("compare_activity", args("person", "p1", "personField", personField, "sources",
                List.of(index.getUuid()), "breadthFields", List.of(breadthField), "windowFrom",
                WINDOW_FROM.toString(), "windowTo", WINDOW_TO.toString(), "baselineFrom", FROM.toString(),
                "timeZone", ZONE.getId(), "peers", false));
    }

    // The profile's events column by date.
    private Map<String, String> eventsByDay(final Map<String, Object> result) {
        final Map<String, String> days = new TreeMap<>();
        final String profile = file(result, 0);
        List<String> header = null;
        for (final String line : profile.split("\n")) {
            // read_file numbers each line ("    12<TAB>...") under a heading line.
            final int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            final String row = line.substring(tab + 1);
            final List<String> cells = List.of(row.split(",", -1));
            if (header == null) {
                header = cells;
                continue;
            }
            if (cells.size() == header.size()) {
                days.put(cells.get(header.indexOf("date")), cells.get(header.indexOf("events")));
            }
        }
        return days;
    }

    private String file(final Map<String, Object> result, final int index) {
        final String path = ((List<String>) result.get("files")).get(index);
        return McpTestClient.text(client.call("read_file", args("path", path, "lineCount", 1_000)));
    }

    // --------------------------------------------------------------------------------
    // The events

    private static List<Event> events() {
        final List<Event> events = new ArrayList<>();
        for (LocalDate date = FROM; date.isBefore(WINDOW_TO); date = date.plusDays(1)) {
            final boolean weekday = date.getDayOfWeek() != DayOfWeek.SATURDAY
                                    && date.getDayOfWeek() != DayOfWeek.SUNDAY;
            for (int hour = 0; hour < 24; hour++) {
                events.add(new Event(at(date, hour, 0), "svc", "updates.example"));
            }
            if (!weekday) {
                continue;
            }
            for (int person = 0; person < 5; person++) {
                for (int hour = 9; hour < 17; hour++) {
                    events.add(new Event(at(date, hour, 10), "other" + person, USUAL.get(person % 3)));
                    events.add(new Event(at(date, hour, 40), "other" + person, USUAL.get((person + 1) % 3)));
                }
            }
            final int perHour = date.isBefore(STEP)
                    ? 2
                    : 8;
            for (int hour = 9; hour < 17; hour++) {
                for (int i = 0; i < perHour; i++) {
                    events.add(new Event(at(date, hour, 5 + 50 * i / perHour), "p1",
                            USUAL.get((hour + i) % 3)));
                }
            }
            if (!date.isBefore(STEP)) {
                for (int i = 0; i < NEW_A_DAY; i++) {
                    events.add(new Event(at(date, 12, 55), "p1", "new-" + date + "-" + i + ".example"));
                }
            }
        }
        return events;
    }

    private static Instant at(final LocalDate date, final int hour, final int minute) {
        return date.atStartOfDay(ZONE).plusHours(hour).plusMinutes(minute).toInstant();
    }

    private DocRef elasticIndex(final List<Event> events, final long stream) {
        node.createIndex(indexName, """
                {"mappings": {"properties": {
                  "@timestamp": {"type": "date"},
                  "user": {"properties": {"id": {"type": "keyword"}}},
                  "destination": {"properties": {"domain": {"type": "keyword"}}},
                  "StreamId": {"type": "long"},
                  "EventId": {"type": "long"}}}}""");
        for (int from = 0; from < events.size(); from += 5_000) {
            final ArrayNode batch = JsonUtil.getMapper().createArrayNode();
            for (int i = from; i < Math.min(events.size(), from + 5_000); i++) {
                final Event event = events.get(i);
                final ObjectNode doc = JsonUtil.getMapper().createObjectNode();
                doc.put("_id", String.valueOf(i));
                doc.put("@timestamp", event.time().toString());
                doc.putObject("user").put("id", event.person());
                doc.putObject("destination").put("domain", event.destination());
                doc.put("StreamId", stream);
                doc.put("EventId", i + 1);
                batch.add(doc);
            }
            node.bulkIndex(indexName, batch);
        }
        final DocRef cluster = liveElasticCluster(node, "E2E compare cluster " + indexName);
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, "E2E compare " + indexName, templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("@timestamp")
                .build()));
        return created.getDocRef();
    }

    private DocRef luceneIndex(final List<Event> events, final long stream) {
        final String name = "E2E compare lucene " + Long.toHexString(System.nanoTime());
        final ExplorerNode created = create(LuceneIndexDoc.TYPE, name, templateFolder());
        final LuceneIndexField streamId = LuceneIndexField.createIdField("StreamId");
        final LuceneIndexField eventId = LuceneIndexField.createIdField("EventId");
        final LuceneIndexField eventTime = LuceneIndexField.builder().fldName("EventTime").fldType(FieldType.DATE)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC).indexed(true).stored(true).build();
        final LuceneIndexField userId = keyword("UserId");
        final LuceneIndexField destination = keyword("Destination");
        final LuceneIndexDoc doc = asUser(admin, () -> {
            indexStore.writeDocument(indexStore.readDocument(created.getDocRef()).copy()
                    .fields(List.of(streamId, eventId, eventTime, userId, destination))
                    .timeField("EventTime")
                    .volumeGroupName("Default Volume Group")
                    .build());
            return indexStore.readDocument(created.getDocRef());
        });
        final IndexShardKey key = IndexShardKey.createKey(doc);
        securityContext.asProcessingUser(() -> {
            for (int i = 0; i < events.size(); i++) {
                final Event event = events.get(i);
                final IndexDocument document = new IndexDocument();
                document.add(new FieldValue(streamId, ValLong.create(stream)));
                document.add(new FieldValue(eventId, ValLong.create(i + 1)));
                document.add(new FieldValue(eventTime, ValDate.create(event.time().toEpochMilli())));
                document.add(new FieldValue(userId, ValString.create(event.person())));
                document.add(new FieldValue(destination, ValString.create(event.destination())));
                indexer.addDocument(key, document);
            }
        });
        flushIndexShards();
        return created.getDocRef();
    }

    private static LuceneIndexField keyword(final String name) {
        return LuceneIndexField.builder().fldName(name).fldType(FieldType.TEXT).analyzerType(AnalyzerType.KEYWORD)
                .caseSensitive(true).indexed(true).stored(true).build();
    }

    private long stream() {
        final List<Long> id = new ArrayList<>();
        asUser(admin, () -> id.add(metaService.create(MetaProperties.builder()
                .feedName("COMPARE-E2E-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT))
                .typeName(StreamTypeNames.EVENTS)
                .createMs(Instant.now().toEpochMilli())
                .build(), Status.UNLOCKED).getId()));
        return id.getFirst();
    }


    // --------------------------------------------------------------------------------


    /// One event.
    ///
    /// @param time        Its time.
    /// @param person      Its person id.
    /// @param destination Its destination.
    private record Event(Instant time, String person, String destination) {

    }
}
