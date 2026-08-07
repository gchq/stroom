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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * gh-5699. Answers "which of the filters this node may process have work waiting for it?" without
 * asking the database about each filter in turn. See
 * PROCESSOR_WORKER_TASK_QUEUEING_DESIGN.md §3.2.
 * <p>
 * <b>Why caching this is safe when caching task ids would not be.</b> The design deliberately
 * keeps no cache of task <em>identities</em>: a stale task id goes stale into a collision with
 * another node's claim, and coping with that needs cursors, wrap-around and per-node offsets.
 * Stale filter <em>availability</em> goes stale into one wasted claim query, or into one refresh
 * interval of extra latency for a filter that has only just gained work. Both self-correct, and
 * neither can cause a task to be processed twice.
 * <p>
 * <b>Node-local, by design.</b> Each node summarises its own eligible filters, in its own JVM, and
 * answers only for itself. One node computing this on behalf of others is the rejected Option C:
 * it puts the master back on the processing path this mode exists to take it off, and degrades
 * exactly when the cluster is most stressed.
 * <p>
 * The summary is intersected with the <em>current</em> eligible set on every call rather than with
 * the set it was taken over, so a filter this node has just become eligible for is picked up as
 * soon as the next refresh includes it - at most one interval late. That is the same
 * self-correcting staleness as above, not an oversight.
 */
@Singleton
public class ProcessorTaskAvailability implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTaskAvailability.class);

    private final EligibleFilters eligibleFilters;
    private final ProcessorTaskDao processorTaskDao;
    private final Provider<ProcessorConfig> processorConfigProvider;

    private final Object refreshLock = new Object();

    /**
     * The last summary taken, or null if none has been. Volatile so the common case - a summary
     * that is still current - is answered without contending on the refresh lock.
     */
    private volatile Summary summary;

    @Inject
    public ProcessorTaskAvailability(final EligibleFilters eligibleFilters,
                                     final ProcessorTaskDao processorTaskDao,
                                     final Provider<ProcessorConfig> processorConfigProvider) {
        this.eligibleFilters = eligibleFilters;
        this.processorTaskDao = processorTaskDao;
        this.processorConfigProvider = processorConfigProvider;
    }

    /**
     * @return The filters this node may currently process that have tasks waiting, highest
     * priority first. Empty if there is nothing here for this node to do.
     */
    public List<FilterAvailability> getFiltersWithWork() {
        return getFiltersWithWork(System.currentTimeMillis());
    }

    /**
     * Factored with "now" as a parameter for testing (there is no injectable clock in
     * stroom-util; same approach as #5683).
     */
    public List<FilterAvailability> getFiltersWithWork(final long nowMs) {
        // Already in priority order, so the result is too - see PrioritisedFilters.
        final List<ProcessorFilter> eligible = eligibleFilters.getEligibleFilters(Instant.ofEpochMilli(nowMs));
        final Map<Integer, Long> availability = getAvailability(eligible, nowMs);

        final List<FilterAvailability> filtersWithWork = eligible
                .stream()
                .filter(filter -> availability.containsKey(filter.getId()))
                .map(filter -> new FilterAvailability(filter, availability.get(filter.getId())))
                .toList();
        LOGGER.debug(() -> LogUtil.message("getFiltersWithWork() - {}/{} eligible filters have work",
                filtersWithWork.size(), eligible.size()));
        return filtersWithWork;
    }

    private Map<Integer, Long> getAvailability(final List<ProcessorFilter> eligible, final long nowMs) {
        final long intervalMs = processorConfigProvider.get().getTaskAvailabilityInterval().toMillis();

        Summary current = summary;
        if (isCurrent(current, nowMs, intervalMs)) {
            return current.availability();
        }

        synchronized (refreshLock) {
            // Every processing thread that wants work asks this question at once, and they all
            // want the same answer, so one of them takes the summary and the rest use it rather
            // than each running the same query.
            current = summary;
            if (isCurrent(current, nowMs, intervalMs)) {
                return current.availability();
            }

            final Map<Integer, Long> availability = processorTaskDao.getTaskAvailability(
                    eligible.stream().map(ProcessorFilter::getId).toList());
            summary = new Summary(availability, nowMs);
            return availability;
        }
    }

    /**
     * The summary this node is currently working from, without taking a fresh one. Diagnostics
     * only - a look at the state must not change it, or reading sysinfo would perturb the thing
     * being diagnosed.
     *
     * @return Empty if no summary has been taken yet, i.e. this node has not looked for work.
     */
    public Optional<SummaryInfo> getSummaryInfo() {
        final Summary current = summary;
        return current == null
                ? Optional.empty()
                : Optional.of(new SummaryInfo(Map.copyOf(current.availability()), current.takenMs()));
    }

    private static boolean isCurrent(final Summary summary, final long nowMs, final long intervalMs) {
        // A zero interval turns the cache off, and so does a clock that has gone backwards - take
        // a fresh summary rather than trust one of unknown age.
        return summary != null
               && intervalMs > 0
               && nowMs >= summary.takenMs()
               && nowMs - summary.takenMs() < intervalMs;
    }

    @Override
    public void clear() {
        summary = null;
    }


    // --------------------------------------------------------------------------------


    /**
     * A filter with tasks waiting, and the id of the oldest of them. The task id is not a claim on
     * anything - by the time it is used another node may have taken that task - it is the oldest
     * waiting work for the filter at the moment the summary was taken, useful for ordering and
     * for diagnostics, and it comes free with the summary query.
     */
    public record FilterAvailability(ProcessorFilter filter, long oldestTaskId) {

    }


    // --------------------------------------------------------------------------------


    private record Summary(Map<Integer, Long> availability, long takenMs) {

    }


    // --------------------------------------------------------------------------------


    /**
     * @param availability Filter id to the id of its oldest waiting task, as at {@code takenMs}.
     * @param takenMs      When this summary was taken.
     */
    public record SummaryInfo(Map<Integer, Long> availability, long takenMs) {

    }
}
