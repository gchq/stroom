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

package stroom.pipeline.stepping.client;

import java.util.ArrayList;
import java.util.List;

/// Chooses which segments of the pipeline bar to collapse into `…` segments when the bar is too
/// narrow to show them all.
///
/// The first and last segments, the selected one and its neighbours are always shown. The others
/// are collapsed furthest from the selected segment first, so the bar shows the ends of the
/// pipeline and the part around the selection. Each run of collapsed segments is replaced by one
/// `…` segment.
public final class PipelinePathCollapse {

    private PipelinePathCollapse() {
        // Static utility
    }

    /// Chooses the segments to collapse.
    ///
    /// @param widths        The width of each segment, in order.
    /// @param available     The width the bar has.
    /// @param selectedIndex The index of the selected segment, or -1 for none.
    /// @param ellipsisWidth The width of a `…` segment.
    /// @return Whether each segment is collapsed, in order. None are if they all fit, and only
    ///         those that may be are, even if the rest still don't fit.
    public static boolean[] collapse(final int[] widths,
                                     final int available,
                                     final int selectedIndex,
                                     final int ellipsisWidth) {
        final boolean[] collapsed = new boolean[widths.length];
        final List<Integer> candidates = getCandidates(widths.length, selectedIndex);
        int candidate = 0;
        while (getWidth(widths, collapsed, ellipsisWidth) > available && candidate < candidates.size()) {
            collapsed[candidates.get(candidate)] = true;
            candidate++;
        }
        return collapsed;
    }

    /// Gets the width the bar needs to show its segments with some collapsed.
    ///
    /// @param widths        The width of each segment, in order.
    /// @param collapsed     Whether each segment is collapsed.
    /// @param ellipsisWidth The width of a `…` segment, which replaces each run of collapsed
    ///                      segments.
    /// @return The width needed.
    public static int getWidth(final int[] widths, final boolean[] collapsed, final int ellipsisWidth) {
        int width = 0;
        for (int i = 0; i < widths.length; i++) {
            if (!collapsed[i]) {
                width += widths[i];
            } else if (i == 0 || !collapsed[i - 1]) {
                width += ellipsisWidth;
            }
        }
        return width;
    }

    // The segments that may be collapsed, furthest from the selected one first, those before it
    // first where two are as far
    private static List<Integer> getCandidates(final int count, final int selectedIndex) {
        final int centre = selectedIndex >= 0 && selectedIndex < count
                ? selectedIndex
                : 0;
        final List<Integer> candidates = new ArrayList<>();
        for (int distance = count; distance > 1; distance--) {
            addIfCollapsible(candidates, centre - distance, count);
            addIfCollapsible(candidates, centre + distance, count);
        }
        return candidates;
    }

    // Neither end may be collapsed
    private static void addIfCollapsible(final List<Integer> candidates, final int index, final int count) {
        if (index > 0 && index < count - 1) {
            candidates.add(index);
        }
    }
}
