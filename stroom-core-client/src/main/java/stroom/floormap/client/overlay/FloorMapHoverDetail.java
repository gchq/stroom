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

package stroom.floormap.client.overlay;

import stroom.floormap.client.cluster.FloorMapClusterLabel;
import stroom.floormap.shared.FloorMapMeasurementUnits;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// The wording of the hover panel that describes *one* entity — the
/// single-object counterpart to [FloorMapClusterLabel], which words the
/// same panel for a cluster.
///
/// A glyph on the map says almost nothing about what it is: the shape carries
/// its type at best, and the caption (when there is room for one) carries its
/// name. This is what the pointer is for — name, type, which area it is standing
/// in, and where that is in real units, without selecting anything or leaving
/// the map.
///
/// ## What is said, and what is left out
///
/// - **Every containing area is named**, innermost first, in
///   the same words the Tracking panel and the cluster dialog use — one
///   entity must not read differently in two places.
/// - **Areas are only mentioned on a map that has areas.**
///   "Not inside an area" is worth saying where areas exist and this entity
///   is in none of them; on a map with no areas at all it is noise on every
///   hover, so the line is dropped (`null` rather than an empty
///   list).
/// - **The id is shown only when it is not already the caption.**
///   An unnamed entity is captioned by its id, and repeating it as a detail
///   line would say nothing twice.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM. Note that
/// `String.format` is unavailable under GWT, hence the concatenation.
public final class FloorMapHoverDetail {

    /// Said when the map has areas but the entity is inside none of them.
    private static final String NO_AREA = "Not inside an area";

    /// Bullet prefix for one area in a multi-area list.
    private static final String BULLET = "• ";

    private FloorMapHoverDetail() {
        // Utility class.
    }

    /// The panel's heading: the entity's display name, falling back to its id.
    ///
    /// An entity with no name is not nameless to the user — its id is what
    /// every grid shows for it — so the caption is never empty while there is
    /// anything at all to identify it by.
    ///
    /// @param id   the entity id; may be `null`
    /// @param name the resolved display name; `null` or blank falls back
    ///         to the id
    /// @return the caption, or `null` when there is neither
    public static String caption(final String id, final String name) {
        if (NullSafe.isNonBlankString(name)) {
            return name.trim();
        }
        return NullSafe.isNonBlankString(id)
                ? id.trim()
                : null;
    }

    /// The detail lines shown under the caption, in reading order: what kind of
    /// thing it is, where it is standing, where that is, and — only when it adds
    /// anything — what it is called in the store.
    ///
    /// Every argument is optional: a line whose input is missing is simply not
    /// emitted, so a bare fact with nothing but a key still produces a usable
    /// panel rather than a column of blanks.
    ///
    /// @param type         the entity type, e.g. `"person"`; `null`
    ///         or blank omits the line
    /// @param areaNames    the containing area names, innermost first; an empty
    ///         list reads "not inside an area", and `null`
    ///         omits the subject entirely (used when the map has no
    ///         areas to be inside)
    /// @param positionText the pre-formatted position, e.g.
    ///         `"X 4.5 m, Y 2.1 m"` from
    ///         [FloorMapMeasurementUnits#formatPosition];
    ///         `null` or blank omits the line
    /// @param id           the entity id; omitted when blank or when it is
    ///         already the caption
    /// @param caption      the caption from [#caption], so the id line can
    ///         tell whether it would be a repeat
    /// @return the lines to render; never `null`, possibly empty
    public static List<String> lines(final String type,
                                     final List<String> areaNames,
                                     final String positionText,
                                     final String id,
                                     final String caption) {
        final List<String> lines = new ArrayList<>();

        if (NullSafe.isNonBlankString(type)) {
            lines.add("Type: " + type.trim());
        }
        lines.addAll(areaLines(areaNames));
        if (NullSafe.isNonBlankString(positionText)) {
            lines.add("Position: " + positionText.trim());
        }
        // The caption is already the id for an unnamed entity; saying it twice
        // would push the useful lines further from the pointer for nothing.
        if (NullSafe.isNonBlankString(id) && !id.trim().equals(caption)) {
            lines.add("Id: " + id.trim());
        }
        return Collections.unmodifiableList(lines);
    }

    /// The area part of the panel: nothing on a map without areas, one line when
    /// the entity is in none or exactly one, and a counted list — one area per
    /// line, innermost first — when it is in several.
    ///
    /// Every area is named rather than summarised as "+2", for the same reason
    /// [FloorMapAreaCellText#joinNames] names them all: the names are the
    /// answer the reader came for.
    ///
    /// @param areaNames the containing area names, innermost first; `null`
    ///         means the map has no areas
    /// @return the lines; never `null`
    public static List<String> areaLines(final List<String> areaNames) {
        if (areaNames == null) {
            return Collections.emptyList();
        }
        final List<String> named = new ArrayList<>(areaNames.size());
        for (final String name : areaNames) {
            if (NullSafe.isNonBlankString(name)) {
                named.add(name.trim());
            }
        }
        if (named.isEmpty()) {
            return Collections.singletonList(NO_AREA);
        }
        if (named.size() == 1) {
            //noinspection SequencedCollectionMethodCanBeUsed
            return Collections.singletonList("Inside " + named.get(0));
        }
        final List<String> lines = new ArrayList<>(named.size() + 1);
        // The same heading the Tracking panel's Area tooltip uses, so the two
        // read identically for one entity.
        lines.add("Inside " + named.size() + " areas (innermost first):");
        for (final String name : named) {
            lines.add(BULLET + name);
        }
        return Collections.unmodifiableList(lines);
    }
}
