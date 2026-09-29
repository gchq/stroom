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

import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.FloorMapMeasurementUnits.Unit;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.TypeStyle.Shape;
import stroom.floormap.shared.ValueFormat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapDocSession {

    /// A pre-area schema (no Geometry/Fill/Opacity roles).
    private static final List<FloorMapFieldMapping> PRE_AREA_SCHEMA = List.of(
            new FloorMapFieldMapping(".type", Role.TYPE, "Type", null),
            new FloorMapFieldMapping(".coords", Role.POSITION, "Coords", null),
            new FloorMapFieldMapping(".tm-world-to-map", Role.WORLD_TO_MAP, null, null));

    private static final TypeStyle GATE = new TypeStyle("gate", Shape.CIRCLE, "#ff0000");

    private FloorMapDocSession session;

    @BeforeEach
    void setUp() {
        session = new FloorMapDocSession();
    }

    private static FloorMapDoc doc(final List<TypeStyle> typeStyles) {
        return FloorMapDoc.builder()
                .uuid("test-uuid")
                .name("test-map")
                .valueFormat(ValueFormat.JSON)
                .valueSchema(TestFloorMapDocSession.PRE_AREA_SCHEMA)
                .typeStyles(typeStyles)
                .build();
    }

    // -----------------------------------------------------------------------

    /// With nothing staged, the effective lists are the entity's and write is a no-op.
    @Test
    void testNoPendingEdits() {
        final FloorMapDoc d = doc(List.of(GATE));
        assertThat(session.hasPendingDocEdits()).isFalse();
        assertThat(session.valueSchema(d.getValueSchema())).isEqualTo(PRE_AREA_SCHEMA);
        assertThat(session.typeStyles(d.getTypeStyles())).containsExactly(GATE);
        assertThat(session.applyToWrite(d)).isSameAs(d);
    }

    /// A Layers-panel edit becomes the effective + written type styles.
    @Test
    void testStageTypeStyles() {
        final FloorMapDoc d = doc(List.of(GATE));
        final TypeStyle door = new TypeStyle("door", Shape.SQUARE, "#00ff00");
        session.stageTypeStyles(List.of(GATE, door));

        assertThat(session.typeStyles(d.getTypeStyles())).containsExactly(GATE, door);
        assertThat(session.applyToWrite(d).getTypeStyles()).containsExactly(GATE, door);
    }

    /// Staging the area upgrade adds the area schema roles and the "area" style.
    @Test
    void testStageAreaUpgrade() {
        final FloorMapDoc d = doc(List.of(GATE));
        session.stageAreaUpgrade(d.getValueSchema(), d.getValueFormat(), d.getTypeStyles());

        assertThat(FloorMapDocSession.hasAreaSupport(session.valueSchema(d.getValueSchema()))).isTrue();
        assertThat(FloorMapDocSession.hasAreaStyle(session.typeStyles(d.getTypeStyles()))).isTrue();

        final FloorMapDoc written = session.applyToWrite(d);
        assertThat(FloorMapDocSession.hasAreaSupport(written.getValueSchema())).isTrue();
        assertThat(FloorMapDocSession.hasAreaStyle(written.getTypeStyles())).isTrue();
    }

    /// A Layers edit made around a pending area upgrade must NOT drop the "area"
    /// style — onWrite folds it in (the wedge-fix regression).
    @Test
    void testLayersEditAroundAreaUpgradeKeepsAreaStyle() {
        final FloorMapDoc d = doc(List.of(GATE));
        session.stageAreaUpgrade(d.getValueSchema(), d.getValueFormat(), d.getTypeStyles());
        // Layers panel then reorders/edits, producing a list WITHOUT the area style.
        session.stageTypeStyles(List.of(GATE));

        final FloorMapDoc written = session.applyToWrite(d);
        assertThat(FloorMapDocSession.hasAreaSupport(written.getValueSchema())).isTrue();
        assertThat(FloorMapDocSession.hasAreaStyle(written.getTypeStyles())).isTrue();
    }

    /// The area upgrade is dropped after read only once schema AND style are present.
    @Test
    void testReconcileDropsAreaUpgradeWhenPersisted() {
        final FloorMapDoc d = doc(List.of(GATE));
        session.stageAreaUpgrade(d.getValueSchema(), d.getValueFormat(), d.getTypeStyles());

        // Re-read of the still-old doc: upgrade stays pending.
        session.reconcileAfterRead(d);
        assertThat(session.hasPendingDocEdits()).isTrue();

        // Re-read of the saved (upgraded) doc: upgrade is dropped.
        session.reconcileAfterRead(session.applyToWrite(d));
        assertThat(session.hasPendingDocEdits()).isFalse();
    }

    /// sessionEntity applies pending edits to the returned document.
    @Test
    void testSessionEntityAppliesPending() {
        final FloorMapDoc d = doc(List.of(GATE));
        session.stageAreaUpgrade(d.getValueSchema(), d.getValueFormat(), d.getTypeStyles());

        final FloorMapDoc effective = session.sessionEntity(d);
        assertThat(FloorMapDocSession.hasAreaSupport(effective.getValueSchema())).isTrue();
        assertThat(FloorMapDocSession.hasAreaStyle(effective.getTypeStyles())).isTrue();
    }

    // -----------------------------------------------------------------------
    // Groups (Map tab)
    // -----------------------------------------------------------------------

    private static final FloorMapGroup MAINTENANCE =
            new FloorMapGroup("g1", "Maintenance", "#8e24aa", List.of("bob@x.com"));

    /// A Groups-panel edit becomes the effective + written groups.
    @Test
    void testStageGroups() {
        final FloorMapDoc d = doc(List.of(GATE));
        session.stageGroups(List.of(MAINTENANCE));

        assertThat(session.hasPendingDocEdits()).isTrue();
        assertThat(session.groups(d.getGroups())).containsExactly(MAINTENANCE);
        assertThat(session.applyToWrite(d).getGroups()).containsExactly(MAINTENANCE);
    }

    /// With nothing staged, the document's own groups stand and write is a no-op.
    @Test
    void testGroupsWithoutPendingEdit() {
        final FloorMapDoc d = doc(List.of(GATE))
                .copy().groups(List.of(MAINTENANCE)).build();

        assertThat(session.groups(d.getGroups())).containsExactly(MAINTENANCE);
        assertThat(session.applyToWrite(d)).isSameAs(d);
    }

    /// The Editor's schema/type-style staging and the Map's group staging touch
    /// disjoint fields, so one tab's pending edit can never clobber the other's.
    @Test
    void testGroupsAndTypeStylesDoNotInterfere() {
        final FloorMapDoc d = doc(List.of(GATE));
        final TypeStyle door = new TypeStyle("door", Shape.SQUARE, "#00ff00");
        session.stageTypeStyles(List.of(GATE, door));
        session.stageGroups(List.of(MAINTENANCE));

        final FloorMapDoc written = session.applyToWrite(d);
        assertThat(written.getTypeStyles()).containsExactly(GATE, door);
        assertThat(written.getGroups()).containsExactly(MAINTENANCE);
    }

    /// The groups edit is dropped once a re-read shows it persisted.
    @Test
    void testReconcileDropsGroupsWhenPersisted() {
        final FloorMapDoc d = doc(List.of(GATE));
        session.stageGroups(List.of(MAINTENANCE));

        // Re-read of the doc without the groups: the edit stays pending.
        session.reconcileAfterRead(d);
        assertThat(session.hasPendingDocEdits()).isTrue();

        // Re-read of the saved doc: dropped.
        session.reconcileAfterRead(session.applyToWrite(d));
        assertThat(session.hasPendingDocEdits()).isFalse();
    }

    /// Deleting the last group stages an empty list; a document carrying either
    /// `null` or `[]` counts as having persisted it. Left pending, the
    /// document would stay dirty forever.
    @Test
    void testReconcileTreatsEmptyAndNullGroupsAsEqual() {
        final FloorMapDoc noGroups = doc(List.of(GATE));
        session.stageGroups(List.of());
        session.reconcileAfterRead(noGroups);

        assertThat(session.hasPendingDocEdits()).isFalse();
    }

    /// A rename is staged and written like any other group edit.
    @Test
    void testStageRenamedGroup() {
        final FloorMapDoc d = doc(List.of(GATE))
                .copy().groups(List.of(MAINTENANCE)).build();
        session.stageGroups(FloorMapGroup.replace(
                d.getGroups(), MAINTENANCE.withName("Night Maintenance")));

        final List<FloorMapGroup> written = session.applyToWrite(d).getGroups();
        assertThat(written).hasSize(1);
        assertThat(written.getFirst().getId()).isEqualTo("g1");
        assertThat(written.getFirst().getName()).isEqualTo("Night Maintenance");
        assertThat(written.getFirst().getMemberIds()).containsExactly("bob@x.com");
    }

    // -----------------------------------------------------------------------
    // The copy-builder trap
    // -----------------------------------------------------------------------

    /// Every FloorMap tab's `onWrite` returns `doc.copy()…build()`, so
    /// a field the copy-builder forgets is silently deleted when the user saves
    /// from a tab that does not itself write it. This is the one-line test that
    /// catches "saving from the Settings tab wiped all my groups".
    @Test
    void testCopyBuilderPreservesGroups() {
        final FloorMapDoc d = doc(List.of(GATE))
                .copy().groups(List.of(MAINTENANCE)).build();

        // Stand-in for another tab's onWrite, which touches only its own fields.
        final FloorMapDoc rewritten = d.copy().eventsQuery("from x select y").build();

        assertThat(rewritten.getGroups()).containsExactly(MAINTENANCE);
    }

    /// The document's dirty check diffs the written doc against the read one, so a
    /// group edit has to make the two unequal or the save button never lights up.
    @Test
    void testGroupEditMakesDocumentUnequal() {
        final FloorMapDoc before = doc(List.of(GATE))
                .copy().groups(List.of(MAINTENANCE)).build();
        final FloorMapDoc afterRename = before.copy()
                .groups(List.of(MAINTENANCE.withName("Night Maintenance"))).build();
        final FloorMapDoc afterMemberAdd = before.copy()
                .groups(List.of(MAINTENANCE.withMember("sue@x.com"))).build();
        final FloorMapDoc afterDelete = before.copy().groups(List.of()).build();

        assertThat(afterRename).isNotEqualTo(before);
        assertThat(afterMemberAdd).isNotEqualTo(before);
        assertThat(afterDelete).isNotEqualTo(before);
        // ...and an unchanged rewrite must NOT read as dirty.
        assertThat(before.copy().build()).isEqualTo(before);
    }

    // -----------------------------------------------------------------------
    // Measurement units (Set Scale)
    // -----------------------------------------------------------------------

    /// A calibration becomes the session's effective units and is written on save.
    @Test
    void testStageMeasurementUnits() {
        final FloorMapDoc d = doc(List.of(GATE));
        final FloorMapMeasurementUnits units = new FloorMapMeasurementUnits(Unit.METRE, 0.187);
        session.stageMeasurementUnits(units);

        assertThat(session.hasPendingDocEdits()).isTrue();
        assertThat(session.measurementUnits(d.getMeasurementUnits())).isEqualTo(units);
        assertThat(session.applyToWrite(d).getMeasurementUnits()).isEqualTo(units);
        assertThat(session.sessionEntity(d).getMeasurementUnits()).isEqualTo(units);
    }

    /// With nothing staged the entity's own units show through untouched.
    @Test
    void testUnstagedUnitsComeFromTheEntity() {
        final FloorMapMeasurementUnits stored = new FloorMapMeasurementUnits(Unit.FOOT, 2.0);
        final FloorMapDoc d = doc(List.of(GATE)).copy().measurementUnits(stored).build();

        assertThat(session.measurementUnits(d.getMeasurementUnits())).isEqualTo(stored);
        assertThat(session.applyToWrite(d)).isSameAs(d);
    }

    /// Staging null — "this map has no scale" — is a real edit, and must be
    /// distinguishable from having staged nothing at all.
    @Test
    void testStagingNullUnitsClearsTheScale() {
        final FloorMapMeasurementUnits stored = new FloorMapMeasurementUnits(Unit.METRE, 1.0);
        final FloorMapDoc d = doc(List.of(GATE)).copy().measurementUnits(stored).build();
        session.stageMeasurementUnits(null);

        assertThat(session.hasPendingDocEdits()).isTrue();
        assertThat(session.measurementUnits(d.getMeasurementUnits())).isNull();
        assertThat(session.applyToWrite(d).getMeasurementUnits()).isNull();
    }

    /// Once the document comes back carrying the calibration, the staged copy is dropped.
    @Test
    void testReconcileDropsPersistedUnits() {
        final FloorMapMeasurementUnits units = new FloorMapMeasurementUnits(Unit.METRE, 0.187);
        final FloorMapDoc saved = doc(List.of(GATE)).copy().measurementUnits(units).build();
        session.stageMeasurementUnits(units);

        session.reconcileAfterRead(saved);

        assertThat(session.hasPendingDocEdits()).isFalse();
    }

    /// A read that does NOT carry the calibration must keep it staged.
    @Test
    void testReconcileKeepsUnsavedUnits() {
        final FloorMapDoc unsaved = doc(List.of(GATE));
        session.stageMeasurementUnits(new FloorMapMeasurementUnits(Unit.METRE, 0.187));

        session.reconcileAfterRead(unsaved);

        assertThat(session.hasPendingDocEdits()).isTrue();
        assertThat(session.applyToWrite(unsaved).getMeasurementUnits())
                .isEqualTo(new FloorMapMeasurementUnits(Unit.METRE, 0.187));
    }

    /// Staged units must not disturb the other staged edits, nor they it.
    @Test
    void testUnitsComposeWithOtherStagedEdits() {
        final FloorMapDoc d = doc(List.of(GATE));
        final FloorMapMeasurementUnits units = new FloorMapMeasurementUnits(Unit.METRE, 0.5);
        session.stageAreaUpgrade(d.getValueSchema(), d.getValueFormat(), d.getTypeStyles());
        session.stageMeasurementUnits(units);

        final FloorMapDoc written = session.applyToWrite(d);
        assertThat(written.getMeasurementUnits()).isEqualTo(units);
        assertThat(FloorMapDocSession.hasAreaSupport(written.getValueSchema())).isTrue();
        assertThat(FloorMapDocSession.hasAreaStyle(written.getTypeStyles())).isTrue();
    }
}
