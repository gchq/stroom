/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.query.api.v2.DocRef;
import stroom.query.common.v2.Coprocessor;
import stroom.search.Event;
import stroom.search.extraction.ExtractionTask.ResultReceiver;
import stroom.search.taskqueue.AbstractTaskProducer;
import stroom.search.taskqueue.TaskExecutor;
import stroom.search.taskqueue.TaskProducer;
import stroom.task.TaskContext;
import stroom.task.ThreadPoolImpl;
import stroom.util.shared.Severity;
import stroom.util.shared.Task;
import stroom.util.shared.ThreadPool;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ExtractionTaskProducer extends AbstractTaskProducer implements TaskProducer, Comparable<ExtractionTaskProducer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionTaskProducer.class);

    public static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Extraction",
            5,
            0,
            Integer.MAX_VALUE);

    private final long now = System.currentTimeMillis();

    private final AtomicInteger threadsUsed = new AtomicInteger();
    private final int maxThreadsPerTask;

    private final AtomicInteger tasksTotal = new AtomicInteger();
    private final AtomicInteger tasksCompleted = new AtomicInteger();
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    private final TaskContext taskContext;
    private final FieldIndexMap extractionFieldIndexMap;
    private final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap;
    private final ErrorReceiver errorReceiver;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Queue<ExtractionRunnable> taskQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean completedEventMapping = new AtomicBoolean();
    private final Map<Long, List<Event>> streamEventMap = new ConcurrentHashMap<>();

    private volatile boolean finishedAddingTasks;

    public ExtractionTaskProducer(final TaskExecutor taskExecutor,
                                  final TaskContext taskContext,
                                  final StreamMapCreator streamMapCreator,
                                  final LinkedBlockingQueue<Optional<String[]>> storedData,
                                  final FieldIndexMap extractionFieldIndexMap,
                                  final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap,
                                  final ErrorReceiver errorReceiver,
                                  final int maxThreadsPerTask,
                                  final Executor executor,
                                  final Provider<ExtractionTaskHandler> handlerProvider) {
        super(taskExecutor, executor);
        this.taskContext = taskContext;
        this.extractionFieldIndexMap = extractionFieldIndexMap;
        this.extractionCoprocessorsMap = extractionCoprocessorsMap;
        this.errorReceiver = errorReceiver;
        this.maxThreadsPerTask = maxThreadsPerTask;
        this.handlerProvider = handlerProvider;

        // Start mapping streams.
        CompletableFuture.runAsync(() -> {
            LOGGER.debug("Starting extraction task producer");
            try {
                while (!completedEventMapping.get()) {
                    try {
                        // Poll for the next set of values.
                        final Optional<String[]> values = storedData.take();
                        if (values.isPresent()) {
                            // If we have some values then map them.
                            streamMapCreator.addEvent(streamEventMap, values.get());

                            // Tell the supplied executor that we are ready to deliver tasks.
                            signalAvailable();
                        } else {
                            // If we did not get any values then there are no more to get if the search task producer is complete.
                            completedEventMapping.set(true);
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.debug(e.getMessage(), e);
                        completedEventMapping.set(true);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                        completedEventMapping.set(true);
                    }
                }

                // Clear the event map if we have terminated so that other processing does not occur.
                if (taskContext.isTerminated()) {
                    terminate();
                }

            } catch (final RuntimeException e) {
                error(e.getMessage(), e);
            } finally {
                completedEventMapping.set(true);

                // Tell the supplied executor that we are ready to deliver final tasks.
                signalAvailable();
            }
        }, executor);

        // Attach to the supplied executor.
        attach();

        // Tell the supplied executor that we are ready to deliver tasks.
        signalAvailable();
    }

    public void awaitCompletion() throws InterruptedException {
        completionLatch.await();
    }

    /**
     * Get the next task to execute or null if the producer has reached a concurrent execution limit or no further tasks
     * are available.
     *
     * @return The next task to execute or null if no tasks are available at this time.
     */
    @Override
    public final Runnable next() {
//        LOGGER.info("ExtractionTaskProducer - next");

        Runnable runnable = null;

        final int count = threadsUsed.incrementAndGet();
        if (count > maxThreadsPerTask) {
            threadsUsed.decrementAndGet();
//            LOGGER.info("ExtractionTaskProducer - next - max threads");
        } else {
//            LOGGER.info("ExtractionTaskProducer - getNext -NULL {finishedAddingTasks=" + finishedAddingTasks + ", completedEventMapping="+ completedEventMapping.get() + "}");

            final Runnable task = getNext();
            if (task == null) {
//                LOGGER.info("ExtractionTaskProducer - next -NULL {finishedAddingTasks=" + finishedAddingTasks + ", completedEventMapping="+ completedEventMapping.get() + "}");

                threadsUsed.decrementAndGet();

                // Auto detach if we are complete.
                if (testComplete()) {
                    detach();
                }
            } else {
                runnable = () -> {
                    try {
                        task.run();
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                    } finally {
                        threadsUsed.decrementAndGet();
                        tasksCompleted.incrementAndGet();

                        updateCompletionStatus();

                        signalAvailable();
                    }
                };
            }
        }

        return runnable;
    }

    private Runnable getNext() {
        ExtractionRunnable task = null;

//        LOGGER.info("ExtractionTaskProducer - getNext {finishedAddingTasks=" + finishedAddingTasks + ", completedEventMapping="+ completedEventMapping.get() + "}");

        if (taskContext.isTerminated()) {
//            LOGGER.info("ExtractionTaskProducer - getNext isTerminated()");
            terminate();
        } else {
            task = taskQueue.poll();
//            LOGGER.info("ExtractionTaskProducer - getNext poll " + task);
            if (task == null) {
//                LOGGER.info("ExtractionTaskProducer - getNext addTasks ");
                finishedAddingTasks = addTasks();
//                LOGGER.info("ExtractionTaskProducer - getNext finishedAddingTasks " + finishedAddingTasks);
                task = taskQueue.poll();

//                while (completedEventMapping.get() && task == null && !finishedAddingTasks) {
//                    LOGGER.info("ExtractionTaskProducer - getNext LOOPY finishedAddingTasks " + finishedAddingTasks);
//                    finishedAddingTasks = addTasks();
//                    task = taskQueue.poll();
//                    ThreadUtil.sleep(10);
//                }

//                LOGGER.info("ExtractionTaskProducer - getNext addTasks task " + task);
                updateCompletionStatus();
            }
        }

        return task;
    }

    private void terminate() {
        finishedAddingTasks = true;
        streamEventMap.clear();

        // Drain the queue and increment the complete task count.
        while (taskQueue.poll() != null) {
            tasksCompleted.getAndIncrement();
        }

        completionLatch.countDown();
    }

    private boolean addTasks() {
        final boolean completedEventMapping = this.completedEventMapping.get();
        for (final Entry<Long, List<Event>> entry : streamEventMap.entrySet()) {
            if (streamEventMap.remove(entry.getKey(), entry.getValue())) {
                final int tasksCreated = createTasks(entry.getKey(), entry.getValue());
                if (tasksCreated > 0) {
                    return false;
                }
            }
        }
        return completedEventMapping;
    }

    private int createTasks(final long streamId, final List<Event> events) {
        int tasksCreated = 0;

        long[] eventIds = null;

        for (final Entry<DocRef, Set<Coprocessor>> entry : extractionCoprocessorsMap.entrySet()) {
            final DocRef pipelineRef = entry.getKey();
            final Set<Coprocessor> coprocessors = entry.getValue();

            if (pipelineRef != null) {
                // This set of coprocessors require result extraction so invoke the extraction service.
                final ResultReceiver resultReceiver = values -> {
                    for (final Coprocessor coprocessor : coprocessors) {
                        try {
                            coprocessor.receive(values);
                        } catch (final RuntimeException e) {
                            error(e.getMessage(), e);
                        }
                    }
                };

                if (eventIds == null) {
                    // Get a list of the event ids we are extracting for this stream and sort them.
                    eventIds = new long[events.size()];
                    for (int i = 0; i < eventIds.length; i++) {
                        eventIds[i] = events.get(i).getId();
                    }
                    // Sort the ids as the extraction expects them in order.
                    Arrays.sort(eventIds);
                }

                tasksTotal.incrementAndGet();
                final ExtractionTask task = new ExtractionTask(streamId, eventIds, pipelineRef, extractionFieldIndexMap, resultReceiver, errorReceiver);
                taskQueue.offer(new ExtractionRunnable(task, handlerProvider));
                tasksCreated++;

            } else {
                // Pass raw values to coprocessors that are not requesting values to be extracted.
                for (final Coprocessor coprocessor : coprocessors) {
                    for (final Event event : events) {
                        coprocessor.receive(event.getValues());
                    }
                }
            }
        }

        return tasksCreated;
    }

    private void error(final String message, final Throwable t) {
        errorReceiver.log(Severity.ERROR, null, null, message, t);
    }

    private void updateCompletionStatus() {
        if (testComplete()) {
            completionLatch.countDown();
        }
    }

    private boolean testComplete() {
        return taskContext.isTerminated() || (finishedAddingTasks && (tasksTotal.get() - tasksCompleted.get()) == 0);
    }

    @Override
    public int compareTo(final ExtractionTaskProducer o) {
        return Long.compare(now, o.now);
    }

    private static class ExtractionRunnable implements Runnable {
        private final ExtractionTask task;
        private final Provider<ExtractionTaskHandler> handlerProvider;

        ExtractionRunnable(final ExtractionTask task, final Provider<ExtractionTaskHandler> handlerProvider) {
            this.task = task;
            this.handlerProvider = handlerProvider;
        }

        @Override
        public void run() {
            final ExtractionTaskHandler handler = handlerProvider.get();
            handler.exec(task);
        }

        public ExtractionTask getTask() {
            return task;
        }
    }
}
