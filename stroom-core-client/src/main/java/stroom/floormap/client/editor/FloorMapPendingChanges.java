/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.editor;

import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Client-side staging buffer for temporal-store edits made in the Editor tab.
 *
 * <p>Every user action (create, update, delete) appends an operation to the
 * ordered {@link #changes} list. The list is preserved in exactly the order
 * the user performed the operations, so that {@link #getChanges()} can replay
 * them server-side in the same order within a single transaction.</p>
 *
 * <h3>Optimistic UI</h3>
 * <p>Call {@link #applyTo(List)} to obtain a view of the server-sourced data
 * with all pending changes merged in — creations appended, updates overlaid,
 * and deletions removed. This lets the UI show the intended state immediately
 * while the flush is still pending.</p>
 *
 * <h3>Save / error flow</h3>
 * <p>On a successful flush call {@link #clearSent(int)} with the number of
 * operations that were sent — <em>not</em> {@link #clear()}, which would also
 * discard anything the user staged while the request was in flight. On a failed
 * flush keep the operations staged so the user can retry; the server transaction
 * was rolled back, so replaying them is safe and idempotent.</p>
 */
public class FloorMapPendingChanges {

    // -----------------------------------------------------------------------
    // Staged-change types
    // -----------------------------------------------------------------------

    /** Marker interface for a single staged operation. */
    public interface PendingChange {
    }

    /** A creation: a brand-new effective-time entry for this key. */
    public static final class Creation implements PendingChange {

        private final TemporalEntry entry;

        public Creation(final TemporalEntry entry) {
            this.entry = entry;
        }

        public TemporalEntry getEntry() {
            return entry;
        }
    }

    /** An update: an existing entry (same map+key+effectiveTime) with edited fields. */
    public static final class Update implements PendingChange {

        private final TemporalEntry entry;

        public Update(final TemporalEntry entry) {
            this.entry = entry;
        }

        public TemporalEntry getEntry() {
            return entry;
        }
    }

    /** A deletion: remove the entry identified by its natural key triple. */
    public static final class Deletion implements PendingChange {

        private final TemporalEntryId id;

        public Deletion(final TemporalEntryId id) {
            this.id = id;
        }

        public TemporalEntryId getId() {
            return id;
        }
    }

    // -----------------------------------------------------------------------

    /** Operations in user-performed order. Never reordered. */
    private final List<PendingChange> changes = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Recording operations
    // -----------------------------------------------------------------------

    /**
     * Records the creation of a new temporal entry.
     *
     * @param entry the entry to create; must not be {@code null}
     */
    public void recordCreation(final TemporalEntry entry) {
        changes.add(new Creation(entry));
    }

    /**
     * Records an update to an existing temporal entry.
     *
     * @param entry the updated entry; must not be {@code null}
     */
    public void recordUpdate(final TemporalEntry entry) {
        changes.add(new Update(entry));
    }

    /**
     * Records the deletion of an existing temporal entry.
     * The entry is hidden immediately in the UI via {@link #applyTo(List)}.
     *
     * @param id the natural key of the entry to delete; must not be
     *           {@code null}
     */
    public void recordDeletion(final TemporalEntryId id) {
        changes.add(new Deletion(id));
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if any operations are staged and awaiting flush.
     *
     * @return {@code true} when there are pending changes
     */
    public boolean isDirty() {
        return !changes.isEmpty();
    }

    // -----------------------------------------------------------------------
    // Flush
    // -----------------------------------------------------------------------

    /**
     * Clears all staged operations.
     *
     * <p>Only safe when nothing can have been staged since the flush was
     * dispatched — otherwise use {@link #clearSent(int)}, which keeps the
     * unsent tail.</p>
     */
    public void clear() {
        changes.clear();
    }

    /**
     * Discards the first {@code sentCount} operations — the ones a flush actually
     * sent — and keeps anything staged after it was dispatched.
     *
     * <p>A save is a round-trip and the UI is not blocked while it is in flight,
     * so the user can drag, delete or edit in the meantime. Clearing the whole
     * buffer on success would throw those edits away without ever having sent
     * them, and the follow-up panel reload would make them visibly disappear.</p>
     *
     * <p>This is exact rather than approximate because {@link #changes} is
     * append-only and never reordered (see the class javadoc), so the operations
     * a flush sent are always a prefix of the list.</p>
     *
     * @param sentCount the number of operations that were sent, i.e. the size of
     *                  {@link #getChanges()} at the moment the request was built;
     *                  values outside the current range are clamped
     */
    public void clearSent(final int sentCount) {
        if (sentCount <= 0) {
            return;
        }
        if (sentCount >= changes.size()) {
            changes.clear();
            return;
        }
        changes.subList(0, sentCount).clear();
    }

    // -----------------------------------------------------------------------
    // Optimistic merge
    // -----------------------------------------------------------------------

    /**
     * Merges pending operations into a server-sourced entry list and returns
     * the result for optimistic UI display.
     *
     * @param serverEntries the live entries as returned by the server; may be
     *                      {@code null} (treated as empty)
     * @return a new mutable list reflecting all pending operations;
     *         never {@code null}
     */
    public List<TemporalEntry> applyTo(final List<TemporalEntry> serverEntries) {
        final List<TemporalEntry> result = new ArrayList<>();
        if (serverEntries != null) {
            result.addAll(serverEntries);
        }

        for (final PendingChange change : changes) {
            if (change instanceof Creation) {
                upsertByNaturalKey(result, ((Creation) change).getEntry());
            } else if (change instanceof Update) {
                upsertByNaturalKey(result, ((Update) change).getEntry());
            } else if (change instanceof Deletion) {
                final TemporalEntryId id = ((Deletion) change).getId();
                result.removeIf(e -> naturalKeyMatchesId(e, id));
            }
        }

        return result;
    }

    /**
     * Returns an unmodifiable view of the staged operations, in the order
     * they were recorded. Used by the presenter to build a REST request.
     *
     * @return the list of pending changes; never {@code null}
     */
    public List<PendingChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static boolean naturalKeyMatches(final TemporalEntry a,
                                             final TemporalEntry b) {
        return Objects.equals(a.getMap(), b.getMap())
                && Objects.equals(a.getKey(), b.getKey())
                && Objects.equals(a.getEffectiveTimeMs(), b.getEffectiveTimeMs());
    }

    private static boolean naturalKeyMatchesId(final TemporalEntry entry,
                                                final TemporalEntryId id) {
        return Objects.equals(entry.getMap(), id.getMap())
                && Objects.equals(entry.getKey(), id.getKey())
                && Objects.equals(entry.getEffectiveTimeMs(), id.getEffectiveTimeMs());
    }

    /**
     * Replaces the first entry in the list that matches the given entry's
     * natural key, or appends the entry if no match is found.
     * This mirrors server-side UPSERT semantics.
     */
    private static void upsertByNaturalKey(final List<TemporalEntry> list,
                                           final TemporalEntry entry) {
        for (int i = 0; i < list.size(); i++) {
            if (naturalKeyMatches(list.get(i), entry)) {
                list.set(i, entry);
                return;
            }
        }
        list.add(entry);
    }
}
