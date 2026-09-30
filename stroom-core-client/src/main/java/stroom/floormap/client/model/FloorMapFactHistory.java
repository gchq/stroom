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

import stroom.floormap.client.value.FloorMapFactTableParser;
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Holds the Map tab's whole fact history and computes the snapshot at a timeline position from it,
/// so playback, scrub and step cost no queries at all.
///
/// ### Why hold the history rather than query per tick
///
/// The facts query used to run on every throttled playback tick — about three full fact reads a
/// second — as a snapshot with both time bounds at `T`. Facts change roughly weekly, so that poll was not
/// serving playback; the answer was almost always identical. It was serving **external write
/// detection**, at three times a second, for data that changes at about 1.7&nbsp;µHz.
///
/// So the two jobs are separated: the history is fetched on a slow cadence (and whenever the Map
/// becomes visible), and the snapshot at any position is derived from it locally. Accuracy improves
/// as well as cost — a fact change used to be picked up whenever a tick's snapshot happened to cross
/// it, accurate to one tick of *wall clock*, which at 10x playback is about three seconds of
/// timeline. Deriving it locally makes it exact at every frame.
///
/// ### Why a forward window cannot be used instead
///
/// The obvious cheaper query — fetch the next 60 s of facts once a minute — cannot work against a
/// SQL Temporal Store. `UpdatableTemporalStoreDaoImpl.getQueryTime` lifts the first
/// `EQUALS`/`<`/`<=` time term out as a *snapshot boundary* and
/// `getFilteredExpression` then strips every time term, so a range from `T` to `T+70s` returns one row
/// per key as of **T+70 s** — the future state, not the versions in between. There is no facts
/// delta either: `> X` is stripped rather than applied. Detection has to be a periodic full
/// re-read.
///
/// ### Why the raw millisecond column exists
///
/// Picking the version current at `t` means comparing each row's effective time against
/// `t`, and the `EffectiveTime` column arrives as text. It happens to be ISO-8601 today
/// — StroomQL sets no `Format` on select columns, so `Unformatted` calls
/// `ValDate.toString()`, which is always `DateUtil.createNormalDateTimeString` — but
/// relying on that would make the map's correctness depend on a formatter default two modules away.
/// So `FloorMapQueryBuilder` also selects `toLong(EffectiveTime)` as
/// [#EFFECTIVE_TIME_MS_COLUMN], giving raw epoch millis with no separators, and this class
/// reads only that.
///
/// The same reasoning rules out relying on **arrival order**. Rows do arrive in
/// `(key, effective_time)` ascending order, because the table's primary key is
/// `(doc_uuid, key_, effective_time)` and InnoDB clusters on it — but the standard read path
/// has no `ORDER BY`, so that is a storage-engine accident, not a contract, and a query plan
/// that chose the `map_name` index instead would break it silently. [#snapshotAt(long)]
/// therefore compares times explicitly and does not care what order the rows are in.
///
/// GWT-free and pure, so the decision logic and the snapshot arithmetic are unit-testable without
/// a browser or a clock — the same shape as `FloorMapQueryThrottle`.
public final class FloorMapFactHistory {

    /// How long the held history may go unrefreshed while the Map is visible.
    ///
    /// 60 s. This was originally borrowed from the events read's re-baseline interval so one
    /// constant served both; that machinery has since been retired — the stores can now reduce to
    /// latest-per-key server-side, so the events read needs no cadence of its own — and the
    /// constant lives here, where an interval is still needed. The interval only
    /// bounds how long an *externally written* fact stays unseen; the case that would
    /// actually feel it — someone adding a fact and watching for it to land — is served instead by
    /// re-reading whenever the Map becomes visible, since a person watching for their own write is
    /// about to look at the map.
    public static final long REFETCH_INTERVAL_MS = 60_000L;

    /// The row cap for a history read.
    ///
    /// Matches the events cap. The facts cap used to be 1 000, which was sized for a snapshot —
    /// one row per key — and full history is larger by however many times each fact has ever moved.
    /// At the stated change rate that is keys plus about two rows a week, so 20 000 puts truncation
    /// decades out; [#isTruncated()] covers the case where the assumption is wrong.
    public static final int MAX_ROWS = 20_000;

    /// The alias of the raw epoch-millis column.
    ///
    /// Declared here rather than beside the query builder that emits it, because the builder is
    /// in `stroom-core-client` and this module cannot depend on it. The name is a contract
    /// between the two: the builder writes it after `as`, and [#snapshotAt] matches it
    /// back. Anything not selected by our own builder simply will not match — see
    /// [#snapshotAt] for what happens then.
    public static final String EFFECTIVE_TIME_MS_COLUMN = "Effective Time Ms";

    private static final String KEY_COLUMN = "Key";

    private List<Column> columns;
    private List<Row> rows;
    private boolean loaded;
    private boolean truncated;

    /// Wall-clock millis at which the last read was **issued**; see [#needsRead].
    private double lastReadIssuedAt;

    private boolean readRequested = true;

    /// So a missing millisecond column is reported once rather than per tick.
    private boolean missingTimeColumnWarned;

    /// Whether a history read should be issued now.
    ///
    /// True when one has been [requested][#requestRead()], when none has ever landed, or
    /// when [#REFETCH_INTERVAL_MS] has passed since the last was issued.
    ///
    /// **Issued, not applied.** Stamping on apply would let a read slower than the caller's
    /// tick interval be re-issued by the next tick — and each `startNewSearch` destroys the
    /// search in flight, so the read would never complete and facts would stop updating altogether.
    /// The events read had the same hazard while it kept a cadence of its own.
    ///
    /// @param nowMs wall-clock millis, passed in so this stays testable
    /// @return `true` if the caller should read the whole history
    public boolean needsRead(final double nowMs) {
        return readRequested
               || !loaded
               || nowMs - lastReadIssuedAt > REFETCH_INTERVAL_MS;
    }

    /// Records that a read has been issued, clearing any request and restarting the interval.
    ///
    /// @param nowMs wall-clock millis
    public void markReadIssued(final double nowMs) {
        readRequested = false;
        lastReadIssuedAt = nowMs;
    }

    /// Asks for a read on the next [#needsRead] regardless of the interval — the Map becoming
    /// visible, a save, or a document open.
    public void requestRead() {
        readRequested = true;
    }

    /// Replaces the held history.
    ///
    /// Only called for a read that completed without error: an errored empty result is
    /// indistinguishable from an empty store by its rows, and applying it would blank the floor
    /// plan. The caller gates that.
    ///
    /// @param columns   the result columns; `null` clears the history
    /// @param rows      every version of every fact
    /// @param truncated whether the row cap bound
    public void setHistory(final List<Column> columns, final List<Row> rows, final boolean truncated) {
        this.columns = columns;
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        this.truncated = truncated;
        this.loaded = columns != null;
    }

    /// The rows current at `t`: for each key, the one version with the greatest effective time
    /// at or before `t`. A key whose first version is later than `t` is absent, which is
    /// correct — it did not exist yet.
    ///
    /// Returned in first-seen key order so the canvas draw order is stable between ticks.
    ///
    /// Ties on effective time keep the later row, matching
    /// [FloorMapFactTableParser]'s last-row-wins.
    ///
    /// **Cost, stated rather than optimised.** This is a linear scan of the whole history on
    /// every call, so at the expected volume — keys plus about two rows a week — a playback tick
    /// visits a few hundred rows, which is nothing against the server round trip it replaces. At the
    /// [#MAX_ROWS] cap it would be 20 000 rows three times a second, which is worth knowing
    /// but is a volume [#isTruncated()] already reports as misuse of a facts store. An index
    /// of distinct effective times, recomputed when the history lands, would make the common case
    /// O(1) — deliberately not built, because it would be optimising for the configuration we warn
    /// about rather than the one we expect.
    ///
    /// @param t the timeline position, epoch millis
    /// @return the snapshot rows, never `null`
    public List<Row> snapshotAt(final long t) {
        if (!loaded || NullSafe.isEmptyCollection(rows)) {
            return new ArrayList<>();
        }

        final int keyIdx = columnIndex(KEY_COLUMN);
        final int msIdx = columnIndex(EFFECTIVE_TIME_MS_COLUMN);
        if (keyIdx < 0 || msIdx < 0) {
            // Both columns are emitted by our own query builder, so absence means the document is
            // driving this from something else. Returning every row lets last-row-wins draw the
            // latest state, which is wrong for a scrubbed position but diagnosable on screen;
            // returning nothing would look like an empty store. The caller reports it once.
            return new ArrayList<>(rows);
        }

        final Map<String, Row> latestByKey = new LinkedHashMap<>();
        final Map<String, Long> latestMsByKey = new LinkedHashMap<>();
        for (final Row row : rows) {
            final List<String> values = row.getValues();
            if (values == null || keyIdx >= values.size() || msIdx >= values.size()) {
                continue;
            }
            final String key = values.get(keyIdx);
            if (key == null) {
                continue;
            }
            final Long ms = parseMs(values.get(msIdx));
            if (ms == null || ms > t) {
                continue;
            }
            final Long best = latestMsByKey.get(key);
            if (best == null || ms >= best) {
                latestMsByKey.put(key, ms);
                latestByKey.put(key, row);
            }
        }
        return new ArrayList<>(latestByKey.values());
    }

    /// Whether [#snapshotAt] is filtering by time at all.
    ///
    /// False only when the millisecond column is missing, which is a fault rather than a
    /// configuration — see [#snapshotAt]. Returns `true` the first time it is false, so
    /// the caller reports it once instead of on every tick.
    ///
    /// @return `true` if the caller should report the fault now
    public boolean shouldWarnMissingTimeColumn() {
        if (!loaded) {
            return false;
        }
        if (columnIndex(EFFECTIVE_TIME_MS_COLUMN) >= 0
            && columnIndex(KEY_COLUMN) >= 0) {
            return false;
        }
        if (missingTimeColumnWarned) {
            return false;
        }
        missingTimeColumnWarned = true;
        return true;
    }

    /// The columns of the held history, for handing to [FloorMapFactTableParser].
    public List<Column> columns() {
        return columns;
    }

    /// Whether a history has ever landed. Until it has, there is nothing to draw.
    public boolean isLoaded() {
        return loaded;
    }

    /// Whether the row cap bound, so the history is incomplete.
    public boolean isTruncated() {
        return truncated;
    }

    /// Total held rows — every version of every fact.
    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }

    /// Forgets everything, including the read interval, so a re-read is due immediately.
    ///
    /// Used when the document is re-read: the old document's facts must not be drawn against the
    /// new one, and the warning is reset because it is per-document.
    public void clear() {
        columns = null;
        rows = null;
        loaded = false;
        truncated = false;
        lastReadIssuedAt = 0;
        readRequested = true;
        missingTimeColumnWarned = false;
    }

    private int columnIndex(final String name) {
        if (columns == null) {
            return -1;
        }
        for (int i = 0; i < columns.size(); i++) {
            final Column column = columns.get(i);
            if (column != null && name.equalsIgnoreCase(column.getName())) {
                return i;
            }
        }
        return -1;
    }

    /// Parses the raw millisecond cell.
    ///
    /// `Long.parseLong` rather than a date parse: the cell is `toLong(EffectiveTime)`,
    /// which reaches the client as bare digits because StroomQL sets no `Format` on select
    /// columns. A cell that is not a number is skipped rather than defaulted — a fact placed at
    /// epoch 0 would sit at the far left of every timeline and look like data.
    private static Long parseMs(final String value) {
        if (NullSafe.isEmptyString(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
