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
import stroom.search.extraction.FieldValue;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `sequence_query` over `/mcp` on a Lucene index, which isn't an Elastic Index, so the sequence runs in Stroom as a
/// StroomQL windowed join: a logon then an export by the same user within 5 minutes, over a few users' events with
/// exact answers (as the benchmark's data shapes, `TestBenchSequenceJoins`, at a small size); and a first step with
/// too many events per user, refused before the join runs.
@SuppressWarnings("unchecked")
class TestE2eSequenceQueryLucene extends AbstractE2eTest {

    private static final String T0 = "2026-01-05T";

    @Inject
    private IndexStore indexStore;
    @Inject
    private Indexer indexer;
    @Inject
    private MetaService metaService;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aLogonThenAnExportWithinFiveMinutesGivesExactlyItsPairs() {
        final List<Event> events = new ArrayList<>();
        // alice: two exports within 5 minutes of her logon, and one after.
        events.add(new Event("10:00:00", "alice", "Logon"));
        events.add(new Event("10:01:00", "alice", "Export"));
        events.add(new Event("10:03:00", "alice", "Export"));
        events.add(new Event("10:07:00", "alice", "Export"));
        // bob: an export before his logon only.
        events.add(new Event("10:59:00", "bob", "Export"));
        events.add(new Event("11:00:00", "bob", "Logon"));
        // carol: an export, no logon.
        events.add(new Event("12:00:00", "carol", "Export"));
        // dave: just inside the window.
        events.add(new Event("13:00:00", "dave", "Logon"));
        events.add(new Event("13:04:59", "dave", "Export"));
        // erin: a logon and a read, not an export.
        events.add(new Event("14:00:00", "erin", "Logon"));
        events.add(new Event("14:01:00", "erin", "Read"));
        final String index = lucene("E2E sequence logons", events);

        final Map<String, Object> result = json("sequence_query", logonThenExport(index, "Logon"));
        dump("sequence_query Lucene", result);

        assertThat(result).containsEntry("engine", "StroomQL").containsEntry("maxspan", "5m")
                .containsEntry("sequence_count", 3).containsEntry("entity_count", 2)
                .containsEntry("truncated", false).containsEntry("first_step_keys", 4L);
        assertThat((List<Object>) result.get("step_counts")).containsExactly(4L);
        assertThat((String) result.get("engine_reason")).contains("isn't an Elastic Index")
                .contains("windowed join");
        assertThat((String) result.get("query")).contains("join \"" + index + "\" as e on a.UserId = e.UserId "
                                                          + "within 5m after a.EventTime");
        final Map<String, Long> pairsByUser = new LinkedHashMap<>();
        ((List<Map<String, Object>>) result.get("entities")).forEach(entity -> pairsByUser.put(
                ((List<String>) entity.get("keys")).getFirst(), ((Number) entity.get("sequences")).longValue()));
        assertThat(pairsByUser).containsExactly(Map.entry("alice", 2L), Map.entry("dave", 1L));
        final List<String> columns = (List<String>) result.get("columns");
        final List<List<String>> rows = (List<List<String>>) result.get("rows");
        assertThat(rows).extracting(row -> row.get(columns.indexOf("UserId")) + " "
                                           + row.get(columns.indexOf("s1_time")) + " "
                                           + row.get(columns.indexOf("s2_time")))
                .containsExactly(
                        "alice 2026-01-05T10:00:00.000Z 2026-01-05T10:01:00.000Z",
                        "alice 2026-01-05T10:00:00.000Z 2026-01-05T10:03:00.000Z",
                        "dave 2026-01-05T13:00:00.000Z 2026-01-05T13:04:59.000Z");
        assertThat(rows).allSatisfy(row -> assertThat(row.get(columns.indexOf("s2_EventId"))).isNotBlank());
        // The join is the review query, valid StroomQL to save as a Query.
        assertThat((List<Map<String, Object>>) result.get("review_queries")).singleElement()
                .satisfies(review -> assertThat(review).containsEntry("valid", true)
                        .containsEntry("query", result.get("query")));
        // And it gives the same pairs when the user runs it.
        final String ran = McpTestClient.text(client.call("run_stroomql", args("query", result.get("query"))));
        assertThat(ran).contains("Ran the query").contains("3 rows");
    }

    @Test
    void aFirstStepWithTooManyEventsPerUserIsRefusedBeforeTheJoinRuns() {
        final List<Event> events = new ArrayList<>();
        // One user's 10,001 probes: more than 10,000 first-step events, and more than 100 for each user.
        for (int i = 0; i < 10_001; i++) {
            events.add(new Event(String.format(Locale.ROOT, "%02d:%02d:%02d", 10 + i / 3600, i / 60 % 60, i % 60),
                    "zed", "Probe"));
        }
        events.add(new Event("10:00:30", "zed", "Export"));
        final String index = lucene("E2E sequence probes", events);

        final String refused = McpTestClient.text(client.call("sequence_query", logonThenExport(index, "Probe")));

        assertThat(refused).contains("Not run: step 1 matches 10001 events, with 1 distinct join value")
                .contains("Narrow the first step")
                .contains("join \"" + index + "\" as e");
    }

    @Test
    void whatAWindowedJoinCantDoIsRefusedSayingItNeedsAnElasticIndex() {
        final String index = lucene("E2E sequence refusals", List.of(new Event("10:00:00", "alice", "Logon")));
        final Map<String, Object> args = logonThenExport(index, "Logon");
        final List<Map<String, Object>> steps = new ArrayList<>((List<Map<String, Object>>) args.get("steps"));
        steps.add(steps.getLast());
        args.put("steps", steps);

        assertThat(McpTestClient.text(client.call("sequence_query", args))).contains("this has 3 steps")
                .contains("Elastic Index");
    }

    private static Map<String, Object> logonThenExport(final String index, final String firstAction) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("steps", List.of(
                Map.of("index", index, "by", List.of("UserId"),
                        "where", List.of(Map.of("field", "Action", "value", firstAction))),
                Map.of("index", index, "by", List.of("UserId"),
                        "where", List.of(Map.of("field", "Action", "value", "Export")))));
        args.put("maxspan", "5m");
        args.put("timeFrom", "2026-01-05T00:00:00.000Z");
        args.put("timeTo", "2026-01-06T00:00:00.000Z");
        return args;
    }

    // A Lucene index of the events, as the benchmark makes one, flushed so searches see them; its name.
    private String lucene(final String label, final List<Event> events) {
        final String name = label + " " + Long.toHexString(System.nanoTime());
        final ExplorerNode created = create(LuceneIndexDoc.TYPE, name, templateFolder());
        final LuceneIndexField streamId = LuceneIndexField.createIdField("StreamId");
        final LuceneIndexField eventId = LuceneIndexField.createIdField("EventId");
        final LuceneIndexField eventTime = LuceneIndexField.builder().fldName("EventTime").fldType(FieldType.DATE)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC).indexed(true).stored(true).build();
        final LuceneIndexField userId = keyword("UserId");
        final LuceneIndexField action = keyword("Action");
        final LuceneIndexDoc doc = asUser(admin, () -> {
            indexStore.writeDocument(indexStore.readDocument(created.getDocRef()).copy()
                    .fields(List.of(streamId, eventId, eventTime, userId, action))
                    .timeField("EventTime")
                    .volumeGroupName("Default Volume Group")
                    .build());
            return indexStore.readDocument(created.getDocRef());
        });
        final long stream = stream();
        final IndexShardKey key = IndexShardKey.createKey(doc);
        securityContext.asProcessingUser(() -> {
            for (int i = 0; i < events.size(); i++) {
                final Event event = events.get(i);
                final IndexDocument document = new IndexDocument();
                document.add(new FieldValue(streamId, ValLong.create(stream)));
                document.add(new FieldValue(eventId, ValLong.create(i + 1)));
                document.add(new FieldValue(eventTime, ValDate.create(Instant.parse(T0 + event.time() + "Z")
                        .toEpochMilli())));
                document.add(new FieldValue(userId, ValString.create(event.user())));
                document.add(new FieldValue(action, ValString.create(event.action())));
                indexer.addDocument(key, document);
            }
        });
        flushIndexShards();
        return name;
    }

    private static LuceneIndexField keyword(final String name) {
        return LuceneIndexField.builder().fldName(name).fldType(FieldType.TEXT).analyzerType(AnalyzerType.KEYWORD)
                .caseSensitive(true).indexed(true).stored(true).build();
    }

    private long stream() {
        final List<Long> id = new ArrayList<>();
        asUser(admin, () -> id.add(metaService.create(MetaProperties.builder()
                .feedName("SEQ-E2E-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT))
                .typeName(StreamTypeNames.EVENTS)
                .createMs(Instant.now().toEpochMilli())
                .build(), Status.UNLOCKED).getId()));
        return id.getFirst();
    }


    // --------------------------------------------------------------------------------


    /// One event.
    ///
    /// @param time   Its time on 2026-01-05, `HH:mm:ss`.
    /// @param user   Its user.
    /// @param action What it did.
    private record Event(String time, String user, String action) {

    }
}
