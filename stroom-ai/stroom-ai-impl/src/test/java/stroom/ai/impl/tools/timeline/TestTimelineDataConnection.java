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

package stroom.ai.impl.tools.timeline;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// A timeline's table as a CSV Data Connection: its columns typed, its name, the earlier run's it replaces, its
/// description, and its settings.
class TestTimelineDataConnection {

    private static final long FROM = Instant.parse("2026-10-20T00:00:00Z").toEpochMilli();
    private static final long TO = Instant.parse("2026-10-27T00:00:00Z").toEpochMilli();
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void theSpansColumnsAreTypedTheirTimesDatesTheirEventsANumberTheRestText() {
        final List<DataColumn> columns = TimelineDataConnection.columns(TimelineDataConnection.SPANS);

        assertThat(columns).extracting(DataColumn::getName).isEqualTo(TimelineTables.SPAN_COLUMNS);
        assertThat(columns).filteredOn(column -> column.getType() != DataColumnType.TEXT)
                .extracting(DataColumn::getName, DataColumn::getType)
                .containsExactly(tuple("start", DataColumnType.DATE), tuple("end", DataColumnType.DATE),
                        tuple("events", DataColumnType.LONG));
    }

    @Test
    void theActivitiesDayStaysTextAsItIsALanesName() {
        final List<DataColumn> columns = TimelineDataConnection.columns(TimelineDataConnection.ACTIVITIES);

        assertThat(columns).extracting(DataColumn::getName).isEqualTo(TimelineTables.ACTIVITY_COLUMNS);
        assertThat(columns).filteredOn(column -> column.getType() != DataColumnType.TEXT)
                .extracting(DataColumn::getName, DataColumn::getType)
                .containsExactly(tuple("start", DataColumnType.DATE), tuple("end", DataColumnType.DATE),
                        tuple("active_minutes", DataColumnType.DOUBLE));
    }

    @Test
    void aHeaderOtherThanTheTypedColumnsIsAProblem() {
        assertThat(TimelineDataConnection.headerProblem(TimelineDataConnection.SPANS,
                String.join(",", TimelineTables.SPAN_COLUMNS))).isEmpty();
        assertThat(TimelineDataConnection.headerProblem(TimelineDataConnection.SPANS, "span,start,end,label"))
                .hasValueSatisfying(problem -> assertThat(problem).contains("'span,start,end,label'"));
    }

    @Test
    void theNameGivesTheEntityThePeriodsDaysAndTheTable() {
        // A period ending at midnight ends on the day before.
        assertThat(TimelineDataConnection.name("sam.patel", FROM, TO, UTC, TimelineDataConnection.SPANS))
                .isEqualTo("Timeline sam.patel 2026-10-20 to 2026-10-26 (spans)");
        // One day is named once; the days are the user's.
        assertThat(TimelineDataConnection.name("sam.patel", FROM, FROM + 3_600_000L * 5,
                ZoneId.of("Australia/Sydney"), TimelineDataConnection.ACTIVITIES))
                .isEqualTo("Timeline sam.patel 2026-10-20 (activities)");
        // A character a document's name can't hold is made _.
        assertThat(TimelineDataConnection.name("CORP\\sam@x", FROM, TO, UTC, TimelineDataConnection.SPANS))
                .isEqualTo("Timeline CORP_sam_x 2026-10-20 to 2026-10-26 (spans)");
    }

    @Test
    void anEarlierRunsNameForTheSameEntityAndTableIsOneToReplace() {
        assertThat(TimelineDataConnection.isFor("Timeline sam.patel 2026-09-01 to 2026-09-07 (spans)", "sam.patel",
                TimelineDataConnection.SPANS)).isTrue();
        assertThat(TimelineDataConnection.isFor("Timeline sam.patel 2026-09-01 (activities)", "sam.patel",
                TimelineDataConnection.SPANS)).isFalse();
        assertThat(TimelineDataConnection.isFor("Timeline sam.patelson 2026-09-01 (spans)", "sam.patel",
                TimelineDataConnection.SPANS)).isFalse();
        assertThat(TimelineDataConnection.isFor(null, "sam.patel", TimelineDataConnection.SPANS)).isFalse();
    }

    @Test
    void theDescriptionSaysItIsADatedSnapshotOfWhomAndFromWhichSources() {
        final TimelineSnapshot snapshot = new TimelineSnapshot("sam.patel", FROM, TO, "UTC", "detail", null,
                List.of("Endpoint", "Proxy"), false, Instant.parse("2026-11-24T10:15:00Z").toEpochMilli(),
                Map.of());

        assertThat(TimelineDataConnection.description(snapshot, TimelineDataConnection.SPANS))
                .contains("A snapshot of sam.patel's activity timeline (the detail spans",
                        "for 2026-10-20 00:00 to 2026-10-27 00:00 (UTC)", "at 2026-11-24T10:15:00Z",
                        "from Endpoint, Proxy", "isn't updated: run the timeline again to replace it");
    }

    @Test
    void theSettingsHoldTheCsvAsItIsWithStartTheTimeField() {
        final String csv = String.join(",", TimelineTables.SPAN_COLUMNS) + "\r\n";

        final DataConnectionDoc doc = TimelineDataConnection.apply(DataConnectionDoc.builder().uuid("u").name("n"),
                TimelineDataConnection.SPANS, csv, "A snapshot.").build();

        assertThat(doc.getSourceType()).isEqualTo(DataConnectionType.CSV);
        assertThat(doc.getCsv().getData()).isEqualTo(csv);
        assertThat(doc.getCsv().getHeader()).isTrue();
        assertThat(doc.getCsv().getColumns()).isEqualTo(TimelineDataConnection.columns(TimelineDataConnection.SPANS));
        assertThat(doc.getTimeField()).isEqualTo("start");
        assertThat(doc.getDescription()).isEqualTo("A snapshot.");
    }

    @Test
    void aZoneThatCantBeReadIsUtc() {
        assertThat(TimelineDataConnection.zone("Not/AZone")).isEqualTo(UTC);
        assertThat(TimelineDataConnection.zone(null)).isEqualTo(UTC);
    }
}
