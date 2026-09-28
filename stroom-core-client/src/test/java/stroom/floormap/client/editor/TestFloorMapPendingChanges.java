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

import stroom.floormap.client.editor.FloorMapPendingChanges.Deletion;
import stroom.floormap.client.editor.FloorMapPendingChanges.PendingChange;
import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapPendingChanges {

    private static final String MAP = "testMap";

    private FloorMapPendingChanges pendingChanges;

    @BeforeEach
    void setUp() {
        pendingChanges = new FloorMapPendingChanges();
    }

    // -----------------------------------------------------------------------
    // isDirty / clear
    // -----------------------------------------------------------------------

    /**
     * A freshly-constructed instance has no pending changes.
     */
    @Test
    void testInitiallyClean() {
        assertThat(pendingChanges.isDirty()).isFalse();
    }

    /**
     * Recording a creation marks the instance dirty.
     */
    @Test
    void testDirtyAfterCreation() {
        pendingChanges.recordCreation(entry("k1", 100, "{}"));
        assertThat(pendingChanges.isDirty()).isTrue();
    }

    /**
     * Recording an update marks the instance dirty.
     */
    @Test
    void testDirtyAfterUpdate() {
        pendingChanges.recordUpdate(entry("k1", 100, "{\"v\":1}"));
        assertThat(pendingChanges.isDirty()).isTrue();
    }

    /**
     * Recording a deletion marks the instance dirty.
     */
    @Test
    void testDirtyAfterDeletion() {
        pendingChanges.recordDeletion(id("k1", 100));
        assertThat(pendingChanges.isDirty()).isTrue();
    }

    /**
     * {@link FloorMapPendingChanges#clear()} discards all recorded changes and
     * returns the instance to a clean (not dirty) state.
     */
    @Test
    void testClearResetsDirty() {
        pendingChanges.recordCreation(entry("k1", 100, "{}"));
        pendingChanges.clear();
        assertThat(pendingChanges.isDirty()).isFalse();
    }

    // -----------------------------------------------------------------------
    // applyTo
    // -----------------------------------------------------------------------

    /**
     * With no pending changes, {@code applyTo} returns the server entries
     * unmodified and in the same order.
     */
    @Test
    void testApplyTo_emptyChanges_returnsServerCopy() {
        final List<TemporalEntry> server = List.of(
                entry("k1", 100, "{\"a\":1}"),
                entry("k2", 200, "{\"b\":2}"));
        final List<TemporalEntry> result = pendingChanges.applyTo(server);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getKey()).isEqualTo("k1");
        assertThat(result.get(1).getKey()).isEqualTo("k2");
    }

    /**
     * A {@code null} server list is treated as empty rather than throwing.
     */
    @Test
    void testApplyTo_nullServer_returnsEmptyList() {
        final List<TemporalEntry> result = pendingChanges.applyTo(null);
        assertThat(result).isEmpty();
    }

    /**
     * An empty server list with no pending changes yields an empty result.
     */
    @Test
    void testApplyTo_emptyServer_returnsEmptyList() {
        final List<TemporalEntry> result = pendingChanges.applyTo(new ArrayList<>());
        assertThat(result).isEmpty();
    }

    /**
     * A pending creation is appended after the existing server entries.
     */
    @Test
    void testApplyTo_singleCreation() {
        pendingChanges.recordCreation(entry("k3", 300, "{\"c\":3}"));
        final List<TemporalEntry> result = pendingChanges.applyTo(
                List.of(entry("k1", 100, "{\"a\":1}")));
        assertThat(result).hasSize(2);
        assertThat(result.get(1).getKey()).isEqualTo("k3");
    }

    /**
     * A pending update overlays (replaces the value of) the matching server
     * entry rather than being appended alongside it.
     */
    @Test
    void testApplyTo_singleUpdate_replaces() {
        pendingChanges.recordUpdate(entry("k1", 100, "{\"a\":\"updated\"}"));
        final List<TemporalEntry> result = pendingChanges.applyTo(
                List.of(entry("k1", 100, "{\"a\":1}")));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue()).isEqualTo("{\"a\":\"updated\"}");
    }

    /**
     * A pending deletion removes the matching entry from the merged result,
     * leaving other server entries untouched.
     */
    @Test
    void testApplyTo_singleDeletion_removes() {
        pendingChanges.recordDeletion(id("k1", 100));
        final List<TemporalEntry> result = pendingChanges.applyTo(
                List.of(entry("k1", 100, "{\"a\":1}"), entry("k2", 200, "{\"b\":2}")));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getKey()).isEqualTo("k2");
    }

    /**
     * An update recorded after a creation for the same key overlays the
     * creation's value rather than producing a second entry.
     */
    @Test
    void testApplyTo_createThenUpdate() {
        pendingChanges.recordCreation(entry("k3", 300, "{\"c\":\"original\"}"));
        pendingChanges.recordUpdate(entry("k3", 300, "{\"c\":\"updated\"}"));
        final List<TemporalEntry> result = pendingChanges.applyTo(new ArrayList<>());
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue()).isEqualTo("{\"c\":\"updated\"}");
    }

    /**
     * Deleting a natural key and then recreating it at the same key/time
     * results in the recreated value surviving, not the deletion.
     */
    @Test
    void testApplyTo_deleteThenReCreate() {
        pendingChanges.recordDeletion(id("k1", 100));
        pendingChanges.recordCreation(entry("k1", 100, "{\"a\":\"recreated\"}"));
        final List<TemporalEntry> result = pendingChanges.applyTo(
                List.of(entry("k1", 100, "{\"a\":\"original\"}")));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue()).isEqualTo("{\"a\":\"recreated\"}");
    }

    /**
     * A creation, update, and deletion recorded together are all applied
     * correctly and independently to the server entries.
     */
    @Test
    void testApplyTo_multipleOpsInOrder() {
        pendingChanges.recordCreation(entry("k3", 300, "{\"c\":3}"));
        pendingChanges.recordUpdate(entry("k1", 100, "{\"a\":\"updated\"}"));
        pendingChanges.recordDeletion(id("k2", 200));

        final List<TemporalEntry> result = pendingChanges.applyTo(List.of(
                entry("k1", 100, "{\"a\":1}"),
                entry("k2", 200, "{\"b\":2}")));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().getKey()).isEqualTo("k1");
        assertThat(result.getFirst().getValue()).isEqualTo("{\"a\":\"updated\"}");
        assertThat(result.get(1).getKey()).isEqualTo("k3");
    }

    /**
     * When the same key appears at multiple effective times, a deletion
     * targeting one time only removes that specific shard, leaving the
     * other time's entry in place.
     */
    @Test
    void testApplyTo_duplicateKeys_differentTimes() {
        final List<TemporalEntry> server = List.of(
                entry("k1", 100, "{\"v\":1}"),
                entry("k1", 200, "{\"v\":2}"));
        pendingChanges.recordDeletion(id("k1", 100));
        final List<TemporalEntry> result = pendingChanges.applyTo(server);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEffectiveTimeMs()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // Natural-key matching with boxed Long identity
    // -----------------------------------------------------------------------

    /**
     * Natural-key matching must compare effective times by value, not by object
     * identity. {@link Long} values outside the JVM's autobox cache
     * ({@code -128}..{@code 127}) are distinct objects, so an accidental
     * {@code ==} comparison would silently fail to match. This guards the update
     * path: an update keyed on one {@code Long} instance must still overlay a
     * server entry holding an equal-but-distinct {@code Long} instance.
     */
    @Test
    void testApplyTo_update_effectiveTimeOutsideCacheRange() {
        final long serverTime = 1_000_000_000_000L;
        final long updateTime = Long.parseLong("1000000000000");
        // Guard the guard: these must be distinct objects for the test to bite.
        assertThat(serverTime).isNotSameAs(updateTime);

        pendingChanges.recordUpdate(entry("k1", updateTime, "{\"v\":\"updated\"}"));
        final List<TemporalEntry> result = pendingChanges.applyTo(
                List.of(entry("k1", serverTime, "{\"v\":\"original\"}")));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue()).isEqualTo("{\"v\":\"updated\"}");
    }

    /**
     * As above, but for the deletion path: a deletion keyed on one {@code Long}
     * instance must remove a server entry holding an equal-but-distinct
     * {@code Long} instance.
     */
    @Test
    void testApplyTo_deletion_effectiveTimeOutsideCacheRange() {
        final long serverTime = 1_000_000_000_000L;
        final long deletionTime = Long.parseLong("1000000000000");
        assertThat(serverTime).isNotSameAs(deletionTime);

        pendingChanges.recordDeletion(id("k1", deletionTime));
        final List<TemporalEntry> result = pendingChanges.applyTo(
                List.of(entry("k1", serverTime, "{\"v\":\"original\"}")));

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // getChanges
    // -----------------------------------------------------------------------

    /**
     * With no operations recorded, {@code getChanges} returns an empty list.
     */
    @Test
    void testGetChanges_empty() {
        assertThat(pendingChanges.getChanges()).isEmpty();
    }

    /**
     * {@code getChanges} returns operations in the exact order they were
     * recorded, regardless of operation type.
     */
    @Test
    void testGetChanges_preservesOrder() {
        pendingChanges.recordCreation(entry("k1", 100, "{}"));
        pendingChanges.recordDeletion(id("k2", 200));
        assertThat(pendingChanges.getChanges()).hasSize(2);
    }

    // -----------------------------------------------------------------------
    // clearSent — a save must not discard edits it never sent
    // -----------------------------------------------------------------------

    @Test
    void testClearSent_keepsOpsStagedDuringTheFlush() {
        // Two ops are sent...
        pendingChanges.recordCreation(entry("sent1", 100, "{}"));
        pendingChanges.recordUpdate(entry("sent2", 200, "{}"));
        final int sentCount = pendingChanges.getChanges().size();

        // ...then the user edits again while the request is in flight.
        pendingChanges.recordDeletion(id("stagedDuringFlush", 300));

        pendingChanges.clearSent(sentCount);

        // The unsent edit must survive, otherwise it silently disappears when the
        // panels reload after a successful save.
        assertThat(pendingChanges.getChanges()).hasSize(1);
        assertThat(pendingChanges.isDirty()).isTrue();
        final PendingChange remaining = pendingChanges.getChanges().getFirst();
        assertThat(remaining).isInstanceOf(Deletion.class);
        assertThat(((Deletion) remaining).getId().getKey()).isEqualTo("stagedDuringFlush");
    }

    @Test
    void testClearSent_emptiesBufferWhenNothingWasStagedMeanwhile() {
        pendingChanges.recordCreation(entry("k1", 100, "{}"));
        pendingChanges.recordUpdate(entry("k2", 200, "{}"));

        pendingChanges.clearSent(2);

        assertThat(pendingChanges.getChanges()).isEmpty();
        assertThat(pendingChanges.isDirty()).isFalse();
    }

    @Test
    void testClearSent_clampsOutOfRangeCounts() {
        pendingChanges.recordCreation(entry("k1", 100, "{}"));

        pendingChanges.clearSent(0);
        assertThat(pendingChanges.getChanges()).hasSize(1);

        pendingChanges.clearSent(-5);
        assertThat(pendingChanges.getChanges()).hasSize(1);

        // More than are present must not throw.
        pendingChanges.clearSent(99);
        assertThat(pendingChanges.getChanges()).isEmpty();
    }

    @Test
    void testClearSent_removesAPrefixNotByEquality() {
        // Two identical-looking ops: only the first (sent) one may be dropped.
        pendingChanges.recordUpdate(entry("same", 100, "{}"));
        pendingChanges.recordUpdate(entry("same", 100, "{}"));

        pendingChanges.clearSent(1);

        assertThat(pendingChanges.getChanges()).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TemporalEntry entry(final String key, final long time, final String value) {
        return new TemporalEntry(MAP, key, time, value);
    }

    private static TemporalEntryId id(final String key, final long time) {
        return new TemporalEntryId(MAP, key, time);
    }
}
