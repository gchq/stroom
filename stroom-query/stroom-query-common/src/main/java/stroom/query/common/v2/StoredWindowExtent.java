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
/// was not noted: when a store that already holds rows is opened, when rows are deleted, and when
/// rows arrive from another node.
///
/// The thread that writes to the store calls [#add(LmdbKV)] and [#committed(boolean)]. A read takes
/// a [Snapshot] before it begins, so that it is given the windows of the rows it can see and not of
/// rows committed while it runs. The methods that take a snapshot are called by one read at a time.
final class StoredWindowExtent {

    // The windows of rows that have been written but not committed, used by the writing thread only.
    private long uncommittedFirst = Long.MAX_VALUE;
    private long uncommittedLast = Long.MIN_VALUE;
    private boolean uncommittedUnknown;

    // The windows of rows committed since the store last changed in a way that was not noted.
    private long committedFirst = Long.MAX_VALUE;
    private long committedLast = Long.MIN_VALUE;
    // Counts the changes to the store that were not noted. The first is that the store may hold rows
    // when it is opened.
    private long unknownChanges = 1;

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
            uncommittedUnknown = true;
        } else if (lmdbKV.getRealWindowKey() != null) {
            uncommittedFirst = Math.min(uncommittedFirst, lmdbKV.getRealWindowKey());
            uncommittedLast = Math.max(uncommittedLast, lmdbKV.getRealWindowKey());
        }
    }

    /// Notes that the rows written so far have been committed, so reads can now see them.
    ///
    /// @param rowsDeleted Whether the commit removed rows from the store.
    synchronized void committed(final boolean rowsDeleted) {
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
        return new Snapshot(unknownChanges, committedFirst, committedLast);
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
    record Snapshot(long unknownChanges, long committedFirst, long committedLast) {

    }
}
