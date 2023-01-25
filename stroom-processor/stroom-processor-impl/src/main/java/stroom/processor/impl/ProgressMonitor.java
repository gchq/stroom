package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
public class ProgressMonitor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressMonitor.class);
    private final AtomicInteger totalQueuedCount = new AtomicInteger();
    private final List<CompletableFuture<?>> futures = new ArrayList<>();
    // processorFilter => (phaseName => phaseDetails)
    private final Map<ProcessorFilter, Map<String, PhaseDetails>> phaseDetailsMap = new ConcurrentHashMap<>();

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
        final Map<String, PhaseDetails> combinedPhaseDetailsMap = new HashMap<>();
        for (final Entry<ProcessorFilter, Map<String, PhaseDetails>> entry : phaseDetailsMap.entrySet()) {
            for (final Entry<String, PhaseDetails> entry2 : entry.getValue().entrySet()) {
                combinedPhaseDetailsMap
                        .computeIfAbsent(entry2.getKey(), k -> new PhaseDetails())
                        .add(entry2.getValue());
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Created tasks for ");
        sb.append(phaseDetailsMap.size());
        sb.append("/");
        sb.append(totalFilterCount);
        sb.append(" filters in ");
        sb.append(Duration.ofMillis(System.currentTimeMillis() - startTime));
        sb.append("\n");
        for (final Entry<String, PhaseDetails> entry : combinedPhaseDetailsMap.entrySet()) {
            sb.append(entry.getKey());
            sb.append(": ");
            sb.append(entry.getValue().count.get());
            sb.append(" (");
            sb.append(entry.getValue().calls.get());
            sb.append(" calls in ");
            sb.append(Duration.ofMillis(entry.getValue().duration.get()));
            sb.append(")");
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getDetail() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<ProcessorFilter, Map<String, PhaseDetails>> entry : phaseDetailsMap.entrySet()) {
            sb.append("\n");
            sb.append("Filter (id = ");
            sb.append(entry.getKey().getId());
            sb.append(")\n");
            for (final Entry<String, PhaseDetails> entry2 : entry.getValue().entrySet()) {
                sb.append(entry2.getKey());
                sb.append(": ");
                sb.append(entry2.getValue().count.get());
                sb.append(" (");
                sb.append(entry2.getValue().calls.get());
                sb.append(" calls in ");
                sb.append(Duration.ofMillis(entry2.getValue().duration.get()));
                sb.append(")");
                sb.append("\n");
            }
        }
        return sb.toString();
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

    public void logPhase(final String phaseName, final ProcessorFilter filter, final Supplier<Integer> supplier) {
        final long startTime = System.currentTimeMillis();
        final int count = supplier.get();
        final long duration = System.currentTimeMillis() - startTime;
        LOGGER.debug(() ->
                "Completed phase " +
                        phaseName +
                        " for filter " +
                        filter.getId() +
                        " with count " +
                        count + " in " +
                        Duration.ofMillis(duration));
        LOGGER.trace(() ->
                "Completed phase " +
                        phaseName +
                        " for filter " +
                        filter +
                        " with count " +
                        count + " in " +
                        Duration.ofMillis(duration));
        phaseDetailsMap
                .computeIfAbsent(filter, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(phaseName, k -> new PhaseDetails())
                .increment(count, duration);
    }

    private static class PhaseDetails {

        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicLong duration = new AtomicLong();

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
