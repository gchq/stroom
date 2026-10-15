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

package stroom.floormap.client.editor;

import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.floormap.shared.TypeStyle;
import stroom.floormap.shared.ValueFormat;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Objects;

/// A tab's pending document-level edits — the Editor's area- and status-support
/// upgrades, Layers type-styles list and Set Scale calibration, and the Map tab's groups —
/// with the invariants that keep them consistent across read/write.
///
/// Neither tab normally writes the [FloorMapDoc] itself (temporal store
/// edits flush separately), so these edits are staged here and merged into the
/// document on save. The tricky rules — when a staged edit has been persisted and
/// can be dropped ([#reconcileAfterRead]), and how the type-styles edits
/// combine on write so a Layers edit can't drop the area style
/// ([#applyToWrite]) — are extracted from the GWT presenters so they are
/// unit-testable on the JVM.
///
/// Each tab owns its own instance and stages only the fields it edits, so two
/// live sessions never contend: the Editor's touches `valueSchema` and
/// `typeStyles`, the Map's touches `groups`, and both
/// `applyToWrite` calls chain safely through `FloorMapDoc.copy()`.
///
/// Holds only the pending state; the loaded entity's own schema/type-styles
/// are passed in where needed (this class doesn't own the document).
public final class FloorMapDocSession {

    /// The value schema with every staged schema upgrade applied, or `null` if
    /// none is staged. When set, it is the effective value schema for the session
    /// (older docs predate the area and status roles). Which upgrades it carries is
    /// recorded by [#areaUpgradeStaged] and [#statusUpgradeStaged], so each
    /// can be re-applied on write and dropped independently once persisted.
    private List<FloorMapFieldMapping> pendingSchema;

    /// Whether the area-support upgrade is staged. Its companion
    /// [#pendingAreaTypeStyles] seeds the "area" z-order style.
    private boolean areaUpgradeStaged;
    private List<TypeStyle> pendingAreaTypeStyles;

    /// Whether the status-support upgrade (a default Status mapping, so facts can
    /// be deleted from a point in time) is staged.
    private boolean statusUpgradeStaged;

    /// Type styles edited via the Layers panel (reorder / appearance / discovered
    /// types) not yet saved. When non-null this is the authoritative ordered list.
    private List<TypeStyle> pendingTypeStyles;

    /// Groups edited via the Map tab's Groups panel and not yet saved. When
    /// non-null this is the authoritative list for the session.
    ///
    /// Unlike the type styles, groups have no second contributor to merge with —
    /// no other tab writes them — so staging is a straight replace.
    private List<FloorMapGroup> pendingGroups;

    /// A Set Scale calibration made on the Editor tab and not yet saved, with
    /// [#pendingUnitsStaged] recording that it was staged at all.
    ///
    /// The flag is needed because `null` is a meaningful staged value —
    /// "this map has no scale" — and must be distinguishable from "nothing
    /// staged".
    private FloorMapMeasurementUnits pendingMeasurementUnits;
    private boolean pendingUnitsStaged;

    /// `true` if any document-level edit is staged.
    public boolean hasPendingDocEdits() {
        return pendingSchema != null
               || pendingTypeStyles != null
               || pendingGroups != null
               || pendingUnitsStaged;
    }

    /// The value schema in effect this session: the pending upgrade, else the entity's.
    public List<FloorMapFieldMapping> valueSchema(final List<FloorMapFieldMapping> entitySchema) {
        return pendingSchema != null ? pendingSchema : entitySchema;
    }

    /// The type styles in effect this session (Layers edit, else area upgrade, else entity's).
    public List<TypeStyle> typeStyles(final List<TypeStyle> entityTypeStyles) {
        if (pendingTypeStyles != null) {
            return pendingTypeStyles;
        }
        return pendingAreaTypeStyles != null ? pendingAreaTypeStyles : entityTypeStyles;
    }

    /// The groups in effect this session: the pending edit, else the entity's.
    public List<FloorMapGroup> groups(final List<FloorMapGroup> entityGroups) {
        return pendingGroups != null ? pendingGroups : entityGroups;
    }

    /// Stages a Layers-panel type-styles edit.
    public void stageTypeStyles(final List<TypeStyle> styles) {
        this.pendingTypeStyles = styles;
    }

    /// Stages a Groups-panel edit (create, rename, recolour, membership, delete).
    public void stageGroups(final List<FloorMapGroup> groups) {
        this.pendingGroups = groups;
    }

    /// The measurement units in effect this session: the calibration, else the entity's.
    public FloorMapMeasurementUnits measurementUnits(final FloorMapMeasurementUnits entityUnits) {
        return pendingUnitsStaged
                ? pendingMeasurementUnits
                : entityUnits;
    }

    /// Stages a Set Scale calibration.
    ///
    /// @param units the calibrated units; `null` stages "this map has no
    ///         scale", which is not the same as staging nothing
    public void stageMeasurementUnits(final FloorMapMeasurementUnits units) {
        this.pendingMeasurementUnits = units;
        this.pendingUnitsStaged = true;
    }

    /// Stages the area-support upgrade: the default Geometry/Fill/Opacity schema
    /// mappings and an "area" type style, derived from the current effective
    /// lists.
    ///
    /// @param baseSchema     the current effective value schema
    /// @param format         the document's value format (for default paths)
    /// @param baseTypeStyles the current effective type styles
    public void stageAreaUpgrade(final List<FloorMapFieldMapping> baseSchema,
                                 final ValueFormat format,
                                 final List<TypeStyle> baseTypeStyles) {
        pendingSchema = FloorMapFieldMapping.withAreaMappings(baseSchema, format);
        areaUpgradeStaged = true;
        pendingAreaTypeStyles = TypeStyle.withAreaStyle(baseTypeStyles);
    }

    /// Stages the status-support upgrade: a default Status schema mapping,
    /// derived from the current effective schema, so facts can be deleted from a
    /// point in time on a document created before that was possible.
    ///
    /// Composes with [#stageAreaUpgrade] in either order, since each derives
    /// from the effective schema, which already carries the other.
    ///
    /// @param baseSchema the current effective value schema
    /// @param format     the document's value format (for the default path)
    public void stageStatusUpgrade(final List<FloorMapFieldMapping> baseSchema,
                                   final ValueFormat format) {
        pendingSchema = FloorMapFieldMapping.withStatusMapping(baseSchema, format);
        statusUpgradeStaged = true;
    }

    /// Returns the document as this session sees it: the loaded entity with any
    /// pending edits applied. Hand this to any child that resolves schema roles
    /// itself, or fill/opacity/geometry would resolve against the pre-upgrade
    /// schema until save.
    public FloorMapDoc sessionEntity(final FloorMapDoc entity) {
        if (!hasPendingDocEdits()) {
            return entity;
        }
        return entity.copy()
                .valueSchema(valueSchema(entity.getValueSchema()))
                .typeStyles(typeStyles(entity.getTypeStyles()))
                .groups(groups(entity.getGroups()))
                .measurementUnits(measurementUnits(entity.getMeasurementUnits()))
                .build();
    }

    /// Merges the staged edits into `document` for save. The area and status
    /// upgrades write the upgraded schema; the type styles come from the Layers edit if
    /// present (with the "area" style folded in when an area upgrade is also
    /// pending, so a Layers edit around the upgrade can't drop it), else from the
    /// area upgrade alone. Staged groups are written as-is.
    public FloorMapDoc applyToWrite(final FloorMapDoc document) {
        if (!hasPendingDocEdits()) {
            return document;
        }
        final FloorMapDoc.Builder builder = document.copy();
        if (pendingSchema != null) {
            List<FloorMapFieldMapping> schema = document.getValueSchema();
            if (areaUpgradeStaged) {
                schema = FloorMapFieldMapping.withAreaMappings(schema, document.getValueFormat());
            }
            if (statusUpgradeStaged) {
                schema = FloorMapFieldMapping.withStatusMapping(schema, document.getValueFormat());
            }
            builder.valueSchema(schema);
        }
        if (pendingTypeStyles != null) {
            builder.typeStyles(areaUpgradeStaged
                    ? TypeStyle.withAreaStyle(pendingTypeStyles)
                    : pendingTypeStyles);
        } else if (areaUpgradeStaged) {
            builder.typeStyles(TypeStyle.withAreaStyle(document.getTypeStyles()));
        }
        if (pendingGroups != null) {
            builder.groups(pendingGroups);
        }
        if (pendingUnitsStaged) {
            builder.measurementUnits(pendingMeasurementUnits);
        }
        return builder.build();
    }

    /// Drops staged edits that the just-read document already carries (post-save
    /// re-read). The area upgrade is dropped only once BOTH the schema roles and
    /// the "area" style are present; the status upgrade once the Status role is;
    /// the Layers edit once the doc's styles equal it (accepting the area-folded
    /// form written by [#applyToWrite]); the groups edit once the doc's groups
    /// equal it.
    public void reconcileAfterRead(final FloorMapDoc document) {
        if (areaUpgradeStaged
                && hasAreaSupport(document.getValueSchema())
                && hasAreaStyle(document.getTypeStyles())) {
            areaUpgradeStaged = false;
            pendingAreaTypeStyles = null;
        }
        if (statusUpgradeStaged && hasStatusSupport(document.getValueSchema())) {
            statusUpgradeStaged = false;
        }
        if (!areaUpgradeStaged && !statusUpgradeStaged) {
            pendingSchema = null;
        }
        if (pendingTypeStyles != null
                && (pendingTypeStyles.equals(document.getTypeStyles())
                    || TypeStyle.withAreaStyle(pendingTypeStyles)
                            .equals(document.getTypeStyles()))) {
            pendingTypeStyles = null;
        }
        if (pendingGroups != null && sameGroups(pendingGroups, document.getGroups())) {
            pendingGroups = null;
        }
        if (pendingUnitsStaged
                && Objects.equals(pendingMeasurementUnits, document.getMeasurementUnits())) {
            pendingMeasurementUnits = null;
            pendingUnitsStaged = false;
        }
    }

    /// Whether two group lists are the same for reconciliation purposes, treating
    /// `null` and empty as equivalent.
    ///
    /// Deleting the last group stages an empty list against a document that may
    /// carry either form. Being lenient here matters more than being strict: a
    /// staged edit that is never dropped leaves the document permanently dirty.
    private static boolean sameGroups(final List<FloorMapGroup> a,
                                     final List<FloorMapGroup> b) {
        if (NullSafe.isEmptyCollection(a) && NullSafe.isEmptyCollection(b)) {
            return true;
        }
        return a != null && a.equals(b);
    }

    /// `true` when the schema maps every role areas need.
    public static boolean hasAreaSupport(final List<FloorMapFieldMapping> schema) {
        return FloorMapFieldMapping.findPath(schema, Role.GEOMETRY) != null
                && FloorMapFieldMapping.findPath(schema, Role.FILL) != null
                && FloorMapFieldMapping.findPath(schema, Role.OPACITY) != null;
    }

    /// `true` when the schema maps the Status role, which deleting a fact from a
    /// point in time needs.
    public static boolean hasStatusSupport(final List<FloorMapFieldMapping> schema) {
        return FloorMapFieldMapping.findPath(schema, Role.STATUS) != null;
    }

    /// `true` when the type styles contain an `"area"` entry.
    public static boolean hasAreaStyle(final List<TypeStyle> typeStyles) {
        if (typeStyles != null) {
            for (final TypeStyle style : typeStyles) {
                if (style != null && FloorMapJsonKeys.AREA.equals(style.getType())) {
                    return true;
                }
            }
        }
        return false;
    }
}
