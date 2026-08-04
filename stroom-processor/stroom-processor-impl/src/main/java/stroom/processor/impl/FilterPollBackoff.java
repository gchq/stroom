/*
 * Copyright 2025 Crown Copyright
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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;
import stroom.util.time.StroomDuration;

/**
 * Decides how often task creation should poll a filter that is not producing any tasks.
 * <p>
 * Polling a filter costs a query against meta (or a full search extraction for filters with a
 * search based data source), so filters that repeatedly find nothing are polled progressively
 * less often. The wait starts at {@link ProcessorConfig#getSkipNonProducingFiltersDuration()} and
 * grows by that same amount after each successive non producing poll, up to a maximum. The
 * increase is deliberately linear rather than exponential; the maximum is what determines the
 * steady state polling cost, so a gentler ramp costs very little but keeps recently active
 * filters responsive.
 * </p>
 * <p>
 * The maximum also bounds how long a filter that suddenly receives data waits for its first task,
 * so latency sensitive filters can lower it with {@link ProcessorFilter#getMaxTaskCreationDelay()}.
 * </p>
 * <p>
 * The state is held on the tracker as {@link ProcessorFilterTracker#getNextPollMs()} plus the
 * existing last poll time, which is enough to derive the current interval without needing to
 * count consecutive non producing polls.
 * </p>
 */
public final class FilterPollBackoff {

    private FilterPollBackoff() {
        // Utility class.
    }

    /**
     * @return True if task creation should poll this filter now.
     */
    public static boolean isPollDue(final ProcessorFilter filter,
                                    final ProcessorFilterTracker tracker,
                                    final ProcessorConfig processorConfig,
                                    final long nowMs) {
        if (tracker == null) {
            // We know nothing about previous polls, so poll it.
            return true;
        }

        final Integer lastPollTaskCount = tracker.getLastPollTaskCount();
        final Long lastPollMs = tracker.getLastPollMs();
        final StroomDuration skipNonProducingFiltersDuration =
                processorConfig.getSkipNonProducingFiltersDuration();

        if (lastPollTaskCount == null
            || lastPollTaskCount > 0
            || lastPollMs == null
            || skipNonProducingFiltersDuration == null) {
            // We have either never polled this filter, the last poll produced tasks so there may
            // well be more to create, or backing off has been turned off altogether.
            return true;
        }

        if (isChangedSinceLastPoll(filter, lastPollMs)) {
            // The filter has been created, edited, enabled or otherwise changed since we last
            // polled it, so what we learnt from that poll no longer tells us anything.
            return true;
        }

        return nowMs >= getDueMs(tracker, lastPollMs, skipNonProducingFiltersDuration.toMillis());
    }

    /**
     * @return The time that this filter is next due a poll, for logging and system info.
     */
    public static long getDueMs(final ProcessorFilterTracker tracker,
                                final long lastPollMs,
                                final long skipNonProducingFiltersDurationMs) {
        final Long nextPollMs = tracker.getNextPollMs();
        return nextPollMs != null
                ? nextPollMs
                // Trackers written before this filter last backed off, e.g. by an upgrade, have no
                // next poll time, so fall back to the fixed wait that used to apply.
                : lastPollMs + skipNonProducingFiltersDurationMs;
    }

    /**
     * Works out the next poll time to record against the tracker at the end of a poll.
     *
     * @param nowMs        The time of the poll, i.e. the value the tracker's last poll time is
     *                     being set to.
     * @param tasksCreated How many tasks the poll created.
     * @return The next poll time, or null if the filter should be polled on the next run.
     */
    public static Long calculateNextPollMs(final ProcessorFilter filter,
                                           final ProcessorFilterTracker tracker,
                                           final ProcessorConfig processorConfig,
                                           final long nowMs,
                                           final int tasksCreated) {
        final StroomDuration skipNonProducingFiltersDuration =
                processorConfig.getSkipNonProducingFiltersDuration();
        if (tasksCreated > 0 || skipNonProducingFiltersDuration == null) {
            // The filter is producing tasks (or backing off is turned off) so start again from
            // scratch.
            return null;
        }

        final long stepMs = Math.max(0, skipNonProducingFiltersDuration.toMillis());
        final long maxMs = getMaxDelayMs(filter, processorConfig, stepMs);
        final long intervalMs = Math.min(maxMs, getPreviousIntervalMs(tracker) + stepMs);
        return nowMs + intervalMs;
    }

    /**
     * @return The longest we will wait before polling this filter again, taking any filter level
     * override of the cluster wide maximum into account.
     */
    public static long getMaxDelayMs(final ProcessorFilter filter,
                                     final ProcessorConfig processorConfig,
                                     final long defaultMaxMs) {
        final SimpleDuration maxTaskCreationDelay = filter.getMaxTaskCreationDelay();
        final StroomDuration maxDuration = maxTaskCreationDelay != null
                ? SimpleDurationUtil.convertToStroomDuration(maxTaskCreationDelay)
                : processorConfig.getSkipNonProducingFiltersMaxDuration();
        return maxDuration != null
                ? Math.max(0, maxDuration.toMillis())
                : defaultMaxMs;
    }

    /**
     * @return How long we waited before the poll that has just happened, or zero if this is the
     * first non producing poll in a run of them.
     */
    private static long getPreviousIntervalMs(final ProcessorFilterTracker tracker) {
        final Long nextPollMs = tracker.getNextPollMs();
        final Long lastPollMs = tracker.getLastPollMs();
        if (nextPollMs != null && lastPollMs != null && nextPollMs > lastPollMs) {
            return nextPollMs - lastPollMs;
        }
        return 0;
    }

    /**
     * Has the filter itself changed since we last polled it? We deliberately treat any change to
     * the filter as a reason to poll it again rather than trying to work out which fields matter;
     * getting this wrong the other way leaves a user looking at a filter they have just fixed that
     * appears to do nothing, and the cost of being wrong this way is a single extra poll.
     */
    private static boolean isChangedSinceLastPoll(final ProcessorFilter filter,
                                                  final long lastPollMs) {
        // Note that the update time is stamped by whichever node served the update while the last
        // poll time is stamped by the node that created tasks, so clock skew between nodes can
        // make a filter look changed for a while. That only costs extra polls and rights itself
        // as soon as the filter is polled with an up to date clock.
        final Long updateTimeMs = filter.getUpdateTimeMs();
        return updateTimeMs != null && updateTimeMs > lastPollMs;
    }
}
