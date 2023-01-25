package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
public class ProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressMonitor.class);
    private final AtomicInteger totalQueuedCount = new AtomicInteger();
    private final List<CompletableFuture<?>> futures = new ArrayList<>();

    // Probably don't need this anymore as we are relying on the ordinal sorting of the Phase enum
    private final Map<ProcessorFilter, List<PhaseDetails>> filterToPhaseDetailsListMap = new ConcurrentHashMap<>();
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

    public String getSummary() {
        final Map<Phase, PhaseDetails> combinedPhaseDetailsMap = new HashMap<>();

        // Ensure we have all phases, even if they have not been logged
        for (final Phase phase : Phase.values()) {
            combinedPhaseDetailsMap.computeIfAbsent(phase, k -> new PhaseDetails(phase));
        }

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

        // Relies on enum order being correct
        final List<PhaseDetails> phaseDetailsList = combinedPhaseDetailsMap.entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(Entry::getValue)
                .collect(Collectors.toList());

        appendPhaseDetails(sb, phaseDetailsList);

        return sb.toString();
    }

    public String getDetail() {
        final StringBuilder sb = new StringBuilder();

        for (final ProcessorFilter filter : filterToPhaseDetailsMapMap.keySet()) {
            sb.append("Filter (");
            appendFilter(sb, filter);
            sb.append(")\n");
            final List<PhaseDetails> phaseDetailsList = filterToPhaseDetailsListMap.get(filter);

            appendPhaseDetails(sb, phaseDetailsList);

            sb.append("\n");
        }

        return sb.toString()
                .replaceFirst("\n$", "");
    }

    private void appendPhaseDetails(final StringBuilder sb,
                                    final List<PhaseDetails> phaseDetailsList) {
        phaseDetailsList.stream()
                .sorted(Comparator.comparing(phaseDetails -> phaseDetails.phase))
                .forEach(phaseDetails -> {
                    sb.append(phaseDetails.phase.phaseName);
                    sb.append(": ");
                    sb.append(phaseDetails.count.get());
                    sb.append(" (");
                    sb.append(phaseDetails.calls.get());
                    sb.append(" calls in ");
                    sb.append(Duration.ofMillis(phaseDetails.duration.get()));
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
            // Some of task creation is async (tasks for search queries) so we need
            // to wait for them to finish
            final CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[futures.size()]));

            LOGGER.debug("Waiting for all async task creation to be completed");

            LOGGER.logDurationIfDebugEnabled(
                    allOfFuture::join,
                    "Wait for futures to complete");

            allOfFuture.join();
        } else {
            LOGGER.debug("No futures to wait for");
        }
    }

    @Override
    public String toString() {
        return String.valueOf(totalQueuedCount.get());
    }

    public void logPhase(final Phase phase,
                         final ProcessorFilter filter,
                         final Supplier<Integer> supplier) {

        final long startTime = System.currentTimeMillis();
        final int count = supplier.get();
        final long duration = System.currentTimeMillis() - startTime;
        LOGGER.debug(() -> {
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
                    Duration.ofMillis(duration);
        });
        LOGGER.trace(() ->
                "Completed phase " +
                        phase.phaseName +
                        " for filter " +
                        filter +
                        " with count " +
                        count + " in " +
                        Duration.ofMillis(duration));

        final PhaseDetails phaseDetails = filterToPhaseDetailsMapMap
                .computeIfAbsent(filter, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(phase, k -> {
                    // Ensure the same PhaseDetails exists in both map and list
                    final PhaseDetails newPhaseDetails = new PhaseDetails(phase);
                    filterToPhaseDetailsListMap.computeIfAbsent(filter, k2 ->
                                    new ArrayList<>(Phase.values().length))
                            .add(newPhaseDetails);
                    return newPhaseDetails;
                });

        phaseDetails.increment(count, duration);
    }


    // --------------------------------------------------------------------------------


    public enum Phase {
        // Order is important. It governs the order the phases have in the logging
        ADD_UNOWNED_TASKS("Add unowned tasks"),
        INSERT_NEW_TASKS("Inserting new task records"),
        SELECT_NEW_TASKS("Selecting new task records"),
        UPDATE_TRACKERS("Update trackers"),
        ;

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
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicLong duration = new AtomicLong();

        private PhaseDetails(final Phase phase) {
            this.phase = phase;
        }

        public void increment(final int count, final long duration) {
            this.calls.incrementAndGet();
            this.count.addAndGet(count);
            this.duration.addAndGet(duration);
        }

        public void add(final PhaseDetails phaseDetails) {
            this.calls.addAndGet(phaseDetails.calls.get());
            this.count.addAndGet(phaseDetails.count.get());
            this.duration.addAndGet(phaseDetails.duration.get());
        }
    }
}
