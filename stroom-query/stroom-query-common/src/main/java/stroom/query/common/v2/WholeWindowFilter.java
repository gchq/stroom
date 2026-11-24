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

/// Chooses the time windows that a rule run on a schedule judges, and how far back it must search for
/// them.
///
/// A query with a `window` clause gives a row for every window that has an event. A rule that is run
/// on a schedule judges a window once, in the run whose time range holds the time the window is ready,
/// which is when the window has ended and so has every later window that it reads with `lead`. Runs
/// whose time ranges follow on from each other then judge each window once, whether or not the ranges
/// line up with the windows.
///
/// A window that is judged must be worked out from all of its events, and so must every window that it
/// reads with `lag`, so the rule searches back from the start of its time range to the start of the
/// earliest of these windows.
public final class WholeWindowFilter {

    /// The start of a time range that has no start.
    public static final long NO_START = Long.MIN_VALUE;

    private final TimeWindows timeWindows;
    private final int furthestLag;
    private final int furthestLead;
    private final long from;
    private final long to;
    private final long searchFrom;

    /// Creates a filter for the windows of a query.
    ///
    /// @param window       The windows of the query's window clause.
    /// @param windowShifts How far, in windows, the rows of the query are copied to the windows that
    ///                     read them: later for `lag` and earlier, a negative number, for `lead`.
    /// @param timeRange    The time range of the run, from its start up to its end. A start of
    ///                     [#NO_START] means the range has no start.
    public WholeWindowFilter(final HoppingWindow window,
                             final int[] windowShifts,
                             final TimeFilter timeRange) {
        this.timeWindows = TimeWindows.create(window);
        int lag = 0;
        int lead = 0;
        for (final int windowShift : windowShifts) {
            lag = Math.max(lag, windowShift);
            lead = Math.max(lead, -windowShift);
        }
        this.furthestLag = lag;
        this.furthestLead = lead;
        this.from = timeRange.getFrom();
        this.to = timeRange.getTo();
        this.searchFrom = findSearchFrom();
    }

    /// Gets the time that the rule must search from to have all the events of the windows it judges
    /// and of the windows they read with `lag`.
    ///
    /// @return The time, in epoch milliseconds, which is never after the start of the time range.
    public long getSearchFrom() {
        return searchFrom;
    }

    /// Tells whether the rule must search from before the start of its time range.
    ///
    /// @return True if [#getSearchFrom()] is before the start of the time range.
    public boolean searchesBeforeRange() {
        return searchFrom < from;
    }

    /// Tests whether a run judges the row of a window.
    ///
    /// @param windowKey The key of the row's window.
    /// @return True if the window is ready in the time range of the run and every window that the row
    ///         is worked out from was searched in full.
    public boolean isJudged(final long windowKey) {
        final long ready = getReadyTime(windowKey);
        return ready > from
               && ready <= to
               && timeWindows.getWindowStart(timeWindows.shift(windowKey, -furthestLag)) >= searchFrom;
    }

    // The time that a window has ended, and so has every window that it reads with lead.
    private long getReadyTime(final long windowKey) {
        return timeWindows.getWindowEnd(timeWindows.shift(windowKey, furthestLead));
    }

    // The start of the earliest window that is read by a window that is ready after the start of the
    // time range.
    private long findSearchFrom() {
        if (from == NO_START) {
            return from;
        }
        // The first window to end after the start of the range is the first that can be ready in it,
        // and the first window judged is the one that reads it with the furthest lead.
        final long firstReady = timeWindows.getNextWindow(timeWindows.getLatestEndedWindow(from));
        final long firstJudged = timeWindows.shift(firstReady, -furthestLead);
        final long firstRead = timeWindows.shift(firstJudged, -furthestLag);
        return Math.min(from, timeWindows.getWindowStart(firstRead));
    }
}
