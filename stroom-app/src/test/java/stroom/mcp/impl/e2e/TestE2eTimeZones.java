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

import stroom.config.global.impl.UserPreferencesServiceImpl;
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
import stroom.query.api.UserTimeZone;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.security.api.SecurityContext;
import stroom.ui.config.shared.UserPreferences;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// The query tools' time zones over `/mcp`, on a Lucene index with events at known times either side of the start
/// of today in Australia/Canberra: `day()` and a time without an offset are worked out in the zone the call gives
/// (`timeZone`), or else the user's Stroom time zone preference, and each result says which zone its times are in.
@SuppressWarnings("unchecked")
class TestE2eTimeZones extends AbstractE2eTest {

    private static final ZoneId CANBERRA = ZoneId.of("Australia/Canberra");

    @Inject
    private IndexStore indexStore;
    @Inject
    private Indexer indexer;
    @Inject
    private MetaService metaService;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private UserPreferencesServiceImpl userPreferencesService;

    @Test
    void dayIsTheStartOfTodayInTheZoneGiven() {
        final Instant canberraDay = ZonedDateTime.now(CANBERRA).truncatedTo(ChronoUnit.DAYS).toInstant();
        final Instant utcDay = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        final List<Event> events = List.of(
                new Event(canberraDay.minus(1, ChronoUnit.HOURS), "yesterday-in-canberra"),
                new Event(canberraDay.plus(1, ChronoUnit.HOURS), "today-in-canberra"));
        final String index = lucene("E2E time zones day", events);
        final String query = "from \"" + index + "\" where EventTime > day() select UserId, EventTime";

        final String inCanberra = run(query, "Australia/Canberra");

        assertThat(inCanberra).contains("Times are in Australia/Canberra (+1").contains("1 row")
                .contains("today-in-canberra").doesNotContain("yesterday-in-canberra")
                // The result's times are shown in the zone.
                .contains(ZonedDateTime.ofInstant(canberraDay.plus(1, ChronoUnit.HOURS), CANBERRA).toLocalDate()
                          + "T01:00:00.000+1");

        final String inUtc = run(query, "UTC");
        assertThat(inUtc).contains("Times are in UTC.");
        events.stream()
                .filter(event -> event.time().isAfter(utcDay))
                .forEach(event -> assertThat(inUtc).contains(event.user()));
    }

    @Test
    void aTimeWithoutAnOffsetIsInTheZoneGiven() {
        final ZonedDateTime canberraDay = ZonedDateTime.now(CANBERRA).truncatedTo(ChronoUnit.DAYS);
        final String index = lucene("E2E time zones local time", List.of(
                new Event(canberraDay.plusMinutes(30).toInstant(), "before-one"),
                new Event(canberraDay.plusMinutes(90).toInstant(), "after-one")));
        final String query = "from \"" + index + "\" where EventTime >= \"" + canberraDay.toLocalDate()
                             + "T01:00:00\" select UserId";

        assertThat(run(query, "Australia/Canberra")).contains("after-one").doesNotContain("before-one");
        assertThat(run(query, "+11:00")).contains("Times are in +11:00.");
    }

    @Test
    void theDefaultIsTheUsersPreferenceAndALocalPreferenceIsUtcOverMcp() {
        final Instant canberraDay = ZonedDateTime.now(CANBERRA).truncatedTo(ChronoUnit.DAYS).toInstant();
        final String index = lucene("E2E time zones preference", List.of(
                new Event(canberraDay.minus(1, ChronoUnit.HOURS), "yesterday-in-canberra"),
                new Event(canberraDay.plus(1, ChronoUnit.HOURS), "today-in-canberra")));
        final String query = "from \"" + index + "\" where EventTime > day() select UserId";

        final String preferred = withPreference(UserTimeZone.fromId("Australia/Canberra"), () -> run(query, null));
        assertThat(preferred).contains("the user's Stroom time zone").contains("today-in-canberra")
                .doesNotContain("yesterday-in-canberra");
        assertThat(withPreference(UserTimeZone.fromId("Australia/Canberra"),
                () -> McpTestClient.text(client.call("get_current_datetime", args()))))
                .contains("(Australia/Canberra); UTC ");

        final String local = withPreference(UserTimeZone.local(), () -> run(query, "local"));
        assertThat(local).contains("Times are in UTC. The user's Stroom time zone is their browser's, which isn't "
                                   + "known here");
    }

    @Test
    void aSequencesTimeRangeAndTimesAreInTheZoneGiven() {
        final Instant canberraDay = ZonedDateTime.now(CANBERRA).truncatedTo(ChronoUnit.DAYS).toInstant();
        final String index = lucene("E2E time zones sequence", List.of(
                new Event(canberraDay.minus(30, ChronoUnit.MINUTES), "alice", "Logon"),
                new Event(canberraDay.minus(28, ChronoUnit.MINUTES), "alice", "Export"),
                new Event(canberraDay.plus(60, ChronoUnit.MINUTES), "bob", "Logon"),
                new Event(canberraDay.plus(62, ChronoUnit.MINUTES), "bob", "Export")));
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("steps", List.of(
                Map.of("index", index, "by", List.of("UserId"),
                        "where", List.of(Map.of("field", "Action", "value", "Logon"))),
                Map.of("index", index, "by", List.of("UserId"),
                        "where", List.of(Map.of("field", "Action", "value", "Export")))));
        arguments.put("maxspan", "5m");
        arguments.put("timeFrom", "day()");
        arguments.put("timeZone", "Australia/Canberra");

        final Map<String, Object> result = json("sequence_query", arguments);
        dump("sequence_query in Canberra", result);

        assertThat(result).containsEntry("engine", "StroomQL").containsEntry("sequence_count", 1)
                .containsEntry("time_zone", "Australia/Canberra");
        final String canberraDate = ZonedDateTime.ofInstant(canberraDay, CANBERRA).toLocalDate().toString();
        assertThat((String) ((Map<String, Object>) result.get("time_range")).get("from"))
                .startsWith(canberraDate + "T00:00:00+1");
        final List<String> columns = (List<String>) result.get("columns");
        assertThat((List<List<String>>) result.get("rows")).singleElement()
                .satisfies(row -> assertThat(row.get(columns.indexOf("UserId")) + " "
                                             + row.get(columns.indexOf("s1_time")))
                        .startsWith("bob " + canberraDate + "T01:00:00.000+1"));
    }

    @Test
    void anUnknownZoneIsRefused() {
        final String index = lucene("E2E time zones refused", List.of(new Event(Instant.now(), "anyone")));

        assertThat(run("from \"" + index + "\" select UserId", "Mars/Olympus"))
                .contains("unknown time zone 'Mars/Olympus': give local, UTC, a zone id such as Europe/London, or "
                          + "an offset such as +01:00");
    }

    // run_stroomql's result, with the rows of its table after it.
    private String run(final String query, final String timeZone) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("query", query);
        if (timeZone != null) {
            arguments.put("timeZone", timeZone);
        }
        final String result = McpTestClient.text(client.call("run_stroomql", arguments));
        dump("run_stroomql " + timeZone, Map.of("result", result));
        return result;
    }

    // Runs the work with the admin's time zone preference set, then puts the preference back.
    private <T> T withPreference(final UserTimeZone timeZone, final Supplier<T> work) {
        final UserPreferences before = asUser(admin, () -> userPreferencesService.fetch());
        asUser(admin, () -> {
            userPreferencesService.update(before.copy().timeZone(timeZone).build());
        });
        try {
            return work.get();
        } finally {
            asUser(admin, () -> {
                userPreferencesService.update(before);
            });
        }
    }

    // A Lucene index of the events, flushed so searches see them; its name.
    private String lucene(final String label, final List<Event> events) {
        final String name = label + " " + Long.toHexString(System.nanoTime());
        final ExplorerNode created = create(LuceneIndexDoc.TYPE, name, templateFolder());
        final LuceneIndexField streamId = LuceneIndexField.createIdField("StreamId");
        final LuceneIndexField eventId = LuceneIndexField.createIdField("EventId");
        final LuceneIndexField eventTime = LuceneIndexField.builder().fldName("EventTime").fldType(FieldType.DATE)
                .analyzerType(AnalyzerType.ALPHA_NUMERIC).indexed(true).stored(true).build();
        final LuceneIndexField userId = LuceneIndexField.builder().fldName("UserId").fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.KEYWORD).caseSensitive(true).indexed(true).stored(true).build();
        final LuceneIndexField action = LuceneIndexField.builder().fldName("Action").fldType(FieldType.TEXT)
                .analyzerType(AnalyzerType.KEYWORD).caseSensitive(true).indexed(true).stored(true).build();
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
                document.add(new FieldValue(eventTime, ValDate.create(event.time().toEpochMilli())));
                document.add(new FieldValue(userId, ValString.create(event.user())));
                document.add(new FieldValue(action, ValString.create(event.action())));
                indexer.addDocument(key, document);
            }
        });
        flushIndexShards();
        return name;
    }

    private long stream() {
        final List<Long> id = new ArrayList<>();
        asUser(admin, () -> id.add(metaService.create(MetaProperties.builder()
                .feedName("TZ-E2E-" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT))
                .typeName(StreamTypeNames.EVENTS)
                .createMs(Instant.now().toEpochMilli())
                .build(), Status.UNLOCKED).getId()));
        return id.getFirst();
    }


    // --------------------------------------------------------------------------------


    /// One event.
    ///
    /// @param time   Its time.
    /// @param user   Its user.
    /// @param action What it did.
    private record Event(Instant time, String user, String action) {

        private Event(final Instant time, final String user) {
            this(time, user, "Logon");
        }
    }
}
