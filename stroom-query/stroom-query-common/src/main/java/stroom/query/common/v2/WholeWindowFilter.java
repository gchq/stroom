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

package stroom.query.common.v2;

import stroom.query.api.HoppingWindow;
import stroom.query.api.TimeFilter;
import stroom.query.language.functions.TimeWindows;

/// Tells the time windows that a search has all the events of from those that the time range of the
/// search cuts short.
///
/// A query with a `window` clause that is run over a range of time gives a row for every window that
/// has an event in the range. The windows at the two ends of the range may only be partly inside it,
/// so their rows are worked out from part of their events. A rule that is run on a schedule judges
/// only the rows of whole windows, so that a window is not judged on part of its events and is not
/// judged again, with a different answer, on the next run.
public final class WholeWindowFilter {

    private final TimeWindows timeWindows;
    private final int[] windowShifts;
    private final long from;
    private final long to;

    /// Creates a filter for the windows of a query.
    ///
    /// @param window       The windows of the query's window clause.
    /// @param windowShifts How far, in windows, the rows of the query are copied to the windows that
    ///                     read them: later for `lag` and earlier, a negative number, for `lead`.
    /// @param timeRange    The range of time that was searched, from its start up to its end.
    public WholeWindowFilter(final HoppingWindow window,
                             final int[] windowShifts,
                             final TimeFilter timeRange) {
        this.timeWindows = TimeWindows.create(window);
        this.windowShifts = windowShifts.clone();
        this.from = timeRange.getFrom();
        this.to = timeRange.getTo();
    }

    /// Tests whether the row of a window was worked out from all of the events it needs.
    ///
    /// The window itself must be wholly inside the time range. So must a later window that the row
    /// reads with `lead`. An earlier window that the row reads with `lag` must be wholly inside the
    /// range or wholly before it, where it has no events and `lag` gives its default.
    ///
    /// @param windowKey The key of the row's window.
    /// @return True if no window that the row is worked out from is cut short by the time range.
    public boolean isWhole(final long windowKey) {
        if (timeWindows.getWindowStart(windowKey) < from || timeWindows.getWindowEnd(windowKey) > to) {
            return false;
        }
        for (final int windowShift : windowShifts) {
            // A row is copied forward to the window that reads it, so the window read is as far back.
            final long windowRead = timeWindows.shift(windowKey, -windowShift);
            final long start = timeWindows.getWindowStart(windowRead);
            final long end = timeWindows.getWindowEnd(windowRead);
            if (end > to || (start < from && end > from)) {
                return false;
            }
        }
        return true;
    }
}
