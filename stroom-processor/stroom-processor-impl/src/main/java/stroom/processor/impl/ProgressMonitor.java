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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
public class ProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressMonitor.class);


    private final List<FilterProgressMonitor> filterProgressMonitorList =
            Collections.synchronizedList(new ArrayList<>());

    private final int totalFilterCount;
    private final DurationTimer totalDuration;

    public ProgressMonitor(final int totalFilterCount) {
        totalDuration = DurationTimer.start();
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
            sb.append(title);
            sb.append("\n");
            sb.append("---\n");
            sb.append("Inspected ");
            sb.append(filterProgressMonitorList.size());
            sb.append("/");
            sb.append(totalFilterCount);
            sb.append(" filters");
            sb.append("\n");
            sb.append("Total time: ");
            sb.append(totalDuration.get());
            sb.append("\n");
            if (queueProcessTasksState != null) {
                queueProcessTasksState.report(sb);
            } else {
                final AtomicInteger initialCount = new AtomicInteger();
                final AtomicInteger added = new AtomicInteger();
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
                                .computeIfAbsent(phase, k -> new PhaseDetails(phase))
                                .add(filterPhaseDetails);
                    }
                }
                appendPhaseDetails(sb, combinedPhaseDetailsMap.values());
            }
        }
    }

    private void addDetail(final StringBuilder sb, final boolean showPhaseDetail) {
        synchronized (filterProgressMonitorList) {
            if (!filterProgressMonitorList.isEmpty()) {
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

                    // Only show phase detail in trace log.
                    if (showPhaseDetail) {
                        appendPhaseDetails(sb, filterProgressMonitor.phaseDetailsMap.values());
                    }
                }
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
                    .computeIfAbsent(phase, k -> new PhaseDetails(phase));

            phaseDetails.increment(affectedItemCount, duration);
        }

        public void complete() {
            completeDuration = durationTimer.get();
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
}
