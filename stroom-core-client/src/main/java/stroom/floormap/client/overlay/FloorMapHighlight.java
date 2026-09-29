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

package stroom.floormap.client.overlay;

/// Resolves which non-selection highlight an entity carries, when more than one
/// could apply.
///
/// The canvas has two independent reasons to ring something that is not
/// selected, and they can land on the same entity at the same time:
///
/// 1. **Group** — the entity is a member of a group the user has
///    switched on in the Groups panel. Drawn in that group's colour,
///    *solid*.
/// 2. **Area-related** — the entity is inside the focused area, or
///    is an area containing the focused entity
///    ([FloorMapAreaOverlay#isRelated]). Drawn green and
///    *dashed*.
///
/// **Group wins**, because a group highlight is what the user
/// explicitly asked to see, whereas area-relatedness is incidental to whatever
/// they happen to have focused. The dash pattern is carried separately from the
/// colour rather than inferred from it, so the two stay distinguishable even when
/// a user picks a green group colour.
///
/// **Selection is deliberately not modelled here.** The view styles
/// a selected entity differently depending on what it is drawing — blue for an
/// image fact, orange for a glyph, plus its own stroke width and opacity changes
/// for an area — so folding selection into one resolved colour would flatten
/// distinctions that already exist. Callers keep their existing
/// `isSelected ? … : highlight` shape; selection therefore still wins over
/// everything here.
///
/// Resolution is computed per lookup from the two overlays rather than
/// precomputed into a map: the canvas rebuilds this every frame, and both overlays
/// are already indexed.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM.
public final class FloorMapHighlight {

    /// The colour of the area-containment highlight. Lives here rather than in the
    /// canvas view so the one place that decides highlight colours owns it.
    public static final String RELATED_COLOUR = "#00c853";

    /// Nothing highlighted.
    public static final FloorMapHighlight EMPTY =
            new FloorMapHighlight(FloorMapGroupOverlay.EMPTY, FloorMapAreaOverlay.EMPTY);

    private final FloorMapGroupOverlay groups;
    private final FloorMapAreaOverlay areas;

    private FloorMapHighlight(final FloorMapGroupOverlay groups,
                              final FloorMapAreaOverlay areas) {
        this.groups = groups;
        this.areas = areas;
    }

    /// @param groups the group highlight, or `null` for none
    /// @param areas  the area-containment overlay, or `null` for none
    /// @return the resolver; never `null`
    public static FloorMapHighlight of(final FloorMapGroupOverlay groups,
                                       final FloorMapAreaOverlay areas) {
        final FloorMapGroupOverlay g = groups != null
                ? groups
                : FloorMapGroupOverlay.EMPTY;
        final FloorMapAreaOverlay a = areas != null
                ? areas
                : FloorMapAreaOverlay.EMPTY;
        return !g.hasAny() && !a.hasRelated()
                ? EMPTY
                : new FloorMapHighlight(g, a);
    }

    /// The highlight colour for the given entity, or `null` when it carries
    /// no non-selection highlight.
    ///
    /// @param id a fact key or event entity id; may be `null`
    public String colourFor(final String id) {
        final String groupColour = groups.colourFor(id);
        if (groupColour != null) {
            return groupColour;
        }
        return areas.isRelated(id)
                ? RELATED_COLOUR
                : null;
    }

    /// `true` when this entity's highlight should be drawn dashed — i.e. it
    /// came from area containment rather than from a group.
    ///
    /// @param id a fact key or event entity id; may be `null`
    public boolean isDashed(final String id) {
        return groups.colourFor(id) == null && areas.isRelated(id);
    }

    /// `true` when the entity carries any non-selection highlight.
    ///
    /// @param id a fact key or event entity id; may be `null`
    public boolean isHighlighted(final String id) {
        return colourFor(id) != null;
    }

    /// `true` if nothing at all is highlighted.
    public boolean isEmpty() {
        return !groups.hasAny() && !areas.hasRelated();
    }
}
