package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Holds state relating to the creation of tasks by the master node
 */
class TaskCreationProgressTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskCreationProgressTracker.class);

    private final AtomicInteger remainingTasksToCreateCounter;
    private final CountDownLatch filtersProcessedCountDownLatch;
    private final ConcurrentMap<ProcessorFilter, AtomicInteger> tasksCreatedCountsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ProcessorFilter, StreamTaskQueue> queueMap;
    private final ProcessorConfig processorConfig;

    public TaskCreationProgressTracker(final int totalTasksToCreate,
                                       final int filterCount,
                                       final ConcurrentHashMap<ProcessorFilter, StreamTaskQueue> queueMap,
                                       final ProcessorConfig processorConfig) {
        this.remainingTasksToCreateCounter = new AtomicInteger(totalTasksToCreate);
        this.filtersProcessedCountDownLatch = new CountDownLatch(filterCount);
        this.queueMap = queueMap;
        this.processorConfig = processorConfig;
    }

    public void countDownProcessedFilters() {
        filtersProcessedCountDownLatch.countDown();
    }

    public boolean areAllTasksCreated() {
        return remainingTasksToCreateCounter.get() <= 0;
    }

    public boolean areTasksRemainingToBeCreated() {
        return remainingTasksToCreateCounter.get() > 0;
    }

    public int getTotalRemainingTasksToCreate() {
        final int remainingTasks = remainingTasksToCreateCounter.get();
        // because of async processing it is possible to go below zero, but hide that from the caller
        return Math.max(remainingTasks, 0);
    }

    public int getCreatedCount(final ProcessorFilter filter) {
        final AtomicInteger counter = tasksCreatedCountsMap.get(filter);
        return counter != null
                ? counter.get()
                : 0;
    }

    public void incrementTaskCreationCount(final ProcessorFilter filter) {
        incrementTaskCreationCount(filter, 1);
    }

    public void incrementTaskCreationCount(final ProcessorFilter filter, final int tasksCreated) {
        if (tasksCreated < 0) {
            throw new IllegalArgumentException("tasksCreated (" + tasksCreated + ") must be >= 0");
        }
        remainingTasksToCreateCounter.addAndGet(tasksCreated * -1);
        tasksCreatedCountsMap.computeIfAbsent(filter, k -> new AtomicInteger(0))
                .addAndGet(tasksCreated);
    }

    public int getTaskCountToCreate(final ProcessorFilter filter) {
        final StreamTaskQueue queue = queueMap.get(filter);
        if (queue != null) {
            // This assumes the max number of tasks to create per filter is the same as the max number of
            // tasks to create over all filters.
            return Math.min(
                    processorConfig.getQueueSize() - queue.size(), // head room in queue
                    getTotalRemainingTasksToCreate()); // total tasks left to create
        } else {
            return 0;
        }
    }

    public int getTotalTasksCreated() {
        return tasksCreatedCountsMap.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
    }

    public String getProgressSummaryMessage() {

        final long filtersWithCreatedTasksCount = tasksCreatedCountsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().get() > 0)
                .count();

        return LogUtil.message("Created {} tasks in total, for {} filters.",
                getTotalTasksCreated(),
                filtersWithCreatedTasksCount);
    }

    public String getProgressDetailMessage() {

        final String filterCreateCountsStr = tasksCreatedCountsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().get() > 0)
                .map(entry -> {
                    return "ID: " + entry.getKey().getId()
                            + " pipe name: " + entry.getKey().getPipelineName()
                            + " count: " + entry.getValue().get();

                })
                .collect(Collectors.joining("\n"));

        return LogUtil.message("""
                        Current task creation state: \
                        remainingTasksToCreate: {}, \
                        filtersToProcessCount: {}, \
                        total tasks created: {}, \
                        creation counts:\n{}""",
                remainingTasksToCreateCounter.get(),
                filtersProcessedCountDownLatch.getCount(),
                getTotalTasksCreated(),
                filterCreateCountsStr);
    }


    public void waitForCompletionOfAllFilters() {
        LOGGER.logDurationIfDebugEnabled(() -> {
            try {
                // Some of task creation is async (tasks for search queries) so we need
                // to wait for them to finish
                LOGGER.debug("Waiting for task creation to be completed for all filters");
                final Instant startTime = Instant.now();
                while (true) {
                    final boolean isComplete = filtersProcessedCountDownLatch.await(10, TimeUnit.SECONDS);
                    if (isComplete) {
                        break;
                    } else {
                        LOGGER.warn(() -> LogUtil.message(
                                "Still waiting for latch to count down, count: {}, wait so far: {}, progress:\n{}",
                                filtersProcessedCountDownLatch.getCount(),
                                Duration.between(startTime, Instant.now()),
                                getProgressDetailMessage()));
                        // go round again and carry on waiting
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.error("Thread interrupted waiting for task creation to complete");
                Thread.currentThread().interrupt();
            }
        }, "Waiting for task creation to be completed for all filters");
    }

    @Override
    public String toString() {
        return "ProgressTracker{" +
                "remainingTasksToCreateCounter=" + remainingTasksToCreateCounter +
                ", filtersProcessedCountDownLatch=" + filtersProcessedCountDownLatch +
                ", tasksCreatedCountsMap=" + tasksCreatedCountsMap +
                '}';
    }
}
