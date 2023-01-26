package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.NullSafe;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.DurationTimer.DurationResult;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
public class ProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressMonitor.class);
    private final AtomicInteger totalQueuedCount = new AtomicInteger();
    private final List<CompletableFuture<?>> futures = new ArrayList<>();

    private final Map<ProcessorFilter, Map<Phase, PhaseDetails>> filterToPhaseDetailsMapMap = new ConcurrentHashMap<>();

    private final int totalFilterCount;
    private final int queueSize;
    private final int halfQueueSize;
    private final long startTime;

    public ProgressMonitor(final ProcessorConfig processorConfig,
                           final int totalFilterCount) {
        queueSize = processorConfig.getQueueSize();
        // If a queue is already half full then don't bother adding more
        halfQueueSize = queueSize / 2;
        startTime = System.currentTimeMillis();
        this.totalFilterCount = totalFilterCount;
    }

    public boolean isQueueOverHalfFull() {
        return getTotalQueuedCount() > halfQueueSize;
    }

    public int getRequiredTaskCount() {
        final int requiredTasks = queueSize - getTotalQueuedCount();
        // because of async processing it is possible to go below zero, but hide that from the caller
        return Math.max(requiredTasks, 0);
    }

    public int getTotalQueuedCount() {
        return Math.max(totalQueuedCount.get(), 0);
    }

    public void incrementTaskQueuedCount(final int tasksQueued) {
        if (tasksQueued < 0) {
            throw new IllegalArgumentException("tasksQueued (" + tasksQueued + ") must be >= 0");
        }
        totalQueuedCount.addAndGet(tasksQueued);
    }

    /**
     * Add any futures obtained during the task creation process so we can wait on them later
     */
    public void addFuture(final CompletableFuture<?> future) {
        futures.add(future);
    }

    public void report() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(() -> {
                final String detail = getDetail();
                return LogUtil.inBoxOnNewLine("{}{}{}",
                        getSummary(),
                        (detail.isBlank()
                                ? ""
                                : "\n"),
                        detail);
            });
        } else {
            LOGGER.info(() ->
                    LogUtil.inBoxOnNewLine(getSummary()));
        }
    }

    public String getSummary() {
        final Map<Phase, PhaseDetails> combinedPhaseDetailsMap = new HashMap<>();
        for (final Entry<ProcessorFilter, Map<Phase, PhaseDetails>> entry : filterToPhaseDetailsMapMap.entrySet()) {
            for (final Entry<Phase, PhaseDetails> entry2 : entry.getValue().entrySet()) {
                final Phase phase = entry2.getKey();
                final PhaseDetails filterPhaseDetails = entry2.getValue();

                combinedPhaseDetailsMap
                        .computeIfAbsent(phase, k -> new PhaseDetails(phase))
                        .add(filterPhaseDetails);
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Created tasks for ");
        sb.append(filterToPhaseDetailsMapMap.size());
        sb.append("/");
        sb.append(totalFilterCount);
        sb.append(" filters in ");
        sb.append(Duration.ofMillis(System.currentTimeMillis() - startTime));
        sb.append("\n");

        appendPhaseDetails(sb, combinedPhaseDetailsMap.values());

        return sb.toString();
    }

    public String getDetail() {
        final StringBuilder sb = new StringBuilder();

        for (final Entry<ProcessorFilter, Map<Phase, PhaseDetails>> entry : filterToPhaseDetailsMapMap.entrySet()) {
            sb.append("Filter (");
            appendFilter(sb, entry.getKey());
            sb.append(")\n");

            appendPhaseDetails(sb, entry.getValue().values());

            sb.append("\n");
        }

        return sb.toString()
                .replaceFirst("\n$", "");
    }

    private void appendPhaseDetails(final StringBuilder sb,
                                    final Collection<PhaseDetails> phaseDetailsList) {
        phaseDetailsList.stream()
                .sorted(Comparator.comparing(phaseDetails -> phaseDetails.phase))
                .forEach(phaseDetails -> {
                    sb.append(phaseDetails.phase.phaseName);
                    sb.append(": ");
                    sb.append(phaseDetails.count.get());
                    sb.append(" (");
                    sb.append(phaseDetails.calls.get());
                    sb.append(" calls in ");
                    sb.append(phaseDetails.durationRef.get());
                    sb.append(")\n");
                });
    }

    private void appendFilter(final StringBuilder sb, final ProcessorFilter filter) {
        sb.append("id: ");
        sb.append(filter.getId());
        if (!NullSafe.isEmptyString(filter.getPipelineName())) {
            sb.append(", pipeline: ");
            sb.append(filter.getPipelineName());
        }
    }

    public void waitForCompletion() {
        if (!futures.isEmpty()) {
            // Some task creation is async (tasks for search queries) so we need
            // to wait for them to finish
            final CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            LOGGER.trace("Waiting for all async task creation to be completed");

            LOGGER.logDurationIfTraceEnabled(
                    allOfFuture::join,
                    "Wait for futures to complete");

            allOfFuture.join();
        } else {
            LOGGER.trace("No futures to wait for");
        }
    }

    @Override
    public String toString() {
        return String.valueOf(totalQueuedCount.get());
    }

    public <R> R logPhase(final Phase phase,
                          final ProcessorFilter filter,
                          final Supplier<CountResult<R>> supplier) {
        return logPhaseDuration(phase, filter, DurationTimer.measure(supplier));
    }

    public <R> R logPhaseDuration(final Phase phase,
                                  final ProcessorFilter filter,
                                  final DurationResult<CountResult<R>> durationResult) {
        final Duration duration = durationResult.getDuration();
        final CountResult<R> countResult = durationResult.getResult();
        final R result = countResult.getResult();
        final long count = countResult.getCount();
        LOGGER.trace(() -> {
            final String filterInfo = NullSafe.isEmptyString(filter.getPipelineName())
                    ? ""
                    : (" (pipeline: " + filter.getPipelineName() + ")");

            return "Completed phase " +
                    phase.phaseName +
                    " for filter " +
                    filter.getId() +
                    filterInfo +
                    " with count " +
                    count + " in " +
                    duration;
        });

        final PhaseDetails phaseDetails = filterToPhaseDetailsMapMap
                .computeIfAbsent(filter, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(phase, k -> new PhaseDetails(phase));

        phaseDetails.increment(count, duration);

        return result;
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

    public static class CountResult<R> {

        private final long count;
        private final R result;

        public CountResult(final long count) {
            this.count = count;
            this.result = null;
        }

        public CountResult(final long count, final R result) {
            this.count = count;
            this.result = result;
        }

        public long getCount() {
            return count;
        }

        public R getResult() {
            return result;
        }
    }
}
