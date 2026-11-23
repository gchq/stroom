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

package stroom.ai.impl.vis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// How a timeline packs the bars of one lane into rows so that none hides another, as the chat's Gantt chart does
/// (`AiGanttChart`) but by time rather than by pixels, so the rows don't change as the chart zooms. Both adapters
/// (`timelinePack` in `echarts-generic.js` and `d3-generic.js`) pack the same way; [ChartCheck] uses this to say how
/// many rows the busiest lane needs.
///
/// Bars are taken in order of start (then end), each into the first row it fits in: one whose last bar ends before
/// it starts, or as it starts where neither is a marker (a bar ending as it starts). A marker is never put where a
/// bar ends or another marker is, as it would be drawn over it.
public final class TimelineRows {

    private TimelineRows() {
        // Static only.
    }

    /// Packs a lane's bars into rows.
    ///
    /// @param bars The lane's bars, in any order; each ends at or after it starts.
    /// @return The row (from 0) of each bar, in the order given.
    public static List<Integer> pack(final List<Bar> bars) {
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bars.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingLong(i -> bars.get(i).start())
                .thenComparingLong(i -> bars.get(i).end()));
        final List<Integer> rows = new ArrayList<>(bars.size());
        for (int i = 0; i < bars.size(); i++) {
            rows.add(0);
        }
        // The last bar placed in each row.
        final List<Bar> lastInRow = new ArrayList<>();
        for (final int index : order) {
            final Bar bar = bars.get(index);
            int row = 0;
            while (row < lastInRow.size() && !fitsAfter(lastInRow.get(row), bar)) {
                row++;
            }
            if (row == lastInRow.size()) {
                lastInRow.add(bar);
            } else {
                lastInRow.set(row, bar);
            }
            rows.set(index, row);
        }
        return rows;
    }

    /// @param bars A lane's bars.
    /// @return How many rows they are packed into: 0 for none.
    public static int rowCount(final List<Bar> bars) {
        return pack(bars).stream()
                .mapToInt(row -> row + 1)
                .max()
                .orElse(0);
    }

    // Whether a bar can follow the last one in a row without either hiding the other.
    private static boolean fitsAfter(final Bar last, final Bar bar) {
        if (last.end() < bar.start()) {
            return true;
        }
        return last.end() == bar.start() && !last.isMarker() && !bar.isMarker();
    }

    // ---------------------------------------------------------------------------------------------------------

    /// A bar of a timeline, by its times.
    ///
    /// @param start When it starts, in milliseconds since 1970.
    /// @param end   When it ends, at or after its start.
    public record Bar(long start, long end) {

        /// @return True if it ends as it starts, so it is drawn as a marker.
        public boolean isMarker() {
            return end == start;
        }
    }
}
