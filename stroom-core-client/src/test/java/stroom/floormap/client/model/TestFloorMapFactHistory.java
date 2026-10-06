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

package stroom.floormap.client.model;

import stroom.query.api.Column;
import stroom.query.api.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapFactHistory {

    private static final String MS = FloorMapFactHistory.EFFECTIVE_TIME_MS_COLUMN;

    // ---- read cadence ----

    @Test
    void readIsDueBeforeAnythingHasLanded() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        assertThat(history.needsRead(0)).isTrue();
        assertThat(history.isLoaded()).isFalse();
    }

    @Test
    void issuingAReadClearsTheRequestButNotTheNeedUntilOneLands() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.markReadIssued(1_000);

        // Still not loaded, so still due - otherwise a failed first read would leave the map
        // permanently empty with nothing retrying.
        assertThat(history.needsRead(1_100)).isTrue();

        history.setHistory(columns(), rows(row("desk-1", 500)), false);
        assertThat(history.needsRead(1_100)).isFalse();
    }

    @Test
    void theIntervalIsMeasuredFromIssueNotFromApply() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.markReadIssued(1_000);
        history.setHistory(columns(), rows(row("desk-1", 500)), false);

        assertThat(history.needsRead(1_000 + FloorMapFactHistory.REFETCH_INTERVAL_MS)).isFalse();
        assertThat(history.needsRead(1_001 + FloorMapFactHistory.REFETCH_INTERVAL_MS)).isTrue();
    }

    @Test
    void requestOverridesTheInterval() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.markReadIssued(1_000);
        history.setHistory(columns(), rows(row("desk-1", 500)), false);
        assertThat(history.needsRead(1_100)).isFalse();

        history.requestRead();
        assertThat(history.needsRead(1_100)).isTrue();
    }

    @Test
    void issuingOnceIsEnoughEvenIfTheReadIsSlowerThanATick() {
        // The livelock guard: a read in flight must not be re-issued 300ms later, because
        // startNewSearch destroys the search in flight and it would never complete.
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.requestRead();
        assertThat(history.needsRead(1_000)).isTrue();
        history.markReadIssued(1_000);

        assertThat(history.needsRead(1_300)).isTrue();  // still not loaded
        history.setHistory(columns(), rows(row("desk-1", 500)), false);
        assertThat(history.needsRead(1_600)).isFalse(); // and now quiet until the interval
    }

    @Test
    void clearMakesAReadDueImmediately() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.markReadIssued(1_000);
        history.setHistory(columns(), rows(row("desk-1", 500)), false);
        assertThat(history.needsRead(1_100)).isFalse();

        history.clear();
        assertThat(history.needsRead(1_100)).isTrue();
        assertThat(history.isLoaded()).isFalse();
        assertThat(history.snapshotAt(9_999)).isEmpty();
    }

    // ---- the snapshot ----

    @Test
    void picksTheVersionCurrentAtTheGivenTime() {
        final FloorMapFactHistory history = loaded(
                row("desk-1", 100),
                row("desk-1", 500),
                row("desk-1", 900));

        assertThat(keysAndMs(history.snapshotAt(400))).containsExactly("desk-1@100");
        assertThat(keysAndMs(history.snapshotAt(500))).containsExactly("desk-1@500");
        assertThat(keysAndMs(history.snapshotAt(899))).containsExactly("desk-1@500");
        assertThat(keysAndMs(history.snapshotAt(1_000))).containsExactly("desk-1@900");
    }

    @Test
    void factThatDidNotExistYetIsAbsent() {
        final FloorMapFactHistory history = loaded(row("desk-1", 500));

        assertThat(history.snapshotAt(499)).isEmpty();
        assertThat(keysAndMs(history.snapshotAt(500))).containsExactly("desk-1@500");
    }

    @Test
    void everyKeyIsRepresentedOnceByItsOwnLatestVersion() {
        final FloorMapFactHistory history = loaded(
                row("desk-1", 100),
                row("desk-2", 100),
                row("desk-1", 800),
                row("desk-3", 900));

        assertThat(keysAndMs(history.snapshotAt(850)))
                .containsExactly("desk-1@800", "desk-2@100");
    }

    @Test
    void keyOrderIsFirstSeenAndStableAcrossPositions() {
        // These eight keys are the test fixture's own, and they are chosen deliberately: a HashMap
        // reorders exactly this set, so the assertion fails if the insertion order is ever lost.
        // Draw order comes straight from this list, and an unstable one makes overlapping facts
        // flicker which is above which, three times a second.
        final FloorMapFactHistory history = loaded(
                row("desk-105", 100),
                row("desk-104", 100),
                row("desk-103", 100),
                row("desk-102", 100),
                row("desk-101", 100),
                row("area-south", 100),
                row("area-north", 100),
                row("bg-ground", 100),
                row("desk-103", 800));

        final List<String> expected = List.of(
                "desk-105", "desk-104", "desk-103", "desk-102", "desk-101",
                "area-south", "area-north", "bg-ground");

        assertThat(keys(history.snapshotAt(150))).containsExactlyElementsOf(expected);
        // A later version of a key already seen must not move it to the end.
        assertThat(keys(history.snapshotAt(900))).containsExactlyElementsOf(expected);
    }

    @Test
    void orderOfArrivalDoesNotDecideWhichVersionWins() {
        // The whole point of comparing times rather than trusting arrival order: the DAO's
        // standard read path has no ORDER BY, so this ordering is reachable.
        final FloorMapFactHistory history = loaded(
                row("desk-1", 900),
                row("desk-1", 100));

        assertThat(keysAndMs(history.snapshotAt(500))).containsExactly("desk-1@100");
        assertThat(keysAndMs(history.snapshotAt(1_000))).containsExactly("desk-1@900");
    }

    @Test
    void tieOnEffectiveTimeKeepsTheLaterRow() {
        // Matching FloorMapFactTableParser's last-row-wins, so the two agree.
        final FloorMapFactHistory history = loaded(
                row("desk-1", 500, "first"),
                row("desk-1", 500, "second"));

        final List<Row> snapshot = history.snapshotAt(500);
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.getFirst().getValues().get(2)).isEqualTo("second");
    }

    @Test
    void anUnparseableTimeIsSkippedRatherThanTreatedAsEpochZero() {
        // A fact defaulted to 0 would sit at the far left of every timeline and look like data.
        final FloorMapFactHistory history = loaded(
                row("desk-1", 500),
                rawRow("desk-2", "not-a-number"),
                rawRow("desk-3", ""),
                rawRow("desk-4", null));

        assertThat(keys(history.snapshotAt(9_999))).containsExactly("desk-1");
    }

    @Test
    void rowWithNoKeyIsSkipped() {
        final FloorMapFactHistory history = loaded(
                rawRow(null, "500"),
                row("desk-1", 500));

        assertThat(keys(history.snapshotAt(9_999))).containsExactly("desk-1");
    }

    @Test
    void shortRowIsSkippedRatherThanThrowing() {
        final List<Row> rows = new ArrayList<>();
        rows.add(Row.builder().values(List.of("desk-1")).build());   // no ms cell at all
        rows.add(row("desk-2", 500));

        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), rows, false);

        assertThat(keys(history.snapshotAt(9_999))).containsExactly("desk-2");
    }

    @Test
    void anEmptyHistorySnapshotsToNothing() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), List.of(), false);

        assertThat(history.isLoaded()).isTrue();
        assertThat(history.snapshotAt(9_999)).isEmpty();
    }

    @Test
    void anUnloadedHistorySnapshotsToNothing() {
        assertThat(new FloorMapFactHistory().snapshotAt(9_999)).isEmpty();
    }

    // ---- the missing-column fault ----

    @Test
    void withoutTheMillisecondColumnEveryRowIsReturnedSoTheMapIsNotBlank() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(
                List.of(column("Key"), column("EffectiveTime")),
                rows(row("desk-1", 100), row("desk-1", 900)),
                false);

        // Not time-filtered: last-row-wins downstream draws the latest state. Wrong for a
        // scrubbed position, but diagnosable on screen where an empty map would not be.
        assertThat(history.snapshotAt(500)).hasSize(2);
    }

    @Test
    void theMissingColumnFaultIsReportedOnceOnly() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(List.of(column("Key")), rows(row("desk-1", 100)), false);

        assertThat(history.shouldWarnMissingTimeColumn()).isTrue();
        assertThat(history.shouldWarnMissingTimeColumn()).isFalse();
        assertThat(history.shouldWarnMissingTimeColumn()).isFalse();
    }

    @Test
    void wellFormedHistoryNeverWarns() {
        final FloorMapFactHistory history = loaded(row("desk-1", 100));
        assertThat(history.shouldWarnMissingTimeColumn()).isFalse();
    }

    @Test
    void nothingIsReportedBeforeAHistoryHasLanded() {
        // Otherwise opening a document warns about a fault that has not been observed yet.
        assertThat(new FloorMapFactHistory().shouldWarnMissingTimeColumn()).isFalse();
    }

    @Test
    void theFaultIsReportableAgainAfterClear() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(List.of(column("Key")), rows(row("desk-1", 100)), false);
        assertThat(history.shouldWarnMissingTimeColumn()).isTrue();

        history.clear();
        history.setHistory(List.of(column("Key")), rows(row("desk-1", 100)), false);
        assertThat(history.shouldWarnMissingTimeColumn()).isTrue();
    }

    // ---- truncation and size ----

    @Test
    void truncationAndRowCountAreReportedAsGiven() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), rows(row("desk-1", 100), row("desk-1", 200)), true);

        assertThat(history.isTruncated()).isTrue();
        assertThat(history.rowCount()).isEqualTo(2);
    }

    @Test
    void laterUntruncatedReadClearsTheTruncationFlag() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), rows(row("desk-1", 100)), true);
        history.setHistory(columns(), rows(row("desk-1", 100)), false);

        assertThat(history.isTruncated()).isFalse();
    }

    @Test
    void nullRowListIsHeldAsEmptyRatherThanExploding() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), null, false);

        assertThat(history.isLoaded()).isTrue();
        assertThat(history.rowCount()).isZero();
        assertThat(history.snapshotAt(9_999)).isEmpty();
    }

    @Test
    void nullColumnsMeanNothingLanded() {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(null, rows(row("desk-1", 100)), false);

        assertThat(history.isLoaded()).isFalse();
        assertThat(history.needsRead(9_999)).isTrue();
    }

    @Test
    void heldRowsAreNotAliasedToTheCallersList() {
        final List<Row> caller = new ArrayList<>(rows(row("desk-1", 100)));
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), caller, false);

        caller.clear();

        assertThat(history.rowCount()).isEqualTo(1);
    }

    // ---- helpers ----

    private static FloorMapFactHistory loaded(final Row... rows) {
        final FloorMapFactHistory history = new FloorMapFactHistory();
        history.setHistory(columns(), Arrays.asList(rows), false);
        return history;
    }

    private static List<Column> columns() {
        return List.of(column("Key"), column(MS), column("Label"));
    }

    private static Column column(final String name) {
        return Column.builder().id(name).name(name).build();
    }

    private static List<Row> rows(final Row... rows) {
        return Arrays.asList(rows);
    }

    private static Row row(final String key, final long ms) {
        return row(key, ms, "label");
    }

    private static Row row(final String key, final long ms, final String label) {
        return Row.builder().values(Arrays.asList(key, String.valueOf(ms), label)).build();
    }

    private static Row rawRow(final String key, final String ms) {
        return Row.builder().values(Arrays.asList(key, ms, "label")).build();
    }

    private static List<String> keys(final List<Row> rows) {
        final List<String> keys = new ArrayList<>();
        for (final Row row : rows) {
            keys.add(row.getValues().getFirst());
        }
        return keys;
    }

    private static List<String> keysAndMs(final List<Row> rows) {
        final List<String> out = new ArrayList<>();
        for (final Row row : rows) {
            out.add(row.getValues().get(0) + "@" + row.getValues().get(1));
        }
        return out;
    }
}
