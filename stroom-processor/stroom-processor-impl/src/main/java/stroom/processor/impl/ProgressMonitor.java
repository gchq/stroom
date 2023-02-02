package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.NullSafe;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
public class ProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressMonitor.class);


    private final List<FilterProgressMonitor> filterProgressMonitorList = new ArrayList<>();

    private final int totalFilterCount;
    private final DurationTimer totalDuration;

    public ProgressMonitor(final int totalFilterCount) {
        totalDuration = DurationTimer.start();
        this.totalFilterCount = totalFilterCount;
    }

    public void report(final CreateProcessTasksState createProcessTasksState) {
        LOGGER.info(() -> getFullReport(
                createProcessTasksState,
                LOGGER.isDebugEnabled(),
                LOGGER.isDebugEnabled(),
                LOGGER.isTraceEnabled()));
    }

    public String getFullReport(final CreateProcessTasksState createProcessTasksState,
                                final boolean showFilterDetail,
                                final boolean showSummaryPhaseDetail,
                                final boolean showFilterPhaseDetail) {
        final StringBuilder sb = new StringBuilder();
        addSummary(sb, showSummaryPhaseDetail, createProcessTasksState);
        if (showFilterDetail) {
            addDetail(sb, showFilterPhaseDetail);
        }
        return LogUtil.inBoxOnNewLine(sb.toString());
    }

    private void addSummary(final StringBuilder sb,
                            final boolean showPhaseDetail,
                            final CreateProcessTasksState createProcessTasksState) {
        sb.append("SUMMARY\n");
        sb.append("---\n");
        sb.append("Inspected ");
        sb.append(filterProgressMonitorList.size());
        sb.append("/");
        sb.append(totalFilterCount);
        sb.append(" filters for task creation");
        sb.append("\n");
        sb.append("Total time: ");
        sb.append(totalDuration.get());
        sb.append("\n");
        createProcessTasksState.report(sb);

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

    private void addDetail(final StringBuilder sb, final boolean showPhaseDetail) {
        if (filterProgressMonitorList.size() > 0) {
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
                sb.append("Initial queue size: ");
                sb.append(filterProgressMonitor.initialQueueSize);
                sb.append("\n");
                sb.append("Unowned tasks added to queue: ");
                sb.append(filterProgressMonitor.queuedUnownedTasks.get());
                sb.append("\n");
                sb.append("Task records created in DB: ");
                sb.append(filterProgressMonitor.newTasksInDb.get());
                sb.append("\n");
                sb.append("Tasks added to queue after DB creation: ");
                sb.append(filterProgressMonitor.queuedNewTasks.get());
                sb.append("\n");
                sb.append("Final queue size: ");
                sb.append(filterProgressMonitor.initialQueueSize +
                        filterProgressMonitor.queuedUnownedTasks.get() +
                        filterProgressMonitor.queuedNewTasks.get());

                // Only show phase detail in trace log.
                if (showPhaseDetail) {
                    appendPhaseDetails(sb, filterProgressMonitor.phaseDetailsMap.values());
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
                    sb.append(phaseDetails.count.get());
                    sb.append(" (");
                    sb.append(phaseDetails.calls.get());
                    sb.append(" calls in ");
                    sb.append(phaseDetails.durationRef.get());
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
        ADD_UNOWNED_TASKS("Add unowned tasks"),
        ADD_UNOWNED_TASKS_FETCH_TASKS("Add unowned tasks -> Fetch tasks"),
        ADD_UNOWNED_TASKS_FETCH_META("Add unowned tasks -> Fetch meta"),
        ADD_UNOWNED_TASKS_QUEUE_TASKS("Add unowned tasks -> Queue tasks"),
        CREATE_TASKS_FROM_SEARCH_QUERY("Create tasks from search query"),
        CREATE_STREAM_MAP("Create stream map"),
        FIND_META_FOR_FILTER("Find meta records matching filter"),
        INSERT_NEW_TASKS("Inserting new task records"),
        SELECT_NEW_TASKS("Selecting new task records"),
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

        private final int initialQueueSize;
        private final AtomicInteger newTasksInDb = new AtomicInteger();
        private final AtomicInteger queuedNewTasks = new AtomicInteger();
        private final AtomicInteger queuedUnownedTasks = new AtomicInteger();

        private Duration completeDuration;

        private FilterProgressMonitor(final ProcessorFilter filter,
                                      final int initialQueueSize) {
            this.filter = filter;
            this.initialQueueSize = initialQueueSize;
            this.durationTimer = DurationTimer.start();
        }

        /**
         * Record adding tasks that already existed in the database but were unowned being added to the task queue.
         *
         * @param count The number of unowned tasks that were added from the database to the task queue.
         */
        public void addUnownedTasksToQueue(final int count) {
            queuedUnownedTasks.addAndGet(count);
        }

        /**
         * Record the total number of tasks that have been created on the database during the task creation process.
         *
         * @param count The number of task records added to the database.
         */
        public void addNewTasksInDb(final int count) {
            newTasksInDb.addAndGet(count);
        }

        /**
         * Record the number of tasks that were created on the database and then immediately selected back and added to
         * the task queue.
         *
         * @param count The number of newly created tasks that were added to the database and added to the task queue.
         */
        public void addNewTasksToQueue(final int count) {
            queuedNewTasks.addAndGet(count);
        }

        public void logPhase(final Phase phase,
                             final DurationTimer durationTimer,
                             final long count) {
            final Duration duration = durationTimer.get();
            final PhaseDetails phaseDetails = phaseDetailsMap
                    .computeIfAbsent(phase, k -> new PhaseDetails(phase));

            phaseDetails.increment(count, duration);
        }

        public void complete() {
            completeDuration = durationTimer.get();
        }
    }

    private static class PhaseDetails {

        private final Phase phase;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicLong count = new AtomicLong();
        private final AtomicReference<Duration> durationRef = new AtomicReference<>(Duration.ofMillis(0));

        private PhaseDetails(final Phase phase) {
            this.phase = phase;
        }

        public void increment(final long count, final Duration duration) {
            this.calls.incrementAndGet();
            this.count.addAndGet(count);
            durationRef.accumulateAndGet(duration, Duration::plus);
        }

        public void add(final PhaseDetails phaseDetails) {
            this.calls.addAndGet(phaseDetails.calls.get());
            this.count.addAndGet(phaseDetails.count.get());
            durationRef.accumulateAndGet(phaseDetails.durationRef.get(), Duration::plus);
        }
    }
}
