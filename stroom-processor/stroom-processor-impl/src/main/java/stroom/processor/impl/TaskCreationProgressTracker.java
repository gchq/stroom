package stroom.processor.impl;

import stroom.processor.shared.ProcessorFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Holds state relating to the progress of creation of tasks by the master node
 */
class TaskCreationProgressTracker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TaskCreationProgressTracker.class);

    // The number of tasks we want to create which will be decremented as we create them
    private final AtomicInteger remainingTasksToCreateCounter;
    private final ConcurrentMap<ProcessorFilter, AtomicInteger> tasksCreatedCountsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ProcessorFilter, ProcessorTaskQueue> queueMap;
    private final ProcessorConfig processorConfig;
    private final List<CompletableFuture<?>> futures = new ArrayList<>();

    public TaskCreationProgressTracker(final int totalTasksToCreate,
                                       final ConcurrentHashMap<ProcessorFilter, ProcessorTaskQueue> queueMap,
                                       final ProcessorConfig processorConfig) {
        this.remainingTasksToCreateCounter = new AtomicInteger(totalTasksToCreate);
        this.queueMap = queueMap;
        this.processorConfig = processorConfig;
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

    /**
     * Add any futures obtained during the task creation process so we can wait on them later
     */
    public void addFuture(final CompletableFuture<?> future) {
        futures.add(future);
    }

    public int getTaskCountToCreate(final ProcessorFilter filter) {
        final ProcessorTaskQueue queue = queueMap.get(filter);
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

    /**
     * @return The number of futures that are not yet complete, exceptionally or otherwise.
     */
    public long getOutstandingFuturesCount() {
        return futures.stream()
                .filter(future -> !future.isDone())
                .count();
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
                        futures outstanding: {}, \
                        total tasks created: {}, \
                        creation counts:\n{}""",
                remainingTasksToCreateCounter.get(),
                getOutstandingFuturesCount(),
                getTotalTasksCreated(),
                filterCreateCountsStr);
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
        return "TaskCreationProgressTracker{" +
                "remainingTasksToCreateCounter=" + remainingTasksToCreateCounter +
                ", tasksCreatedCountsMap=" + tasksCreatedCountsMap +
                ", queueMap=" + queueMap +
                ", processorConfig=" + processorConfig +
                ", futures=" + futures +
                '}';
    }
}
