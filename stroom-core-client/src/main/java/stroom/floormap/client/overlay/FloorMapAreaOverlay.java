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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// The area-containment decorations the canvas draws for one frame — derived
/// from a [FloorMapAreaMembership] snapshot plus whatever the user is
/// currently focused on.
///
/// Two decorations, both computed here rather than in the view:
///
/// 1. **Related highlight** — the reciprocal relation for the
///    focused entity. Focus a person and the area(s) containing them are
///    flagged; focus an area and its occupants are flagged. One set serves
///    both directions because fact keys and event ids share a namespace (the
///    tracking roster keys on it).
/// 2. **Occupant counts** — per area, for the count badge. Areas
///    with no occupants are absent, so the view draws no badge for them.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM.
public final class FloorMapAreaOverlay {

    /// No highlight, no badges.
    public static final FloorMapAreaOverlay EMPTY =
            new FloorMapAreaOverlay(Collections.emptySet(), Collections.emptyMap());

    private final Set<String> relatedIds;
    private final Map<String, Integer> occupantCounts;

    private FloorMapAreaOverlay(final Set<String> relatedIds,
                                final Map<String, Integer> occupantCounts) {
        this.relatedIds = relatedIds;
        this.occupantCounts = occupantCounts;
    }

    /// Builds the overlay for a membership snapshot and the focused entity.
    ///
    /// @param membership the containment snapshot; may be `null`
    /// @param focusId    the tracked/selected entity id — an area or an entity —
    ///         or `null` when nothing is focused (badges are
    ///         still produced)
    /// @return the overlay; never `null`
    public static FloorMapAreaOverlay of(final FloorMapAreaMembership membership,
                                         final String focusId) {
        if (membership == null) {
            return EMPTY;
        }

        final Set<String> related = new LinkedHashSet<>();
        if (focusId != null) {
            // Focused an area → flag what is inside it.
            related.addAll(membership.getOccupants(focusId));
            // Focused an entity → flag the areas containing it.
            related.addAll(membership.getAreaKeys(focusId));
            // Never flag the focused thing itself; selection already styles it.
            related.remove(focusId);
        }

        final Map<String, Integer> counts = membership.getOccupantCounts();

        return related.isEmpty() && counts.isEmpty()
                ? EMPTY
                : new FloorMapAreaOverlay(
                        Collections.unmodifiableSet(related),
                        Collections.unmodifiableMap(counts));
    }

    /// `true` if the given key is related by containment to the focused
    /// entity, and so should carry the "related" highlight.
    ///
    /// @param key a fact key or event entity id; may be `null`
    public boolean isRelated(final String key) {
        return key != null && relatedIds.contains(key);
    }

    /// The occupant count to badge on the given area, or `null` when the
    /// area has no occupants (or is not an area) and so needs no badge.
    ///
    /// @param areaKey the area's fact key; may be `null`
    public Integer getOccupantCount(final String areaKey) {
        return areaKey != null
                ? occupantCounts.get(areaKey)
                : null;
    }

    /// `true` if anything at all is highlighted.
    public boolean hasRelated() {
        return !relatedIds.isEmpty();
    }
}
