/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.util.concurrent.DurationAdder;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
public class ProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressMonitor.class);


    private final List<FilterProgressMonitor> filterProgressMonitorList = Collections.synchronizedList(
            new ArrayList<>());
    //    private final Map<SkipReason, List<ProcessorFilter>> skippedFiltersList = new ConcurrentHashMap<>();
    private final List<SkippedFilter> skippedFilters = Collections.synchronizedList(new ArrayList<>());
    private final List<ErroredFilter> erroredFilters = Collections.synchronizedList(new ArrayList<>());

    private final int totalFilterCount;
    private final DurationTimer totalDuration;

    public ProgressMonitor(final int totalFilterCount) {
        this.totalDuration = DurationTimer.start();
        this.totalFilterCount = totalFilterCount;
    }

    public void report(final String title,
                       final QueueProcessTasksState queueProcessTasksState) {
        LOGGER.info(() -> getFullReport(
                title,
                queueProcessTasksState,
                LOGGER.isDebugEnabled(),
                LOGGER.isDebugEnabled(),
                LOGGER.isTraceEnabled()));
    }

    public String getFullReport(final String title,
                                final QueueProcessTasksState queueProcessTasksState,
                                final boolean showFilterDetail,
                                final boolean showSummaryPhaseDetail,
                                final boolean showFilterPhaseDetail) {
        final StringBuilder sb = new StringBuilder();
        addSummary(title, sb, showSummaryPhaseDetail, queueProcessTasksState);
        if (showFilterDetail) {
            addDetail(sb, showFilterPhaseDetail);
        }
        return LogUtil.inBoxOnNewLine(sb.toString());
    }

    private void addSummary(final String title,
                            final StringBuilder sb,
                            final boolean showPhaseDetail,
                            final QueueProcessTasksState queueProcessTasksState) {
        synchronized (filterProgressMonitorList) {

            final List<FilterProgressMonitor> filterMonitorsWithoutError = filterProgressMonitorList.stream()
                    .filter(Predicate.not(FilterProgressMonitor::hasError))
                    .toList();
            final long erroredFiltersCount = Stream.concat(
                            filterProgressMonitorList.stream()
                                    .filter(FilterProgressMonitor::hasError)
                                    .map(FilterProgressMonitor::getId),
                            erroredFilters.stream()
                                    .map(ErroredFilter::filter)
                                    .map(ProcessorFilter::getId))
                    .filter(Objects::nonNull)
                    .count();

            sb.append(title);
            sb.append("\n");
            sb.append("---\n");
            sb.append("Inspected ");
            sb.append(filterMonitorsWithoutError.size());
            sb.append("/");
            sb.append(totalFilterCount);
            sb.append(" filters");
            sb.append("\n");
            sb.append("Skipped: ");
            sb.append(skippedFilters.size());
            sb.append("\n");
            sb.append("Errored: ");
            sb.append(erroredFiltersCount);
            sb.append("\n");
            sb.append("Total time: ");
            sb.append(totalDuration.get());
            sb.append("\n");
            if (queueProcessTasksState != null) {
                queueProcessTasksState.report(sb);
            } else {
                final AtomicInteger initialCount = new AtomicInteger();
                final AtomicInteger added = new AtomicInteger();
                // It's possible the error happened after the tasks were created
                filterProgressMonitorList.forEach(filterProgressMonitor -> {
                    initialCount.addAndGet(filterProgressMonitor.initialCount);
                    added.addAndGet(filterProgressMonitor.added.get());
                });
                sb.append("Initial: ");
                sb.append(initialCount.get());
                sb.append("\n");
                sb.append("Added: ");
                sb.append(added.get());
                sb.append("\n");
                sb.append("Final: ");
                sb.append(initialCount.get() + added.get());
            }

            // Only show phase detail in trace log.
            if (showPhaseDetail) {
                final Map<Phase, PhaseDetails> combinedPhaseDetailsMap = new HashMap<>();
                for (final FilterProgressMonitor filterProgressMonitor : filterProgressMonitorList) {
                    for (final Entry<Phase, PhaseDetails> entry : filterProgressMonitor.phaseDetailsMap.entrySet()) {
                        final Phase phase = entry.getKey();
                        final PhaseDetails filterPhaseDetails = entry.getValue();

                        combinedPhaseDetailsMap
                                .computeIfAbsent(phase, ignored -> new PhaseDetails(phase))
                                .add(filterPhaseDetails);
                    }
                }
                appendPhaseDetails(sb, combinedPhaseDetailsMap.values());
            }
        }
    }

    private void addDetail(final StringBuilder sb, final boolean showPhaseDetail) {
        synchronized (filterProgressMonitorList) {
            if (!filterProgressMonitorList.isEmpty() || !skippedFilters.isEmpty() || !erroredFilters.isEmpty()) {
                sb.append("\n\nDETAIL");
                for (final FilterProgressMonitor filterProgressMonitor : filterProgressMonitorList) {
                    final ProcessorFilter filter = filterProgressMonitor.filter;
                    sb.append("\n---\n");
                    sb.append("Filter (");
                    appendFilter(sb, filter);
                    sb.append(")\n");
                    sb.append("Total create time: ");
                    sb.append(filterProgressMonitor.completeDuration);
                    sb.append("\n");
                    sb.append("Initial: ");
                    sb.append(filterProgressMonitor.initialCount);
                    sb.append("\n");
                    sb.append("Added: ");
                    sb.append(filterProgressMonitor.added.get());
                    sb.append("\n");
                    sb.append("Final: ");
                    sb.append(filterProgressMonitor.initialCount +
                              filterProgressMonitor.added.get());
                    if (filterProgressMonitor.hasError()) {
                        sb.append("\n");
                        sb.append("Error: ");
                        sb.append(filterProgressMonitor.throwable.getMessage());
                    }

                    // Only show phase detail in trace log.
                    if (showPhaseDetail) {
                        appendPhaseDetails(sb, filterProgressMonitor.phaseDetailsMap.values());
                    }
                }

                skippedFilters.forEach(skippedFilter -> {
                    sb.append("\n---\n");
                    sb.append("Filter (");
                    appendFilter(sb, skippedFilter.filter);
                    sb.append(")\n");
                    sb.append("Skipped due to: ");
                    sb.append(skippedFilter.skipReason.getDisplayValue());
                });

                erroredFilters.forEach(erroredFilter -> {
                    sb.append("\n---\n");
                    sb.append("Filter (");
                    appendFilter(sb, erroredFilter.filter);
                    sb.append(")\n");
                    sb.append("Failed with error : ");
                    sb.append(erroredFilter.throwable.getMessage());
                });
            }
        }
    }

    private void appendPhaseDetails(final StringBuilder sb,
                                    final Collection<PhaseDetails> phaseDetailsList) {
        sb.append("\n");
        phaseDetailsList.stream()
                .sorted(Comparator.comparing(phaseDetails -> phaseDetails.phase))
                .forEach(phaseDetails -> {
                    sb.append("\n");
                    sb.append(phaseDetails.phase.phaseName);
                    sb.append(": ");
                    sb.append(phaseDetails.affectedItemCount.get());
                    sb.append(" (");
                    sb.append(phaseDetails.calls.get());
                    sb.append(" calls in ");
                    sb.append(phaseDetails.durationAdder.get());
                    sb.append(")");
                });
    }

    private void appendFilter(final StringBuilder sb, final ProcessorFilter filter) {
        sb.append("id: ");
        sb.append(filter.getId());
        sb.append(", priority: ");
        sb.append(filter.getPriority());
        if (!NullSafe.isEmptyString(filter.getPipelineName())) {
            sb.append(", pipeline: ");
            sb.append(filter.getPipelineName());
        }
    }

    public FilterProgressMonitor logFilter(final ProcessorFilter filter,
                                           final int initialQueueSize) {
        final FilterProgressMonitor filterProgressMonitor = new FilterProgressMonitor(
                filter,
                initialQueueSize);
        filterProgressMonitorList.add(filterProgressMonitor);
        return filterProgressMonitor;
    }

    /**
     * Log a filter that has been skipped for some reason
     */
    public void logSkippedFilter(final ProcessorFilter filter,
                                 final SkipReason reason) {
        try {
            Objects.requireNonNull(filter);
            Objects.requireNonNull(reason);
            skippedFilters.add(new SkippedFilter(filter, reason));
        } catch (final Exception e) {
            LOGGER.error("Error logging a skipped filter {} - {}",
                    NullSafe.get(filter, ProcessorFilter::getFilterInfo),
                    LogUtil.exceptionMessage(e),
                    e);
            // Swallow so progress monitoring doesn't halt processing
        }
    }

    public void logErroredFilter(final ProcessorFilter filter,
                                 final Throwable filterException) {
        try {
            Objects.requireNonNull(filter);
            Objects.requireNonNull(filterException);
            erroredFilters.add(new ErroredFilter(filter, filterException));
        } catch (final Exception logException) {
            LOGGER.error("Error logging a errored filter {} (filter error: {})- {}",
                    NullSafe.get(filter, ProcessorFilter::getFilterInfo),
                    LogUtil.exceptionMessage(filterException),
                    LogUtil.exceptionMessage(logException),
                    logException);
            // Swallow so progress monitoring doesn't halt processing
        }
    }

    // --------------------------------------------------------------------------------


    public enum Phase {
        // Order is important. It governs the order the phases have in the logging
        QUEUE_CREATED_TASKS("Queue created tasks"),
        QUEUE_CREATED_TASKS_FETCH_TASKS("Queue created tasks -> Fetch tasks"),
        QUEUE_CREATED_TASKS_FETCH_META("Queue created tasks -> Fetch meta"),
        QUEUE_CREATED_TASKS_QUEUE_TASKS("Queue created tasks -> Queue tasks"),
        CREATE_TASKS_FROM_SEARCH_QUERY("Create tasks from search query"),
        CREATE_STREAM_MAP("Create stream map"),
        FIND_META_FOR_FILTER("Find meta records matching filter"),
        INSERT_NEW_TASKS("Inserting new task records"),
        UPDATE_TRACKERS("Update trackers"),
        RELEASE_TASKS_FOR_DISABLED_FILTERS("Release tasks for disabled filters");

        private final String phaseName;

        Phase(final String phaseName) {
            this.phaseName = phaseName;
        }

        public String getPhaseName() {
            return phaseName;
        }
    }


    // --------------------------------------------------------------------------------


    public static class FilterProgressMonitor {

        private final ProcessorFilter filter;
        private final DurationTimer durationTimer;

        private final Map<Phase, PhaseDetails> phaseDetailsMap = new ConcurrentHashMap<>();

        private final int initialCount;
        private final AtomicInteger added = new AtomicInteger();

        private Duration completeDuration;
        private Throwable throwable = null;

        private FilterProgressMonitor(final ProcessorFilter filter,
                                      final int initialCount) {
            this.filter = filter;
            this.initialCount = initialCount;
            this.durationTimer = DurationTimer.start();
        }

        /**
         * Record the total number of tasks that have been created on the database during the task creation process.
         *
         * @param count The number of task records added to the database.
         */
        public void add(final int count) {
            added.addAndGet(count);
        }

        /**
         * Log the duration of a phase with {@code phase} affected items.
         *
         * @param phase             The phase to log against.
         * @param durationTimer     The duration of the phase.
         * @param affectedItemCount The number of items affected in the phase.
         */
        public void logPhase(final Phase phase,
                             final DurationTimer durationTimer,
                             final long affectedItemCount) {
            final Duration duration = durationTimer.get();
            final PhaseDetails phaseDetails = phaseDetailsMap
                    .computeIfAbsent(phase, ignored -> new PhaseDetails(phase));

            phaseDetails.increment(affectedItemCount, duration);
        }

        public void logException(final Throwable throwable) {
            this.throwable = throwable;
        }

        public void complete() {
            completeDuration = durationTimer.get();
        }

        public void complete(final Throwable throwable) {
            this.throwable = throwable;
            completeDuration = durationTimer.get();
        }

        boolean hasError() {
            return throwable != null;
        }

        Integer getId() {
            return NullSafe.get(filter, ProcessorFilter::getId);
        }
    }


    // --------------------------------------------------------------------------------


    private static class PhaseDetails {

        private final Phase phase;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicLong affectedItemCount = new AtomicLong();
        private final DurationAdder durationAdder = new DurationAdder();

        private PhaseDetails(final Phase phase) {
            this.phase = phase;
        }

        /**
         * Increment the call count by one and add the duration.
         */
        public void increment(final Duration duration) {
            increment(0, duration);
        }

        /**
         * Increment the call count by one, increment the count of items affected by the
         * phase by {@code count} and add the duration.
         *
         * @param count    Number of items affected
         * @param duration The duration to add.
         */
        public void increment(final long count, final Duration duration) {
            this.calls.incrementAndGet();
            this.affectedItemCount.addAndGet(count);
            durationAdder.add(duration);
        }

        public void add(final PhaseDetails phaseDetails) {
            this.calls.addAndGet(phaseDetails.calls.get());
            this.affectedItemCount.addAndGet(phaseDetails.affectedItemCount.get());
            durationAdder.add(phaseDetails.durationAdder);
        }
    }


    // --------------------------------------------------------------------------------


    public enum SkipReason {
        /**
         * The maximum number of tasks has been reached for this filter
         */
        MAX_TASKS_REACHED("Maximum number of tasks already created"),
        /**
         * The filter created zero tasks on the last poll
         */
        ZERO_TASKS_ON_LAST_POLL("No tasks created on last poll"),
        /**
         * Filter was disabled/deleted after the job started
         */
        DISABLED_OR_DELETED("Filter was disabled/deleted after the job started"),
        /**
         * The tracker is in a completed state
         */
        TRACKER_COMPLETE("The tracker is in a completed state"),
        /**
         * The tracker is in a error state
         */
        TRACKER_ERROR("The tracker is in a error state");

        private final String displayValue;

        SkipReason(final String displayValue) {
            this.displayValue = displayValue;
        }

        public String getDisplayValue() {
            return displayValue;
        }
    }


    // --------------------------------------------------------------------------------


    private record SkippedFilter(ProcessorFilter filter,
                                 SkipReason skipReason) {

        private SkippedFilter {
            Objects.requireNonNull(filter);
            Objects.requireNonNull(skipReason);
        }
    }


    // --------------------------------------------------------------------------------


    private record ErroredFilter(ProcessorFilter filter,
                                 Throwable throwable) {

        private ErroredFilter {
            Objects.requireNonNull(filter);
            Objects.requireNonNull(throwable);
        }
    }
}
