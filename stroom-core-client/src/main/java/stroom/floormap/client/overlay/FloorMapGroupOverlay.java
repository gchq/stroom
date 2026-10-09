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

import stroom.floormap.shared.FloorMapGroup;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// The group decoration the canvas draws for one frame: which entity ids belong to
/// a group the user has chosen to highlight, and in what colour.
///
/// Sibling to [FloorMapAreaOverlay] — same job, different relation. A
/// single id → colour map serves every kind of member (event entity, object fact,
/// area) because they all share one id namespace.
///
/// Highlighting is keyed on **group ids**, not names, so renaming a
/// group mid-session cannot silently drop its highlight. It is transient view
/// state and is never persisted with the document — the same treatment layer
/// visibility gets — and it starts *off* for every group, including one just
/// created: nothing lights up unasked.
///
/// Holds no GWT or DOM types so it can be unit-tested on the JVM.
public final class FloorMapGroupOverlay {

    /// Nothing highlighted.
    public static final FloorMapGroupOverlay EMPTY =
            new FloorMapGroupOverlay(Collections.emptyMap());

    private final Map<String, String> colourByMemberId;

    private FloorMapGroupOverlay(final Map<String, String> colourByMemberId) {
        this.colourByMemberId = colourByMemberId;
    }

    /// Builds the overlay for the groups the user has switched on.
    ///
    /// When an entity belongs to two shown groups, **the first of those
    /// groups in list order wins** — a deterministic rule the user can
    /// predict from the panel's row order, rather than whichever group a map
    /// happened to iterate first.
    ///
    /// @param groups        the document's groups, in display order; may be `null`
    /// @param shownGroupIds the ids of the groups currently highlighted; may be
    ///         `null` or empty for no highlight
    /// @return the overlay; never `null`
    public static FloorMapGroupOverlay of(final Collection<FloorMapGroup> groups,
                                          final Collection<String> shownGroupIds) {
        if (NullSafe.isEmptyCollection(groups)
                || NullSafe.isEmptyCollection(shownGroupIds)) {
            return EMPTY;
        }

        final Map<String, String> colours = new LinkedHashMap<>();
        for (final FloorMapGroup group : groups) {
            if (group == null || !shownGroupIds.contains(group.getId())) {
                continue;
            }
            final String colour = group.findColourOrDefault();
            for (final String memberId : group.getMemberIds()) {
                if (NullSafe.isNonEmptyString(memberId)) {
                    // First shown group in list order wins.
                    colours.putIfAbsent(memberId, colour);
                }
            }
        }

        return colours.isEmpty()
                ? EMPTY
                : new FloorMapGroupOverlay(Collections.unmodifiableMap(colours));
    }

    /// The highlight colour for the given entity, or `null` when it is not a
    /// member of any shown group.
    ///
    /// @param id a fact key or event entity id; may be `null`
    public String colourFor(final String id) {
        return id != null
                ? colourByMemberId.get(id)
                : null;
    }

    /// `true` if anything at all is highlighted.
    public boolean hasAny() {
        return !colourByMemberId.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final FloorMapGroupOverlay that)) {
            return false;
        }
        return colourByMemberId.equals(that.colourByMemberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(colourByMemberId);
    }
}
