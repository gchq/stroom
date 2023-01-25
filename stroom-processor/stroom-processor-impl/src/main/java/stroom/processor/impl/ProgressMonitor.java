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
    private final Map<ProcessorFilter, Map<String, PhaseDetails>> phaseDetailsMap = new ConcurrentHashMap<>();

    private final int queueSize;
    private final int halfQueueSize;
    private final long startTime;

    public ProgressMonitor(final ProcessorConfig processorConfig) {
        queueSize = processorConfig.getQueueSize();
        // If a queue is already half full then don't bother adding more
        halfQueueSize = queueSize / 2;
        startTime = System.currentTimeMillis();
    }

    public boolean isQueueOverHalfFull() {
        return getTotalQueuedCount() > halfQueueSize;
    }

    public int getRequiredTaskCount() {
        final int requiredTasks = queueSize - getTotalQueuedCount();
        // because of async processing it is possible to go below zero, but hide that from the caller
        return Math.max(requiredTasks, 0);
    }

//    public int getTotalCreatedCount() {
//        return Math.max(totalCreatedCount.get(), 0);
//    }

    public int getTotalQueuedCount() {
        return Math.max(totalQueuedCount.get(), 0);
    }

    public void incrementTaskQueuedCount(final int tasksQueued) {
        if (tasksQueued < 0) {
            throw new IllegalArgumentException("tasksQueued (" + tasksQueued + ") must be >= 0");
        }
        totalQueuedCount.addAndGet(tasksQueued);
//        tasksQueuedCountsMap.computeIfAbsent(filter, k -> new AtomicInteger(0))
//                .addAndGet(tasksQueued);
    }

//    public void incrementTaskCreationCount(final int tasksCreated) {
//        if (tasksCreated < 0) {
//            throw new IllegalArgumentException("tasksCreated (" + tasksCreated + ") must be >= 0");
//        }
//        totalCreatedCount.addAndGet(tasksCreated);
////        tasksCreatedCountsMap.computeIfAbsent(filter, k -> new AtomicInteger(0))
////                .addAndGet(tasksCreated);
//    }

    /**
     * Add any futures obtained during the task creation process so we can wait on them later
     */
    public void addFuture(final CompletableFuture<?> future) {
        futures.add(future);
    }

//    public int getTaskCountToCreate(final ProcessorFilter filter) {
//        final ProcessorTaskQueue queue = queueMap.get(filter);
//        if (queue != null) {
//            // This assumes the max number of tasks to create per filter is the same as the max number of
//            // tasks to create over all filters.
//            return Math.min(
//                    processorConfig.getQueueSize() - queue.size(), // head room in queue
//                    getTotalRemainingTasksToCreate()); // total tasks left to create
//        } else {
//            return 0;
//        }
//    }
//
//    public int getTotalTasksCreated() {
//        return tasksCreatedCountsMap.values().stream()
//                .mapToInt(AtomicInteger::get)
//                .sum();
//    }

//    public String getProgressSummaryMessage() {
//
//        final long filtersWithCreatedTasksCount = tasksCreatedCountsMap.entrySet()
//                .stream()
//                .filter(entry -> entry.getValue().get() > 0)
//                .count();
//
//        return LogUtil.message("Created {} tasks in total, and queued {} tasks for {} filters.",
//                getTotalCreatedCount(),
//                getTotalQueuedCount(),
//                filtersWithCreatedTasksCount);
//    }

    /**
     * @return The number of futures that are not yet complete, exceptionally or otherwise.
     */
//    public long getOutstandingFuturesCount() {
//        return futures.stream()
//                .filter(future -> !future.isDone())
//                .count();
//    }
//
//    public String getProgressDetailMessage() {
//
//        final String filterCreateCountsStr = tasksCreatedCountsMap.entrySet()
//                .stream()
//                .filter(entry -> entry.getValue().get() > 0)
//                .map(entry -> {
//                    return "ID: " + entry.getKey().getId()
//                            + " pipe name: " + entry.getKey().getPipelineName()
//                            + " count: " + entry.getValue().get();
//
//                })
//                .collect(Collectors.joining("\n"));
//
//        return LogUtil.message("""
//                        Current task creation state: \
//                        remainingTasksToCreate: {}, \
//                        futures outstanding: {}, \
//                        total tasks created: {}, \
//                        creation counts:\n{}""",
//                remainingTasksToCreateCounter.get(),
//                getOutstandingFuturesCount(),
//                getTotalTasksCreated(),
//                filterCreateCountsStr);
//    }
    public String getSummary() {
        final Map<String, PhaseDetails> combinedPhaseDetailsMap = new HashMap<>();
        for (final Entry<ProcessorFilter, Map<String, PhaseDetails>> entry : phaseDetailsMap.entrySet()) {
            for (final Entry<String, PhaseDetails> entry2 : entry.getValue().entrySet()) {
                combinedPhaseDetailsMap
                        .computeIfAbsent(entry2.getKey(), k -> new PhaseDetails())
                        .increment(entry2.getValue().count.get(), entry2.getValue().duration.get());
            }
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Created tasks for ");
        sb.append(phaseDetailsMap.size());
        sb.append(" filters in ");
        sb.append(Duration.ofMillis(System.currentTimeMillis() - startTime));
        sb.append("\n");
        sb.append("Summary: \n");
        for (final Entry<String, PhaseDetails> entry : combinedPhaseDetailsMap.entrySet()) {
            sb.append(entry.getKey());
            sb.append(": ");
            sb.append(entry.getValue().count.get());
            sb.append(" in ");
            sb.append(Duration.ofMillis(entry.getValue().duration.get()));
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getDetail() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<ProcessorFilter, Map<String, PhaseDetails>> entry : phaseDetailsMap.entrySet()) {
            sb.append("----- Filter: ");
            sb.append(entry.getKey().getId());
            sb.append(" -----\n");
            for (final Entry<String, PhaseDetails> entry2 : entry.getValue().entrySet()) {
                sb.append(entry2.getKey());
                sb.append(": ");
                sb.append(entry2.getValue().count.get());
                sb.append(" in ");
                sb.append(Duration.ofMillis(entry2.getValue().duration.get()));
            }
            sb.append("-------------------\n");
            sb.append("\n");
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

    public void logPhase(final String phase, final ProcessorFilter filter, final Supplier<Integer> supplier) {
        final long startTime = System.currentTimeMillis();
        final int count = supplier.get();
        final long duration = System.currentTimeMillis() - startTime;
        LOGGER.debug(() ->
                "Completed phase " +
                        phase +
                        " for filter " +
                        filter.getId() +
                        " with count " +
                        count + " in " +
                        Duration.ofMillis(duration));
        LOGGER.trace(() ->
                "Completed phase " +
                        phase +
                        " for filter " +
                        filter +
                        " with count " +
                        count + " in " +
                        Duration.ofMillis(duration));
        phaseDetailsMap
                .computeIfAbsent(filter, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(phase, k -> new PhaseDetails())
                .increment(count, duration);
    }

    private static class PhaseDetails {

        private final AtomicInteger count = new AtomicInteger();
        private final AtomicLong duration = new AtomicLong();

        public void increment(final int count, final long duration) {
            this.count.addAndGet(count);
            this.duration.addAndGet(duration);
        }
    }
}
