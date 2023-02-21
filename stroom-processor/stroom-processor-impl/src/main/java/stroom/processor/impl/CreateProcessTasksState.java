package stroom.processor.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class CreateProcessTasksState {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CreateProcessTasksState.class);

    private final int initialTotalQueueSize;
    private final int requiredQueueSize;
    private final int halfRequiredQueueSize;

    private final AtomicInteger newTasksInDb = new AtomicInteger();
    private final AtomicInteger currentlyQueuedTasks = new AtomicInteger();
    private final AtomicInteger queuedNewTasks = new AtomicInteger();
    private final AtomicInteger queuedUnownedTasks = new AtomicInteger();
    private final AtomicInteger totalQueuedCount = new AtomicInteger();

    private final List<CompletableFuture<?>> futures = new ArrayList<>();

    public CreateProcessTasksState(final int initialTotalQueueSize,
                                   final int requiredQueueSize) {
        this.initialTotalQueueSize = initialTotalQueueSize;
        this.requiredQueueSize = requiredQueueSize;
        // If a queue is already half full then don't bother adding more
        halfRequiredQueueSize = requiredQueueSize / 2;
    }

    /**
     * Add any futures obtained during the task creation process so we can wait on them later
     */
    public void addFuture(final CompletableFuture<?> future) {
        futures.add(future);
    }

    public void waitForCompletion() {
        if (!futures.isEmpty()) {
            // Some task creation is async (tasks for search queries) so we need
            // to wait for them to finish
            final CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            LOGGER.debug("Waiting for all async task creation to be completed");

            LOGGER.logDurationIfDebugEnabled(
                    allOfFuture::join,
                    "Wait for futures to complete");

            allOfFuture.join();
        } else {
            LOGGER.debug("No futures to wait for");
        }
    }

    /**
     * Determine if we should keep trying to create tasks for subsequent filters as we will give up if the queue is at
     * least half full.
     *
     * @return True if the queue is less than half the size we would like it to be.
     */
    public boolean keepAddingTasks() {
        return totalQueuedCount.get() < halfRequiredQueueSize;
    }

    /**
     * Get the number of tasks we would still like to create if possible. This is the number of tasks configured as the
     * target queue size minus the number of tasks we have already found or added to the queue as we are examining each
     * associated filter in priority order.
     *
     * @return The number of tasks we would still like to add to the queue.
     */
    public int getRequiredTaskCount() {
        final int requiredTasks = requiredQueueSize - totalQueuedCount.get();
        // because of async processing it is possible to go below zero, but hide that from the caller
        return Math.max(requiredTasks, 0);
    }

    /**
     * Record the number of tasks already queued that could be picked up for processing.
     *
     * @param count The number of tasks currently queued for a filter.
     */
    public void addCurrentlyQueuedTasks(final int count) {
        currentlyQueuedTasks.addAndGet(count);
        totalQueuedCount.addAndGet(count);
    }

    /**
     * Record adding tasks that already existed in the database but were unowned being added to the task queue.
     *
     * @param count The number of unowned tasks that were added from the database to the task queue.
     */
    public void addUnownedTasksToQueue(final int count) {
        queuedUnownedTasks.addAndGet(count);
        totalQueuedCount.addAndGet(count);
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
     * Record the number of tasks that were created on the database and then immediately selected back and added to the
     * task queue.
     *
     * @param count The number of newly created tasks that were added to the database and added to the task queue.
     */
    public void addNewTasksToQueue(final int count) {
        queuedNewTasks.addAndGet(count);
        totalQueuedCount.addAndGet(count);
    }

    public void report(final StringBuilder sb) {
        sb.append("Total initial queue size for all filters: ");
        sb.append(initialTotalQueueSize);
        sb.append("\n");
        sb.append("Total initial queue size for considered filters: ");
        sb.append(currentlyQueuedTasks.get());
        sb.append("\n");
        sb.append("Total unowned tasks added to queues from DB: ");
        sb.append(queuedUnownedTasks.get());
        sb.append("\n");
        sb.append("Total task records created in DB: ");
        sb.append(newTasksInDb.get());
        sb.append("\n");
        sb.append("Total tasks added to queues after DB creation: ");
        sb.append(queuedNewTasks.get());
        sb.append("\n");
        sb.append("Total final queue size for considered filters: ");
        sb.append(totalQueuedCount.get());
    }
}
