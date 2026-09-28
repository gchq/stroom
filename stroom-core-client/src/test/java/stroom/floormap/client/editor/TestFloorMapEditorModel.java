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

import stroom.floormap.client.model.Fact;
import stroom.floormap.client.value.MapValueAccessor;
import stroom.floormap.client.value.ParsedValue;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.ValueFormat;
import stroom.util.shared.TemporalEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapEditorModel {

    private static final String MAP = "testMap";
    private static final List<FloorMapFieldMapping> SCHEMA =
            FloorMapFieldMapping.initialValueSchema();
    private static final MapValueAccessor ACCESSOR = MapValueAccessor.INSTANCE;

    private FloorMapEditorModel model;
    private List<String> warnings;

    @BeforeEach
    void setUp() {
        warnings = new ArrayList<>();
        model = new FloorMapEditorModel(new Random(42), warnings::add);
        warnings.clear(); // clear any warnings emitted during construction
    }

    // -----------------------------------------------------------------------
    // Selection state
    // -----------------------------------------------------------------------

    /**
     * A freshly-constructed model has no selection, is at time zero, has
     * "show all facts" off, and has no pending changes.
     */
    @Test
    void testInitialState() {
        assertThat(model.getSelectedFactKey()).isNull();
        assertThat(model.getSelectedTime()).isEqualTo(0);
        assertThat(model.isShowAllFacts()).isFalse();
        assertThat(model.hasPendingChanges()).isFalse();
    }

    /**
     * Setting the selected fact key stores it and it can be read back.
     */
    @Test
    void testSelectFact() {
        model.setSelectedFactKey("gate-1");
        assertThat(model.getSelectedFactKey()).isEqualTo("gate-1");
    }

    /**
     * Setting the selected fact key to {@code null} clears the selection.
     */
    @Test
    void testDeselectFact() {
        model.setSelectedFactKey("gate-1");
        model.setSelectedFactKey(null);
        assertThat(model.getSelectedFactKey()).isNull();
    }

    /**
     * The selected time can be set and read back.
     */
    @Test
    void testTimeChange() {
        model.setSelectedTime(12345L);
        assertThat(model.getSelectedTime()).isEqualTo(12345L);
    }

    /**
     * The "show all facts" flag can be toggled on and off.
     */
    @Test
    void testShowAllToggle() {
        model.setShowAllFacts(true);
        assertThat(model.isShowAllFacts()).isTrue();
        model.setShowAllFacts(false);
        assertThat(model.isShowAllFacts()).isFalse();
    }

    // -----------------------------------------------------------------------
    // onEntriesFetched
    // -----------------------------------------------------------------------

    /**
     * Fetched server entries are stored, and the returned merged list
     * includes any pending creation staged before the fetch completed.
     */
    @Test
    void testOnEntriesFetched_storesAndMerges() {
        final List<TemporalEntry> server = List.of(entry("k1", 100, "{}"));
        model.getPendingChanges().recordCreation(entry("k2", 200, "{}"));

        final List<TemporalEntry> merged = model.onEntriesFetched(server);
        assertThat(merged).hasSize(2);
        assertThat(model.getServerEntriesAtCurrentTime()).hasSize(1);
    }

    /**
     * Fetching {@code null} entries is treated as an empty server result
     * rather than throwing.
     */
    @Test
    void testOnEntriesFetched_null() {
        final List<TemporalEntry> merged = model.onEntriesFetched(null);
        assertThat(merged).isEmpty();
        assertThat(model.getServerEntriesAtCurrentTime()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // buildMergedTimeList
    // -----------------------------------------------------------------------

    /**
     * The merged time list only includes entries whose key matches the
     * currently selected fact.
     */
    @Test
    void testBuildMergedTimeList_filtersToSelectedKey() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"),
                entry("k2", 150, "{}")));

        final List<TemporalEntry> result = model.buildMergedTimeList();
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(e -> assertThat(e.getKey()).isEqualTo("k1"));
    }

    /**
     * The merged time list is returned sorted by effective time, regardless
     * of the input order.
     */
    @Test
    void testBuildMergedTimeList_sorted() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 300, "{}"),
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}")));

        final List<TemporalEntry> result = model.buildMergedTimeList();
        assertThat(result.getFirst().getEffectiveTimeMs()).isEqualTo(100);
        assertThat(result.get(1).getEffectiveTimeMs()).isEqualTo(200);
        assertThat(result.get(2).getEffectiveTimeMs()).isEqualTo(300);
    }

    /**
     * A pending creation for the selected fact appears in the merged time
     * list alongside the server-known entries.
     */
    @Test
    void testBuildMergedTimeList_includesPendingCreation() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(entry("k1", 100, "{}")));
        model.getPendingChanges().recordCreation(entry("k1", 200, "{\"new\":true}"));

        final List<TemporalEntry> result = model.buildMergedTimeList();
        assertThat(result).hasSize(2);
    }

    /**
     * A pending deletion for the selected fact hides the matching entry from
     * the merged time list.
     */
    @Test
    void testBuildMergedTimeList_excludesPendingDeletion() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}")));
        model.getPendingChanges().recordDeletion(
                new stroom.util.shared.TemporalEntryId(MAP, "k1", 100L));

        final List<TemporalEntry> result = model.buildMergedTimeList();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getEffectiveTimeMs()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // findActiveIndexAtTime
    // -----------------------------------------------------------------------

    /**
     * A query time before the earliest entry has no active entry, i.e. -1.
     */
    @Test
    void testFindActiveIndex_beforeAll() {
        final List<TemporalEntry> timeList = List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"));
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(timeList, 50)).isEqualTo(-1);
    }

    /**
     * A query time that exactly matches an entry's effective time returns
     * that entry's index.
     */
    @Test
    void testFindActiveIndex_exactMatch() {
        final List<TemporalEntry> timeList = List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"));
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(timeList, 100)).isEqualTo(0);
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(timeList, 200)).isEqualTo(1);
    }

    /**
     * A query time falling between two entries returns the index of the
     * most recent entry at or before that time.
     */
    @Test
    void testFindActiveIndex_betweenEntries() {
        final List<TemporalEntry> timeList = List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"),
                entry("k1", 300, "{}"));
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(timeList, 150)).isEqualTo(0);
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(timeList, 250)).isEqualTo(1);
    }

    /**
     * A query time after the latest entry returns the index of the last
     * entry, since it remains the active value going forward.
     */
    @Test
    void testFindActiveIndex_afterAll() {
        final List<TemporalEntry> timeList = List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"));
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(timeList, 999)).isEqualTo(1);
    }

    /**
     * An empty time list has no active entry at any time, i.e. -1.
     */
    @Test
    void testFindActiveIndex_empty() {
        assertThat(FloorMapEditorModel.findActiveIndexAtTime(List.of(), 100)).isEqualTo(-1);
    }

    // -----------------------------------------------------------------------
    // Staging edits
    // -----------------------------------------------------------------------

    /**
     * A version move stages both halves: the old shard is deleted and the entry reappears
     * at its new time.
     *
     * <p>This was previously two adjacent statements in the presenter, so nothing could
     * check that both happened. Recording only the upsert leaves the original in place —
     * the user asks for a version to move and gets a second version instead, silently.</p>
     */
    @Test
    void testStageVersionMove_removesTheOldShardAndAddsTheNew() {
        final TemporalEntry original = entry("gate-1", 1_000L, "{}");
        model.onEntriesFetched(List.of(original));

        final TemporalEntry moved = entry("gate-1", 5_000L, "{\"moved\":true}");
        model.stageVersionMove(moved, original.getEffectiveTimeMs());

        final List<TemporalEntry> merged =
                model.getPendingChanges().applyTo(List.of(original));
        assertThat(merged).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        assertThat(merged.get(0).getEffectiveTimeMs()).isEqualTo(5_000L);
    }

    /**
     * A "move" to the time the entry is already at is not a move: it must not delete and
     * re-add the same shard, so the verb is safe to call without the caller first checking
     * whether the time actually changed.
     */
    @Test
    void testStageVersionMove_sameTimeIsAPlainUpdate() {
        final TemporalEntry original = entry("gate-1", 1_000L, "{}");
        model.onEntriesFetched(List.of(original));

        model.stageVersionMove(entry("gate-1", 1_000L, "{\"edited\":true}"), 1_000L);

        assertThat(model.getPendingChanges().getChanges())
                .as("no deletion should be staged")
                .allSatisfy(change -> assertThat(change)
                        .isNotInstanceOf(FloorMapPendingChanges.Deletion.class));
        final List<TemporalEntry> merged =
                model.getPendingChanges().applyTo(List.of(original));
        assertThat(merged).hasSize(1);
    }

    /** A clone keeps the original alongside the new version. */
    @Test
    void testStageUpdate_doesNotRemoveAnything() {
        final TemporalEntry original = entry("gate-1", 1_000L, "{}");
        model.onEntriesFetched(List.of(original));

        model.stageUpdate(entry("gate-1", 5_000L, "{}"));

        assertThat(model.getPendingChanges().applyTo(List.of(original))).hasSize(2);
    }

    // -----------------------------------------------------------------------
    // Key generation
    // -----------------------------------------------------------------------

    /**
     * A generated object key starts with the requested prefix followed by a
     * separator.
     */
    @Test
    void testGenerateObjectKey_prefixPreserved() {
        final String key = model.generateObjectKey("gate");
        assertThat(key).startsWith("gate-");
    }

    /**
     * Two successive calls with the same prefix generate distinct keys.
     */
    @Test
    void testGenerateObjectKey_unique() {
        final String k1 = model.generateObjectKey("obj");
        final String k2 = model.generateObjectKey("obj");
        assertThat(k1).isNotEqualTo(k2);
    }

    /**
     * Key generation avoids colliding with a key already known to the model
     * from previously-fetched server entries.
     *
     * <p>Uses a scripted {@link Random} so the collision actually happens. With a
     * real {@code Random} the first draw has a 1-in-99,999 chance of hitting the
     * pre-seeded number, so the assertion passed whether or not the collision check
     * existed — the test attested to nothing. Here the first draw is forced to
     * collide, so removing the check in {@code generateObjectKey} fails this test.</p>
     */
    @Test
    void testGenerateObjectKey_avoidsExisting() {
        final FloorMapEditorModel m = modelWithRandom(new ScriptedRandom(12345, 54321));
        m.onEntriesFetched(List.of(entry("gate-12345", 100, "{}")));
        assertThat(m.generateObjectKey("gate")).isEqualTo("gate-54321");
    }

    /**
     * A key known only from the selected fact's time list is still avoided.
     *
     * <p>This is the case the old implementation missed: it built its key set from
     * the time-filtered canvas snapshot alone, so a shard of the selected fact
     * sitting outside that snapshot was invisible and could be collided with.</p>
     */
    @Test
    void testGenerateObjectKey_avoidsKeyKnownOnlyFromSelectedFactTimeList() {
        final FloorMapEditorModel m = modelWithRandom(new ScriptedRandom(12345, 54321));
        // Deliberately NOT in the canvas snapshot — only in the selected fact's shards.
        m.onTimeListFetched(List.of(entry("gate-12345", 9_000_000, "{}")));
        assertThat(m.generateObjectKey("gate")).isEqualTo("gate-54321");
    }

    /**
     * A key staged for deletion is not handed out again.
     *
     * <p>{@code applyTo} removes a deleted entry from the merged list, so the key
     * looks free. Reusing it would race the flush — whether the new object survived
     * would depend on the order the server applied the delete and the create.</p>
     */
    @Test
    void testGenerateObjectKey_avoidsKeyPendingDeletion() {
        final FloorMapEditorModel m = modelWithRandom(new ScriptedRandom(12345, 54321));
        final TemporalEntry doomed = entry("gate-12345", 100, "{}");
        m.onEntriesFetched(List.of(doomed));
        m.stageTimeEntryDeletion(doomed);
        assertThat(m.generateObjectKey("gate")).isEqualTo("gate-54321");
    }

    /**
     * A selected key is avoided even when the snapshot no longer contains it, which
     * is reachable when a selection outlives the fetch it was made against.
     */
    @Test
    void testGenerateObjectKey_avoidsSelectedKeyMissingFromSnapshot() {
        final FloorMapEditorModel m = modelWithRandom(new ScriptedRandom(12345, 54321));
        m.setSelectedFactKey("gate-12345");
        m.onEntriesFetched(List.of());
        assertThat(m.generateObjectKey("gate")).isEqualTo("gate-54321");
    }

    /** A model with a specific {@link Random}, sharing this test's warning collector. */
    private FloorMapEditorModel modelWithRandom(final Random random) {
        return new FloorMapEditorModel(random, warnings::add);
    }

    /**
     * A {@link Random} yielding preset values from {@code nextInt(int)}, repeating the
     * last once exhausted, so a test can force a key collision on demand.
     */
    private static final class ScriptedRandom extends Random {

        private static final long serialVersionUID = 1L;

        private final int[] values;
        private int index;

        private ScriptedRandom(final int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(final int bound) {
            final int value = values[Math.min(index, values.length - 1)];
            index++;
            return value;
        }
    }

    // -----------------------------------------------------------------------
    // cloneEntryAtTime
    // -----------------------------------------------------------------------

    /**
     * Cloning an entry at a new time copies the source value and key while
     * moving the effective time to the requested value.
     */
    @Test
    void testCloneEntryAtTime_copiesValue() {
        final TemporalEntry source = entry("k1", 100, "{\"type\":\"gate\"}");
        final TemporalEntry clone = FloorMapEditorModel.cloneEntryAtTime(
                source, MAP, "k1", 200);
        assertThat(clone.getEffectiveTimeMs()).isEqualTo(200);
        assertThat(clone.getValue()).isEqualTo("{\"type\":\"gate\"}");
        assertThat(clone.getKey()).isEqualTo("k1");
    }

    /**
     * Cloning with a {@code null} source produces an entry with an empty
     * JSON object value rather than throwing.
     */
    @Test
    void testCloneEntryAtTime_nullSource() {
        final TemporalEntry clone = FloorMapEditorModel.cloneEntryAtTime(
                null, MAP, "k1", 200);
        assertThat(clone.getEffectiveTimeMs()).isEqualTo(200);
        assertThat(clone.getValue()).isEqualTo("{}");
    }

    // -----------------------------------------------------------------------
    // buildNewEntryAtTime
    // -----------------------------------------------------------------------

    /**
     * A new entry built at a time between two shards is stamped with that time
     * and inherits its value from the earlier (in-effect) shard.
     */
    @Test
    void testBuildNewEntryAtTime_betweenShards_clonesActiveShard() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{\"type\":\"gate\"}"),
                entry("k1", 200, "{\"type\":\"camera\"}")));

        final TemporalEntry created = model.buildNewEntryAtTime(MAP, 150);
        assertThat(created.getMap()).isEqualTo(MAP);
        assertThat(created.getKey()).isEqualTo("k1");
        assertThat(created.getEffectiveTimeMs()).isEqualTo(150);
        assertThat(created.getValue()).isEqualTo("{\"type\":\"gate\"}");
    }

    /**
     * A new entry built after the latest shard inherits that latest shard's
     * value.
     */
    @Test
    void testBuildNewEntryAtTime_afterAllShards_clonesLatest() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{\"type\":\"gate\"}"),
                entry("k1", 200, "{\"type\":\"camera\"}")));

        final TemporalEntry created = model.buildNewEntryAtTime(MAP, 250);
        assertThat(created.getEffectiveTimeMs()).isEqualTo(250);
        assertThat(created.getValue()).isEqualTo("{\"type\":\"camera\"}");
    }

    /**
     * A time exactly on an existing shard clones that shard (the collision case
     * that arises when the scrubber snaps to a selected row); the pending-change
     * upsert then treats saving it as a replace rather than a duplicate.
     */
    @Test
    void testBuildNewEntryAtTime_exactlyOnShard_clonesThatShard() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{\"type\":\"gate\"}"),
                entry("k1", 200, "{\"type\":\"camera\"}")));

        final TemporalEntry created = model.buildNewEntryAtTime(MAP, 200);
        assertThat(created.getEffectiveTimeMs()).isEqualTo(200);
        assertThat(created.getValue()).isEqualTo("{\"type\":\"camera\"}");
    }

    /**
     * A time before every shard has no active shard, so a blank entry is built
     * at the requested time.
     */
    @Test
    void testBuildNewEntryAtTime_beforeAllShards_blankValue() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{\"type\":\"gate\"}"),
                entry("k1", 200, "{\"type\":\"camera\"}")));

        final TemporalEntry created = model.buildNewEntryAtTime(MAP, 50);
        assertThat(created.getEffectiveTimeMs()).isEqualTo(50);
        assertThat(created.getValue()).isEqualTo("{}");
    }

    /**
     * The source shard is chosen from the merged list, so a pending creation
     * (not yet flushed to the server) can be the shard cloned from.
     */
    @Test
    void testBuildNewEntryAtTime_clonesFromPendingCreation() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(entry("k1", 100, "{\"type\":\"gate\"}")));
        model.getPendingChanges().recordCreation(entry("k1", 200, "{\"type\":\"door\"}"));

        final TemporalEntry created = model.buildNewEntryAtTime(MAP, 250);
        assertThat(created.getEffectiveTimeMs()).isEqualTo(250);
        assertThat(created.getValue()).isEqualTo("{\"type\":\"door\"}");
    }

    // -----------------------------------------------------------------------
    // buildDuplicateEntry
    // -----------------------------------------------------------------------

    /**
     * A duplicate is created under the new key at the requested effective time,
     * with its placement-matrix translation (indices 4, 5) shifted by (dx, dy) —
     * the offset that positions image facts, which are placed solely by the
     * matrix.
     */
    @Test
    void testBuildDuplicateEntry_offsetsMatrixTranslation() {
        final TemporalEntry source = entry("gate-1", 100,
                "{\"type\":\"gate\",\"tm-world-to-map\":[1,0,0,1,10,20]}");

        final TemporalEntry dup = FloorMapEditorModel.buildDuplicateEntry(
                source, MAP, "gate-1-copy", 250, 50.0, 50.0, SCHEMA, ACCESSOR);

        assertThat(dup.getMap()).isEqualTo(MAP);
        assertThat(dup.getKey()).isEqualTo("gate-1-copy");
        assertThat(dup.getEffectiveTimeMs()).isEqualTo(250);
        final double[] m = ACCESSOR.getArray(ACCESSOR.parse(dup.getValue()), ".tm-world-to-map");
        assertThat(m[4]).isCloseTo(60.0, within(0.001));
        assertThat(m[5]).isCloseTo(70.0, within(0.001));
    }

    /**
     * The offset changes only the matrix translation; rotation/scale (a, b, c, d)
     * are preserved.
     */
    @Test
    void testBuildDuplicateEntry_preservesRotationScale() {
        final TemporalEntry source = entry("bg", 100,
                "{\"type\":\"background\",\"tm-world-to-map\":[2,0.5,-0.5,2,10,20]}");

        final TemporalEntry dup = FloorMapEditorModel.buildDuplicateEntry(
                source, MAP, "bg-copy", 100, 50.0, 60.0, SCHEMA, ACCESSOR);

        final double[] m = ACCESSOR.getArray(ACCESSOR.parse(dup.getValue()), ".tm-world-to-map");
        assertThat(m[0]).isCloseTo(2.0, within(0.001));
        assertThat(m[1]).isCloseTo(0.5, within(0.001));
        assertThat(m[2]).isCloseTo(-0.5, within(0.001));
        assertThat(m[3]).isCloseTo(2.0, within(0.001));
        assertThat(m[4]).isCloseTo(60.0, within(0.001));
        assertThat(m[5]).isCloseTo(80.0, within(0.001));
    }

    /**
     * When the source has no placement matrix it defaults to identity before the
     * offset is applied, so the duplicate lands at (dx, dy).
     */
    @Test
    void testBuildDuplicateEntry_defaultsToIdentityWhenNoMatrix() {
        final TemporalEntry source = entry("gate-1", 100, "{\"type\":\"gate\"}");

        final TemporalEntry dup = FloorMapEditorModel.buildDuplicateEntry(
                source, MAP, "gate-1-copy", 100, 50.0, 50.0, SCHEMA, ACCESSOR);

        final double[] m = ACCESSOR.getArray(ACCESSOR.parse(dup.getValue()), ".tm-world-to-map");
        assertThat(m).containsExactly(1.0, 0.0, 0.0, 1.0, 50.0, 50.0);
    }

    /**
     * The duplicate's label is repointed at the new key.
     */
    @Test
    void testBuildDuplicateEntry_relabelsToNewKey() {
        final TemporalEntry source = entry("gate-1", 100,
                "{\"type\":\"gate\",\"name\":\"gate-1\",\"tm-world-to-map\":[1,0,0,1,0,0]}");

        final TemporalEntry dup = FloorMapEditorModel.buildDuplicateEntry(
                source, MAP, "gate-1-copy", 100, 5.0, 5.0, SCHEMA, ACCESSOR);

        assertThat(ACCESSOR.getString(ACCESSOR.parse(dup.getValue()), ".name"))
                .isEqualTo("gate-1-copy");
    }

    /**
     * The offset is applied to the matrix, not to POSITION, so a fact's coords
     * are carried over unchanged.
     */
    @Test
    void testBuildDuplicateEntry_leavesPositionCoordsUntouched() {
        final TemporalEntry source = entry("gate-1", 100,
                "{\"type\":\"gate\",\"coords\":[7,9],\"tm-world-to-map\":[1,0,0,1,0,0]}");

        final TemporalEntry dup = FloorMapEditorModel.buildDuplicateEntry(
                source, MAP, "gate-1-copy", 100, 50.0, 50.0, SCHEMA, ACCESSOR);

        final double[] coords = ACCESSOR.getArray(ACCESSOR.parse(dup.getValue()), ".coords");
        assertThat(coords).containsExactly(7.0, 9.0);
    }


    // -----------------------------------------------------------------------
    // Background translation via WORLD_TO_MAP
    //
    // A background has no separate write path: it is translated like any other
    // fact, by shifting its WORLD_TO_MAP matrix translation. These cover
    // translation-only movement, rotation/scale being preserved, and the identity
    // default.
    // -----------------------------------------------------------------------

    /**
     * Translating a background shifts the translation components (indices 4, 5)
     * of its WORLD_TO_MAP matrix, and leaves the POSITION coords field
     * untouched.
     */
    @Test
    void testBackgroundTranslate_updatesMatrixTranslation() {
        model.onEntriesFetched(List.of(entry("background", 100,
                "{\"type\":\"background\",\"coords\":[5,5],"
                        + "\"tm-world-to-map\":[1,0,0,1,0,0]}")));

        final int moved = model.transformFacts(List.of("background"),
                FloorMapTransformationMatrix.translate(60.0, 80.0),
                SCHEMA, ACCESSOR);
        assertThat(moved).isEqualTo(1);

        final TemporalEntry updated = model.buildMergedCanvasEntries().stream()
                .filter(e -> e.getKey().equals("background")).findFirst().orElseThrow();
        final ParsedValue parsed = ACCESSOR.parse(updated.getValue());
        final double[] m = ACCESSOR.getArray(parsed, ".tm-world-to-map");
        assertThat(m[4]).isCloseTo(60.0, within(0.001));
        assertThat(m[5]).isCloseTo(80.0, within(0.001));
        // The POSITION coords are not touched by a matrix translation.
        final double[] coords = ACCESSOR.getArray(parsed, ".coords");
        assertThat(coords[0]).isCloseTo(5.0, within(0.001));
        assertThat(coords[1]).isCloseTo(5.0, within(0.001));
    }

    /**
     * Translating a background preserves the existing rotation/scale components
     * (a, b, c, d) of its WORLD_TO_MAP matrix, changing only translation.
     */
    @Test
    void testBackgroundTranslate_preservesRotationScale() {
        model.onEntriesFetched(List.of(entry("background", 100,
                "{\"type\":\"background\","
                        + "\"tm-world-to-map\":[2,0.5,-0.5,2,10,20]}")));

        // Shift by (50, 60) so the translation lands on (60, 80).
        final int moved = model.transformFacts(List.of("background"),
                FloorMapTransformationMatrix.translate(50.0, 60.0),
                SCHEMA, ACCESSOR);
        assertThat(moved).isEqualTo(1);

        final TemporalEntry updated = model.buildMergedCanvasEntries().stream()
                .filter(e -> e.getKey().equals("background")).findFirst().orElseThrow();
        final double[] m = ACCESSOR.getArray(ACCESSOR.parse(updated.getValue()), ".tm-world-to-map");
        assertThat(m[0]).isCloseTo(2.0, within(0.001));
        assertThat(m[1]).isCloseTo(0.5, within(0.001));
        assertThat(m[2]).isCloseTo(-0.5, within(0.001));
        assertThat(m[3]).isCloseTo(2.0, within(0.001));
        assertThat(m[4]).isCloseTo(60.0, within(0.001));
        assertThat(m[5]).isCloseTo(80.0, within(0.001));
    }

    /**
     * When the background entry has no WORLD_TO_MAP matrix, it defaults to
     * identity before the translation is applied.
     */
    @Test
    void testBackgroundTranslate_defaultsToIdentityWhenMissing() {
        model.onEntriesFetched(List.of(entry("background", 100,
                "{\"type\":\"background\"}")));

        final int moved = model.transformFacts(List.of("background"),
                FloorMapTransformationMatrix.translate(60.0, 80.0),
                SCHEMA, ACCESSOR);
        assertThat(moved).isEqualTo(1);

        final TemporalEntry updated = model.buildMergedCanvasEntries().stream()
                .filter(e -> e.getKey().equals("background")).findFirst().orElseThrow();
        final double[] m = ACCESSOR.getArray(ACCESSOR.parse(updated.getValue()), ".tm-world-to-map");
        assertThat(m).containsExactly(1.0, 0.0, 0.0, 1.0, 60.0, 80.0);
    }

    // -----------------------------------------------------------------------
    // Pending changes integration
    // -----------------------------------------------------------------------

    /**
     * A pending creation appears in the merged canvas entries alongside the
     * server-known objects.
     */
    @Test
    void testPendingCreation_visibleInMergedList() {
        model.onEntriesFetched(List.of(entry("k1", 100, "{}")));
        model.getPendingChanges().recordCreation(entry("k2", 100, "{}"));
        final List<TemporalEntry> merged = model.buildMergedCanvasEntries();
        assertThat(merged).hasSize(2);
    }

    /**
     * A pending update overlays the server entry's value in the merged
     * canvas entries rather than appearing as an extra entry.
     */
    @Test
    void testPendingUpdate_replacesInMergedList() {
        model.onEntriesFetched(List.of(entry("k1", 100, "{\"old\":true}")));
        model.getPendingChanges().recordUpdate(entry("k1", 100, "{\"new\":true}"));
        final List<TemporalEntry> merged = model.buildMergedCanvasEntries();
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().getValue()).isEqualTo("{\"new\":true}");
    }

    /**
     * A pending deletion hides the matching object from the merged canvas
     * entries, leaving other objects visible.
     */
    @Test
    void testPendingDeletion_hiddenFromMergedList() {
        model.onEntriesFetched(List.of(
                entry("k1", 100, "{}"),
                entry("k2", 100, "{}")));
        model.getPendingChanges().recordDeletion(
                new stroom.util.shared.TemporalEntryId(MAP, "k1", 100L));
        final List<TemporalEntry> merged = model.buildMergedCanvasEntries();
        assertThat(merged).hasSize(1);
        assertThat(merged.getFirst().getKey()).isEqualTo("k2");
    }

    /**
     * A flush clears only the prefix it actually sent, so an edit made while the
     * save was in flight survives to be flushed next time.
     *
     * <p>This replaces a test of the old {@code clearPendingChanges()}, which
     * cleared the whole buffer and has been removed. Clearing everything is the
     * one thing a flush must not do: it is indistinguishable from a correct flush
     * right up until a user edits during a save, at which point their edit is
     * silently dropped.</p>
     */
    @Test
    void testFlushClearsOnlyTheSentPrefix() {
        model.getPendingChanges().recordCreation(entry("k1", 100, "{}"));
        assertThat(model.hasPendingChanges()).isTrue();

        // The presenter snapshots how much it is about to send.
        final int sentCount = model.getPendingChanges().getChanges().size();

        // The user edits again while the request is in flight.
        model.getPendingChanges().recordCreation(entry("k2", 200, "{}"));

        model.getPendingChanges().clearSent(sentCount);

        assertThat(model.hasPendingChanges())
                .as("the edit staged during the flush must survive")
                .isTrue();
        assertThat(model.getPendingChanges().getChanges())
                .hasSize(1);

        // Flushing the remainder empties the buffer.
        model.getPendingChanges().clearSent(
                model.getPendingChanges().getChanges().size());
        assertThat(model.hasPendingChanges()).isFalse();
    }

    /**
     * {@code clearSent} clamps, so an overlapping double-flush cannot clear more
     * than is there.
     */
    @Test
    void testClearSentClampsSoOverlappingFlushesAreIdempotent() {
        model.getPendingChanges().recordCreation(entry("k1", 100, "{}"));

        model.getPendingChanges().clearSent(99);

        assertThat(model.hasPendingChanges()).isFalse();
        assertThat(model.getPendingChanges().getChanges()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Time shard management
    // -----------------------------------------------------------------------

    /**
     * A fact with several time shards produces a merged time list
     * containing all of them, sorted by effective time.
     */
    @Test
    void testTimeListShards_multipleTimesForSameKey() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{\"v\":1}"),
                entry("k1", 200, "{\"v\":2}"),
                entry("k1", 300, "{\"v\":3}")));

        final List<TemporalEntry> timeList = model.buildMergedTimeList();
        assertThat(timeList).hasSize(3);
        assertThat(timeList.getFirst().getEffectiveTimeMs()).isEqualTo(100);
        assertThat(timeList.get(1).getEffectiveTimeMs()).isEqualTo(200);
        assertThat(timeList.get(2).getEffectiveTimeMs()).isEqualTo(300);
    }

    /**
     * Staging deletion of a middle time entry suggests selecting the index
     * immediately before the deleted entry's former position.
     */
    @Test
    void testTimeEntryDeletion_suggestsPreviousIndex() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"),
                entry("k1", 300, "{}")));
        model.setSelectedTime(200);

        final int suggestedIndex = model.stageTimeEntryDeletion(entry("k1", 200, "{}"));
        // After deleting 200, the list is [100, 300].
        // The entry at time 200 would have been at index 1, so suggestion is 0.
        assertThat(suggestedIndex).isEqualTo(0);
    }

    /**
     * Staging deletion of the first time entry, with nothing before it,
     * suggests no selection (index -1).
     */
    @Test
    void testTimeEntryDeletion_deletingFirst_suggestsMinusOne() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}")));
        model.setSelectedTime(100);

        final int suggestedIndex = model.stageTimeEntryDeletion(entry("k1", 100, "{}"));
        assertThat(suggestedIndex).isEqualTo(-1);
    }

    /**
     * A newly staged time version for the selected fact appears in the
     * merged time list at its effective time.
     */
    @Test
    void testAddTimeVersion_appearsInTimeList() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{}")));
        model.getPendingChanges().recordCreation(entry("k1", 200, "{\"new\":true}"));

        final List<TemporalEntry> timeList = model.buildMergedTimeList();
        assertThat(timeList).hasSize(2);
        assertThat(timeList.get(1).getEffectiveTimeMs()).isEqualTo(200);
    }

    /**
     * When entries for multiple facts are present, the merged time list only
     * shows entries for the currently selected fact.
     */
    @Test
    void testOnlySelectedFactShownInTimeList() {
        model.setSelectedFactKey("k1");
        model.setServerEntriesForSelectedFact(List.of(
                entry("k1", 100, "{}"),
                entry("k2", 100, "{}"),
                entry("k1", 200, "{}")));

        final List<TemporalEntry> timeList = model.buildMergedTimeList();
        assertThat(timeList).hasSize(2);
        assertThat(timeList).allSatisfy(e -> assertThat(e.getKey()).isEqualTo("k1"));
    }

    // -----------------------------------------------------------------------
    // parseForCanvas
    // -----------------------------------------------------------------------

    /**
     * Parsing a valid batch of entries for the canvas yields one fact per
     * entry — the image (background) fact and the regular object — with no
     * warnings emitted.
     */
    @Test
    void testParseForCanvas() {
        final List<TemporalEntry> entries = List.of(
                entry("bg", 100, "{\"type\":\"background\",\"img\":\"f.png\"}"),
                entry("g1", 100, "{\"type\":\"gate\",\"coords\":[10,20]}"));

        final List<Fact> facts = model.parseForCanvas(entries, SCHEMA, ACCESSOR);

        assertThat(facts).hasSize(2);
        assertThat(facts.stream().filter(Fact::hasImage).findFirst().orElseThrow().getImage())
                .isEqualTo("f.png");
        assertThat(facts.stream().filter(f -> !f.hasImage()).count()).isEqualTo(1L);
        assertThat(warnings).as("valid entries should not emit warnings").isEmpty();
    }

    /**
     * The canvas shows, per key, the single shard active at the scrubber time —
     * so an object never renders (or drags) as several overlaid time versions,
     * and moving the scrubber changes which shard is shown. Here the scrubber
     * sits between shards, so the earlier one is active and the later is ignored.
     */
    @Test
    void testParseForCanvas_showsShardActiveAtSelectedTime() {
        model.setSelectedTime(250);
        final List<TemporalEntry> entries = List.of(
                entry("bg", 100, "{\"type\":\"background\",\"img\":\"old.png\"}"),
                entry("bg", 300, "{\"type\":\"background\",\"img\":\"future.png\"}"),
                entry("bg", 200, "{\"type\":\"background\",\"img\":\"active.png\"}"),
                entry("g1", 100, "{\"type\":\"gate\",\"coords\":[10,20]}"));

        final List<Fact> facts = model.parseForCanvas(entries, SCHEMA, ACCESSOR);

        assertThat(facts).as("one fact per key").hasSize(2);
        final Fact bg = facts.stream().filter(f -> "bg".equals(f.getKey())).findFirst().orElseThrow();
        assertThat(bg.getImage())
                .as("shard active at t=250 (t=200) wins; the future t=300 shard is ignored")
                .isEqualTo("active.png");
        assertThat(facts.stream().anyMatch(f -> "g1".equals(f.getKey()))).isTrue();
    }

    /**
     * When the entry list holds several shards per key (e.g. pending changes
     * staged at other effective times overlaid on the server data) and the
     * scrubber sits at/after all of them, each key shows its latest active
     * shard — no key is dropped.
     */
    @Test
    void testParseForCanvas_scrubberAtLatest_keepsAllKeys() {
        model.setSelectedTime(1000);
        final List<TemporalEntry> entries = List.of(
                entry("bg", 100, "{\"type\":\"background\",\"img\":\"old.png\"}"),
                entry("bg", 300, "{\"type\":\"background\",\"img\":\"new.png\"}"),
                entry("g1", 200, "{\"type\":\"gate\",\"coords\":[10,20]}"));

        final List<Fact> facts = model.parseForCanvas(entries, SCHEMA, ACCESSOR);

        assertThat(facts).as("one fact per key, none dropped").hasSize(2);
        final Fact bg = facts.stream().filter(f -> "bg".equals(f.getKey())).findFirst().orElseThrow();
        assertThat(bg.getImage()).as("latest active shard (t=300) wins").isEqualTo("new.png");
    }

    /**
     * With the scrubber time unset ({@code <= 0}) the time filter is skipped and
     * the latest shard per key wins, so the canvas is not blanked before the
     * scrubber is initialised.
     */
    @Test
    void testParseForCanvas_collapsesToLatestWhenTimeUnset() {
        model.setSelectedTime(0);
        final List<TemporalEntry> entries = List.of(
                entry("bg", 100, "{\"type\":\"background\",\"img\":\"old.png\"}"),
                entry("bg", 300, "{\"type\":\"background\",\"img\":\"new.png\"}"));

        final List<Fact> facts = model.parseForCanvas(entries, SCHEMA, ACCESSOR);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getImage()).as("latest shard (t=300) wins").isEqualTo("new.png");
    }

    /**
     * A malformed entry in the canvas batch is skipped and reported via the
     * model's warning callback, while the well-formed entry still parses.
     */
    @Test
    void testParseForCanvas_malformedEntry_emitsWarning() {
        final List<TemporalEntry> entries = List.of(
                entry("good", 100, "{\"type\":\"gate\",\"coords\":[5,10]}"),
                entry("bad", 100, "not-json"));

        final List<Fact> facts = model.parseForCanvas(entries, SCHEMA, ACCESSOR);

        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().getKey()).isEqualTo("good");
        assertThat(warnings).as("malformed entry should trigger a warning")
                .hasSize(1);
        assertThat(warnings.getFirst()).contains("bad");
    }

    /**
     * Every malformed entry in the canvas batch produces its own warning via
     * the model's warning callback.
     */
    @Test
    void testParseForCanvas_multipleMalformed_emitsAllWarnings() {
        final List<TemporalEntry> entries = List.of(
                entry("bad1", 100, "xxx"),
                entry("bad2", 100, "yyy"));

        model.parseForCanvas(entries, SCHEMA, ACCESSOR);

        assertThat(warnings).hasSize(2);
        assertThat(warnings.getFirst()).contains("bad1");
        assertThat(warnings.get(1)).contains("bad2");
    }

    /**
     * Parsing an empty entry list for the canvas emits no warnings.
     */
    @Test
    void testParseForCanvas_noWarningsOnEmpty() {
        model.parseForCanvas(List.of(), SCHEMA, ACCESSOR);
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // buildUpdatedEntryWithMatrix
    // -----------------------------------------------------------------------

    /**
     * The static helper writes the full six-component matrix into the requested
     * role's field.
     */
    @Test
    void testBuildUpdatedEntryWithMatrix_writesFullMatrix() {
        final TemporalEntry original = entry("g1", 100, "{\"type\":\"gate\"}");
        final TemporalEntry updated = FloorMapEditorModel.buildUpdatedEntryWithMatrix(
                original, FloorMapFieldMapping.Role.WORLD_TO_MAP,
                new FloorMapTransformationMatrix(1, 2, 3, 4, 5, 6), SCHEMA, ACCESSOR);
        final double[] m = ACCESSOR.getArray(ACCESSOR.parse(updated.getValue()), ".tm-world-to-map");
        assertThat(m).containsExactly(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
    }

    // -----------------------------------------------------------------------
    // Selection as a set
    // -----------------------------------------------------------------------

    /** The single-select façade sets/clears the whole selection. */
    @Test
    void testSelection_singleSelectFacade() {
        model.setSelectedFactKey("a");
        assertThat(model.getSelectedFactKey()).isEqualTo("a");
        assertThat(model.getSelectedFactKeys()).containsExactly("a");

        model.setSelectedFactKey(null);
        assertThat(model.getSelectedFactKey()).isNull();
        assertThat(model.getSelectedFactKeys()).isEmpty();
    }

    /** setSelection replaces the whole selection, preserving order. */
    @Test
    void testSelection_setSelection() {
        model.setSelection(List.of("x", "y", "z"));
        assertThat(model.getSelectedFactKeys()).containsExactly("x", "y", "z");
        assertThat(model.getSelectedFactKey()).isEqualTo("x");
    }

    /** Deleting a selected fact removes it from the selection. */
    @Test
    void testSelection_stageDeletionDeselects() {
        final TemporalEntry shard = entry("k1", 100, "{}");
        model.onEntriesFetched(List.of(shard));
        model.setSelectedFactKey("k1");
        model.stageFactDeletionForAllShards("k1", List.of(shard));
        assertThat(model.isSelected("k1")).isFalse();
    }

    // -----------------------------------------------------------------------
    // transformFacts (group move / rotate / scale via a map-space transform)
    // -----------------------------------------------------------------------

    /**
     * Rotating a group 90° about a pivot repositions each fact (its e,f) AND
     * rotates its own orientation (a,b,c,d). Facts a=(anchor 5,10) and
     * b=(anchor 15,10) rotate 90° CCW about (10,10) → anchors (10,5) and (10,15).
     */
    @Test
    void testTransformFacts_groupRotate90_repositionsAndReorients() {
        model.onEntriesFetched(List.of(
                entry("a", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,5,10]}"),
                entry("b", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,15,10]}")));

        final int n = model.transformFacts(List.of("a", "b"),
                FloorMapTransformationMatrix.rotateAbout(90, 10, 10), SCHEMA, ACCESSOR);

        assertThat(n).isEqualTo(2);
        assertMatrix(mergedMatrix("a"), 0, 1, -1, 0, 10, 5);
        assertMatrix(mergedMatrix("b"), 0, 1, -1, 0, 10, 15);
    }

    /** Scaling a group 2× about a pivot scales orientation and spreads anchors. */
    @Test
    void testTransformFacts_groupScale2xAboutPivot() {
        model.onEntriesFetched(List.of(
                entry("a", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,5,10]}"),
                entry("b", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,15,10]}")));

        model.transformFacts(List.of("a", "b"),
                FloorMapTransformationMatrix.scaleAbout(2, 2, 10, 10), SCHEMA, ACCESSOR);

        assertMatrix(mergedMatrix("a"), 2, 0, 0, 2, 0, 10);
        assertMatrix(mergedMatrix("b"), 2, 0, 0, 2, 20, 10);
    }

    /**
     * A single fact rotated about its own anchor keeps its anchor fixed and
     * takes on the rotation in a,b,c,d.
     */
    @Test
    void testTransformFacts_singleFactRotateAboutOwnCentre() {
        model.onEntriesFetched(List.of(
                entry("g", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,50,50]}")));

        model.transformFacts(List.of("g"),
                FloorMapTransformationMatrix.rotateAbout(45, 50, 50), SCHEMA, ACCESSOR);

        final double cos45 = Math.cos(Math.toRadians(45));
        assertMatrix(mergedMatrix("g"), cos45, cos45, -cos45, cos45, 50, 50);
    }

    /** A missing matrix defaults to identity before the transform is applied. */
    @Test
    void testTransformFacts_defaultsToIdentityWhenNoMatrix() {
        model.onEntriesFetched(List.of(entry("g", 100, "{\"type\":\"img\"}")));

        model.transformFacts(List.of("g"),
                FloorMapTransformationMatrix.translate(5, 5), SCHEMA, ACCESSOR);

        assertMatrix(mergedMatrix("g"), 1, 0, 0, 1, 5, 5);
    }

    /** Unknown ids are skipped; the return count reflects only facts found. */
    @Test
    void testTransformFacts_batchSkipsUnknown() {
        model.onEntriesFetched(List.of(
                entry("g1", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,0,0]}"),
                entry("g2", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,0,0]}")));

        final int n = model.transformFacts(List.of("g1", "g2", "ghost"),
                FloorMapTransformationMatrix.scale(2, 2), SCHEMA, ACCESSOR);
        assertThat(n).isEqualTo(2);
    }

    /** Null/empty id collections are a no-op returning zero. */
    @Test
    void testTransformFacts_emptyIds_returnsZero() {
        assertThat(model.transformFacts(List.of(),
                FloorMapTransformationMatrix.scale(2, 2), SCHEMA, ACCESSOR)).isZero();
    }

    /** A null/empty schema throws. */
    @Test
    void testTransformFacts_noSchema_throws() {
        model.onEntriesFetched(List.of(
                entry("g", 100, "{\"type\":\"img\",\"tm-world-to-map\":[1,0,0,1,0,0]}")));
        assertThatThrownBy(() -> model.transformFacts(List.of("g"),
                FloorMapTransformationMatrix.scale(2, 2), List.of(), ACCESSOR))
                .isInstanceOf(IllegalStateException.class);
    }

    /** The transform edits WORLD_TO_MAP only; POSITION coords are untouched. */
    @Test
    void testTransformFacts_leavesPositionCoordsUntouched() {
        model.onEntriesFetched(List.of(
                entry("g", 100,
                        "{\"type\":\"img\",\"coords\":[7,9],\"tm-world-to-map\":[1,0,0,1,0,0]}")));

        model.transformFacts(List.of("g"),
                FloorMapTransformationMatrix.rotateAbout(30, 0, 0), SCHEMA, ACCESSOR);

        final TemporalEntry e = model.buildMergedCanvasEntries().stream()
                .filter(x -> x.getKey().equals("g")).findFirst().orElseThrow();
        final double[] coords = ACCESSOR.getArray(ACCESSOR.parse(e.getValue()), ".coords");
        assertThat(coords).containsExactly(7.0, 9.0);
    }

    private double[] mergedMatrix(final String key) {
        final TemporalEntry e = model.buildMergedCanvasEntries().stream()
                .filter(x -> x.getKey().equals(key)).findFirst().orElseThrow();
        return ACCESSOR.getArray(ACCESSOR.parse(e.getValue()), ".tm-world-to-map");
    }

    private static void assertMatrix(final double[] m,
                                     final double a, final double b, final double c,
                                     final double d, final double e, final double f) {
        assertThat(m[0]).isCloseTo(a, within(0.001));
        assertThat(m[1]).isCloseTo(b, within(0.001));
        assertThat(m[2]).isCloseTo(c, within(0.001));
        assertThat(m[3]).isCloseTo(d, within(0.001));
        assertThat(m[4]).isCloseTo(e, within(0.001));
        assertThat(m[5]).isCloseTo(f, within(0.001));
    }

    // -----------------------------------------------------------------------
    // onTimeListFetched
    // -----------------------------------------------------------------------

    /**
     * Entries fetched for the time list are stored sorted by effective time,
     * regardless of the order they were fetched in.
     */
    @Test
    void testOnTimeListFetched_sortsEntries() {
        model.onTimeListFetched(List.of(
                entry("k1", 300, "{}"),
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}")));

        final List<TemporalEntry> stored = model.getServerEntriesForSelectedFact();
        assertThat(stored.getFirst().getEffectiveTimeMs()).isEqualTo(100);
        assertThat(stored.get(1).getEffectiveTimeMs()).isEqualTo(200);
        assertThat(stored.get(2).getEffectiveTimeMs()).isEqualTo(300);
    }

    /**
     * Fetching a {@code null} time list is treated as an empty result rather
     * than throwing.
     */
    @Test
    void testOnTimeListFetched_null_yieldsEmpty() {
        model.onTimeListFetched(null);
        assertThat(model.getServerEntriesForSelectedFact()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // buildAreaEntry
    // -----------------------------------------------------------------------

    /**
     * Map-space vertices are stored in the local frame centred on their
     * centroid, placed by {@code WORLD_TO_MAP = translate(centroid)}, so
     * local + translation reproduces the original map coordinates and the
     * scale/rotate handles pivot about the middle.
     */
    @Test
    void testBuildAreaEntry_centroidLocalFrame() {
        final List<double[]> mapVertices = List.of(
                new double[]{0, 0},
                new double[]{100, 0},
                new double[]{100, 60},
                new double[]{0, 60});

        final TemporalEntry entry = FloorMapEditorModel.buildAreaEntry(
                MAP, "area-1", mapVertices, 0L,
                FloorMapFieldMapping.withAreaMappings(SCHEMA, ValueFormat.JSON),
                ACCESSOR);

        assertThat(entry.getKey()).isEqualTo("area-1");
        assertThat(entry.getEffectiveTimeMs()).isZero();

        final ParsedValue parsed = ACCESSOR.parse(entry.getValue());
        assertThat(ACCESSOR.getString(parsed, ".type")).isEqualTo(FloorMapJsonKeys.AREA);
        assertThat(ACCESSOR.getString(parsed, ".name")).isEqualTo("area-1");
        assertThat(ACCESSOR.getArray(parsed, ".coords")).containsExactly(0, 0);

        // Centroid of the rectangle is (50, 30).
        final double[] m = ACCESSOR.getArray(parsed, ".tm-world-to-map");
        assertThat(m).containsExactly(1, 0, 0, 1, 50, 30);

        final double[] geometry = ACCESSOR.getArray(parsed, ".geometry");
        assertThat(geometry).hasSize(8);
        // Local vertices + translation == the original map vertices.
        for (int i = 0; i < 4; i++) {
            assertThat(geometry[i * 2] + m[4]).isCloseTo(mapVertices.get(i)[0], within(1e-9));
            assertThat(geometry[i * 2 + 1] + m[5]).isCloseTo(mapVertices.get(i)[1], within(1e-9));
        }
    }

    /** Fewer than three vertices is rejected. */
    @Test
    void testBuildAreaEntry_tooFewVertices() {
        assertThatThrownBy(() -> FloorMapEditorModel.buildAreaEntry(
                MAP, "area-1", List.of(new double[]{0, 0}, new double[]{1, 1}), 0L,
                FloorMapFieldMapping.withAreaMappings(SCHEMA, ValueFormat.JSON),
                ACCESSOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A schema without the GEOMETRY role fails loudly instead of silently
     * writing to a null path (which the accessors ignore).
     */
    @Test
    void testBuildAreaEntry_missingRoleFailsLoudly() {
        assertThatThrownBy(() -> FloorMapEditorModel.buildAreaEntry(
                MAP, "area-1",
                List.of(new double[]{0, 0}, new double[]{1, 0}, new double[]{1, 1}),
                0L, SCHEMA_WITHOUT_AREA_ROLES, ACCESSOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEOMETRY");
    }

    private static final List<FloorMapFieldMapping> SCHEMA_WITHOUT_AREA_ROLES = List.of(
            new FloorMapFieldMapping(".type", FloorMapFieldMapping.Role.TYPE, "Type", null),
            new FloorMapFieldMapping(".name", FloorMapFieldMapping.Role.LABEL, "Name", null),
            new FloorMapFieldMapping(".coords", FloorMapFieldMapping.Role.POSITION, "Coords", null),
            new FloorMapFieldMapping(".tm-world-to-map",
                    FloorMapFieldMapping.Role.WORLD_TO_MAP, null, null));

    // -----------------------------------------------------------------------
    // Edits target the shard active at the scrubber (regression)
    // -----------------------------------------------------------------------

    /**
     * With a pending time-version at a later effective time than the server
     * shard, a transform must update the shard the canvas is showing (the one
     * active at {@code selectedTime}), not merely the first key match — which
     * would silently move the historical server shard instead.
     */
    @Test
    void testTransformFacts_targetsActiveShardNotFirstMatch() {
        // Server shard at t=100 (translation 10,20); pending time-version at
        // t=200 (translation 30,40). applyTo appends the creation after the
        // server entry, so the naive "first match" is the t=100 server shard.
        model.onEntriesFetched(List.of(entry("k1", 100,
                "{\"tm-world-to-map\":[1,0,0,1,10,20]}")));
        model.getPendingChanges().recordCreation(entry("k1", 200,
                "{\"tm-world-to-map\":[1,0,0,1,30,40]}"));
        // Scrubber on the later shard — the canvas renders t=200.
        model.setSelectedTime(200L);

        model.transformFacts(List.of("k1"),
                FloorMapTransformationMatrix.translate(5.0, 6.0),
                SCHEMA, ACCESSOR);

        final List<TemporalEntry> merged = model.buildMergedCanvasEntries();
        final TemporalEntry shard200 = merged.stream()
                .filter(e -> e.getKey().equals("k1") && e.getEffectiveTimeMs() == 200)
                .findFirst().orElseThrow();
        final TemporalEntry shard100 = merged.stream()
                .filter(e -> e.getKey().equals("k1") && e.getEffectiveTimeMs() == 100)
                .findFirst().orElseThrow();
        final double[] m200 = ACCESSOR.getArray(ACCESSOR.parse(shard200.getValue()), ".tm-world-to-map");
        final double[] m100 = ACCESSOR.getArray(ACCESSOR.parse(shard100.getValue()), ".tm-world-to-map");
        // The active (t=200) shard moved to (35,46); the historical shard is untouched.
        assertThat(m200[4]).isCloseTo(35.0, within(0.001));
        assertThat(m200[5]).isCloseTo(46.0, within(0.001));
        assertThat(m100[4]).isCloseTo(10.0, within(0.001));
        assertThat(m100[5]).isCloseTo(20.0, within(0.001));
    }

    /**
     * A geometry edit on a schema with no {@code GEOMETRY} mapping must be a
     * no-op that stages nothing (and so leaves the document clean), rather than
     * recording an identical Update that only marks the doc dirty.
     */
    @Test
    void testUpdateFactGeometry_noGeometryRole_stagesNothing() {
        model.onEntriesFetched(List.of(entry("area-1", 100, "{\"type\":\"area\"}")));
        assertThat(model.getPendingChanges().isDirty()).isFalse();

        final boolean changed = model.updateFactGeometry("area-1",
                new double[][]{{0, 0}, {1, 0}, {1, 1}},
                SCHEMA_WITHOUT_AREA_ROLES, ACCESSOR);

        assertThat(changed).isFalse();
        assertThat(model.getPendingChanges().isDirty()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Delete all shards (regression)
    // -----------------------------------------------------------------------

    /**
     * Deleting a fact must stage a deletion for every shard supplied (all
     * effective times), plus any pending creation for the key — so no time
     * version survives to resurrect the fact.
     */
    @Test
    void testStageFactDeletionForAllShards_deletesEveryVersion() {
        final List<TemporalEntry> serverShards = List.of(
                entry("k1", 100, "{}"),
                entry("k1", 200, "{}"),
                entry("k1", 300, "{}"));
        // A pending, never-saved creation at a further time must also go.
        model.getPendingChanges().recordCreation(entry("k1", 400, "{}"));

        final boolean staged = model.stageFactDeletionForAllShards("k1", serverShards);

        assertThat(staged).isTrue();
        // Applying the staged deletions over the full shard set leaves nothing.
        final List<TemporalEntry> afterServer = model.getPendingChanges().applyTo(serverShards);
        assertThat(afterServer).noneMatch(e -> e.getKey().equals("k1"));
    }

    /**
     * {@link FloorMapEditorModel#selectedFactHasEntryAtTime(long)} detects an
     * existing shard at an exact effective time (so "Add Time Version" can warn
     * instead of overwriting).
     */
    @Test
    void testSelectedFactHasEntryAtTime() {
        model.setSelectedFactKey("k1");
        model.onTimeListFetched(List.of(entry("k1", 100, "{}"), entry("k1", 200, "{}")));

        assertThat(model.selectedFactHasEntryAtTime(100)).isTrue();
        assertThat(model.selectedFactHasEntryAtTime(200)).isTrue();
        assertThat(model.selectedFactHasEntryAtTime(150)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TemporalEntry entry(final String key, final long time,
                                        final String value) {
        return new TemporalEntry(MAP, key, time, value);
    }

    // ---- resolveEntryKey -------------------------------------------------
    // An edit belongs to the key of the entry being edited, never to whatever is
    // currently selected: taking it from the selection means right-clicking fact B
    // while fact A is selected writes B's values under A's key. These pin that
    // rule down.

    @Test
    void testResolveEntryKey_prefersTheEntrysOwnKey() {
        // The whole point: a stale fallback must never win over a real entry.
        assertThat(FloorMapEditorModel.resolveEntryKey(entry("factB", 1000, "{}"), "staleFactA"))
                .isEqualTo("factB");
    }

    @Test
    void testResolveEntryKey_fallsBackOnlyWhenThereIsNoEntry() {
        // A blank new-object form has no entry, so the caller's key applies.
        assertThat(FloorMapEditorModel.resolveEntryKey(null, "newFact")).isEqualTo("newFact");
    }

    @Test
    void testResolveEntryKey_fallsBackWhenTheEntryHasNoKey() {
        assertThat(FloorMapEditorModel.resolveEntryKey(entry(null, 1000, "{}"), "newFact"))
                .isEqualTo("newFact");
        assertThat(FloorMapEditorModel.resolveEntryKey(entry("", 1000, "{}"), "newFact"))
                .isEqualTo("newFact");
    }

    @Test
    void testResolveEntryKey_nullWhenNeitherSourceHasAKey() {
        assertThat(FloorMapEditorModel.resolveEntryKey(null, null)).isNull();
    }
}
