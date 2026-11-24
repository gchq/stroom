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

package stroom.query.common.v2;

/// Keeps the first and last time windows that a store holds real rows for, which are rows of events
/// in the window and not copies of rows from other windows.
///
/// The windows are noted as rows are stored, so that the store does not have to be read through to
/// find them. They are only read through, once, when rows have been stored or removed in a way that
/// was not noted: when a store that already holds rows is opened, and when rows are deleted. Rows that
/// arrive from another node come with the windows that the node has noted, as a [Span].
///
/// The thread that writes to the store calls [#add(LmdbKV)], then [#beginCommit()] before it commits
/// and [#committed(boolean)] after. A read takes a [Snapshot] before it begins and another once it
/// has begun. If the two are the same and no commit was under way, the read sees just the rows that
/// the snapshot is of. If not, a commit happened as the read began, and the read can't tell whether
/// it sees those rows, so it reads through the store. The methods that take a snapshot are called
/// by one read at a time.
final class StoredWindowExtent {

    // The windows of rows that have been written but not committed, used by the writing thread only.
    private long uncommittedFirst = Long.MAX_VALUE;
    private long uncommittedLast = Long.MIN_VALUE;
    private boolean uncommittedUnknown;

    // The windows of every row that has been written, which a store that sends its rows to another
    // node sends with them. They are unknown unless the store held no rows when it was opened. Used by
    // the writing thread only, once the store is open.
    private long writtenFirst = Long.MAX_VALUE;
    private long writtenLast = Long.MIN_VALUE;
    private boolean writtenUnknown = true;

    // The windows of rows committed since the store last changed in a way that was not noted.
    private long committedFirst = Long.MAX_VALUE;
    private long committedLast = Long.MIN_VALUE;
    // Counts the changes to the store that were not noted. The first is that the store may hold rows
    // when it is opened.
    private long unknownChanges = 1;
    // Counts the commits begun and ended, and whether one is under way.
    private long commitCount;
    private boolean committing;

    // The windows found by reading through the store, and the count of changes at that time. These
    // are used by one read at a time.
    private long scannedChanges;
    private long scannedFirst = Long.MAX_VALUE;
    private long scannedLast = Long.MIN_VALUE;

    /// Notes a row that has been written to the store, and not yet committed.
    ///
    /// @param lmdbKV The row.
    void add(final LmdbKV lmdbKV) {
        if (!lmdbKV.isWindowKnown()) {
            addSpan(Span.UNKNOWN);
        } else if (lmdbKV.getRealWindowKey() != null) {
            addSpan(new Span(true, lmdbKV.getRealWindowKey(), lmdbKV.getRealWindowKey()));
        }
    }

    /// Notes that rows have been written to the store, and not yet committed, that hold real rows of
    /// a span of windows. Rows from another node come with such a span.
    ///
    /// @param span The windows.
    void addSpan(final Span span) {
        if (!span.known()) {
            uncommittedUnknown = true;
            writtenUnknown = true;
        } else if (span.first() <= span.last()) {
            uncommittedFirst = Math.min(uncommittedFirst, span.first());
            uncommittedLast = Math.max(uncommittedLast, span.last());
            writtenFirst = Math.min(writtenFirst, span.first());
            writtenLast = Math.max(writtenLast, span.last());
        }
    }

    /// Gets the windows of every row that has been written to the store, which the store sends with
    /// its rows to another node. Called by the writing thread.
    ///
    /// @return The windows.
    Span getWrittenSpan() {
        return writtenUnknown
                ? Span.UNKNOWN
                : new Span(true, writtenFirst, writtenLast);
    }

    /// Notes that the store held no rows when it was opened, so it does not have to be read through to
    /// find its windows. Called before any row is written or read.
    synchronized void openedEmpty() {
        unknownChanges = 0;
        writtenUnknown = false;
    }

    /// Notes that the rows written so far are about to be committed. Until [#committed(boolean)] is
    /// called, a read can't tell whether it sees them.
    synchronized void beginCommit() {
        committing = true;
        commitCount++;
    }

    /// Notes that the rows written so far have been committed, so reads can now see them.
    ///
    /// @param rowsDeleted Whether the commit removed rows from the store.
    synchronized void committed(final boolean rowsDeleted) {
        committing = false;
        commitCount++;
        if (rowsDeleted || uncommittedUnknown) {
            // The store has to be read through, which finds every window, so start again from there.
            unknownChanges++;
            committedFirst = Long.MAX_VALUE;
            committedLast = Long.MIN_VALUE;
        } else {
            committedFirst = Math.min(committedFirst, uncommittedFirst);
            committedLast = Math.max(committedLast, uncommittedLast);
        }
        uncommittedFirst = Long.MAX_VALUE;
        uncommittedLast = Long.MIN_VALUE;
        uncommittedUnknown = false;
    }

    /// Takes what is known of the committed rows. A read does this before it begins.
    ///
    /// @return What is known now.
    synchronized Snapshot snapshot() {
        return new Snapshot(unknownChanges, committedFirst, committedLast, commitCount, committing);
    }

    /// Tests whether a read sees just the rows that its snapshot is of.
    ///
    /// @param before What was known before the read began.
    /// @param after  What was known once the read had begun.
    /// @return True if no commit began or ended as the read began.
    static boolean isSettled(final Snapshot before, final Snapshot after) {
        return !before.committing && before.commitCount == after.commitCount;
    }

    /// Tests whether a read has to read through the store to find its windows.
    ///
    /// @param snapshot What was known before the read began.
    /// @return True if the store has changed, in a way that was not noted, since it was last read
    ///         through.
    boolean needsScan(final Snapshot snapshot) {
        return scannedChanges != snapshot.unknownChanges;
    }

    /// Keeps the windows that a read found by reading through the store.
    ///
    /// @param snapshot What was known before the read began.
    /// @param first    The key of the first window with a real row, or `Long.MAX_VALUE` if none.
    /// @param last     The key of the last window with a real row, or `Long.MIN_VALUE` if none.
    void scanned(final Snapshot snapshot, final long first, final long last) {
        scannedChanges = snapshot.unknownChanges;
        scannedFirst = first;
        scannedLast = last;
    }

    /// Gets the first window that the rows a read can see have a real row for.
    ///
    /// @param snapshot What was known before the read began.
    /// @return The key of the window. It is greater than [#getLast(Snapshot)] if there are none.
    long getFirst(final Snapshot snapshot) {
        return Math.min(scannedFirst, snapshot.committedFirst);
    }

    /// Gets the last window that the rows a read can see have a real row for.
    ///
    /// @param snapshot What was known before the read began.
    /// @return The key of the window. It is less than [#getFirst(Snapshot)] if there are none.
    long getLast(final Snapshot snapshot) {
        return Math.max(scannedLast, snapshot.committedLast);
    }


    // --------------------------------------------------------------------------------


    /// What was known of the committed rows of a store at one time.
    ///
    /// @param unknownChanges The count of changes to the store that were not noted.
    /// @param committedFirst The first window of the rows committed since the last such change.
    /// @param committedLast  The last window of the rows committed since the last such change.
    /// @param commitCount    The count of commits begun and ended.
    /// @param committing     Whether a commit was under way.
    record Snapshot(long unknownChanges,
                    long committedFirst,
                    long committedLast,
                    long commitCount,
                    boolean committing) {

    }


    // --------------------------------------------------------------------------------


    /// The first and last windows that a set of rows holds real rows for, which a store sends with
    /// its rows to another node. It is also put on the queue of the store that receives them, ahead
    /// of the rows, so that it is noted with them.
    ///
    /// The span sent with each set of rows is of every row the sending store has written so far. A
    /// store may send its rows in more than one set, so the store that receives them may note a
    /// window a little before its rows arrive. Once every set has arrived, the windows are exact.
    ///
    /// @param known Whether the windows are known. They are not if rows were written whose windows
    ///              were not noted, so the store that receives them has to read through its rows.
    /// @param first The key of the first window, or `Long.MAX_VALUE` if none.
    /// @param last  The key of the last window, or `Long.MIN_VALUE` if none.
    record Span(boolean known, long first, long last) implements LmdbQueueItem {

        static final Span UNKNOWN = new Span(false, Long.MAX_VALUE, Long.MIN_VALUE);
    }
}
