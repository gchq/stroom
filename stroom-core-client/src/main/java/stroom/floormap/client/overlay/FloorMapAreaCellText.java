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

import stroom.util.shared.NullSafe;

import java.util.List;

/**
 * The wording used in the area columns of the tracking and groups panels.
 *
 * <p>The tracking panel's <strong>Area</strong> column has a single meaning on
 * every row — <em>which area is this inside?</em> — so there is one form of words,
 * and it lives here where it can be unit-tested without a GWT presenter. The
 * Groups panel's <strong>Areas</strong> column answers the same question for a
 * whole group, and so shares the wording with a member count appended.</p>
 *
 * <p>{@link #joinNames} is the plain name-list join and is used beyond the area
 * columns — the cluster dialog's <strong>Group</strong> column lists group names
 * with it — so that a list of names reads the same wherever one is shown.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapAreaCellText {

    /** Separator between area names. */
    private static final String NAME_SEPARATOR = ", ";

    private FloorMapAreaCellText() {
        // Utility class.
    }

    /**
     * Every containing area named, in the order given (innermost first, so the
     * most specific area reads first).
     *
     * <p>Applies to any row — an entity or a nested area — because the column
     * treats them identically.</p>
     *
     * <p>The grid cell is a single {@code nowrap} line that ellipses when the
     * column is too narrow, so the full list is safe to emit here — the caller
     * repeats it in the cell's tooltip for when it is clipped.</p>
     *
     * @param names the resolved area display names, in display order; may be
     *              {@code null} or empty
     * @return the comma-separated names, or {@code ""} when there are none
     */
    public static String joinNames(final List<String> names) {
        if (NullSafe.isEmptyCollection(names)) {
            return "";
        }
        final StringBuilder joined = new StringBuilder();
        for (final String name : names) {
            if (NullSafe.isEmptyString(name)) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(NAME_SEPARATOR);
            }
            joined.append(name);
        }
        return joined.toString();
    }

    /**
     * Every area named with how many of a group's members are in it —
     * {@code "Loading Bay (2), Office (1)"}.
     *
     * <p>Every area is named rather than summarised, for the same reason
     * {@link #joinNames} does: a {@code "+2"} would hide exactly the names the
     * user is looking for. The two lists are parallel; a name with no matching
     * count renders bare, and a blank name is skipped along with its count.</p>
     *
     * @param names  the resolved area display names, in display order; may be
     *               {@code null} or empty
     * @param counts the member count for each name, positionally matched; may be
     *               {@code null}
     * @return the comma-separated names with counts, or {@code ""} when there are
     *         none
     */
    public static String joinNamesWithCounts(final List<String> names,
                                             final List<Integer> counts) {
        if (NullSafe.isEmptyCollection(names)) {
            return "";
        }
        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            if (NullSafe.isEmptyString(name)) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(NAME_SEPARATOR);
            }
            joined.append(name);
            final Integer count = counts != null && i < counts.size()
                    ? counts.get(i)
                    : null;
            if (count != null) {
                joined.append(" (").append(count).append(')');
            }
        }
        return joined.toString();
    }
}
