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

import stroom.dashboard.expression.v1.Val;
import stroom.docref.DocRef;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.Receiver;
import stroom.query.common.v2.ReceiverImpl;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskExecutor;
import stroom.task.api.TaskProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

class ExtractionTaskProducer extends TaskProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionTaskProducer.class);
    private static final String TASK_NAME = "Extraction";

    private final Consumer<Throwable> parentErrorConsumer;
    private final Map<DocRef, ExtractionReceiver> receivers;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Queue<Consumer<TaskContext>> taskQueue = new ConcurrentLinkedQueue<>();

    private final AtomicLong indexSearchTotalValues = new AtomicLong();

    private final CompletionState streamMapCreatorCompletionState = new CompletionState();
    private final StreamEventMap streamEventMap;
    private final LinkedBlockingQueue<Optional<Val[]>> storedDataQueue;
    private final ExtractionProgressTracker tracker;

    ExtractionTaskProducer(final TaskExecutor taskExecutor,
                           final StreamMapCreator streamMapCreator,
                           final Consumer<Throwable> parentErrorConsumer,
                           final Map<DocRef, ExtractionReceiver> receivers,
                           final int maxStoredDataQueueSize,
                           final int maxThreadsPerTask,
                           final int maxStreamEventMapSize,
                           final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final TaskContext parentContext,
                           final Provider<ExtractionTaskHandler> handlerProvider,
                           final SecurityContext securityContext,
                           final ExtractionProgressTracker tracker) {
        super(taskExecutor, maxThreadsPerTask, taskContextFactory, parentContext, TASK_NAME);
        this.parentErrorConsumer = parentErrorConsumer;
        this.receivers = receivers;
        this.handlerProvider = handlerProvider;
        this.tracker = tracker;

        // Create a queue to receive values and store them for asynchronous processing.
        streamEventMap = new StreamEventMap(maxStreamEventMapSize);
        storedDataQueue = new LinkedBlockingQueue<>(maxStoredDataQueueSize);

        // Start mapping streams.
        final Consumer<TaskContext> consumer = tc -> {
            // Elevate permissions so users with only `Use` feed permission can `Read` streams.
            securityContext.asProcessingUser(() -> {
                LOGGER.debug("Starting extraction task producer");
                try {
                    while (!streamMapCreatorCompletionState.isComplete() && !Thread.currentThread().isInterrupted()) {
                        tc.info(() -> "" +
                                "Creating extraction tasks - stored data queue size: " +
                                storedDataQueue.size() +
                                " stream event map size: " +
                                streamEventMap.size());

                        // Poll for the next set of values.
                        final Optional<Val[]> optional = storedDataQueue.take();

                        try {
                            // We will have a value here unless index search has finished adding values in which case we
                            // will have an empty optional.
                            if (optional.isPresent()) {
                                try {
                                    // If we have some values then map them.
                                    streamMapCreator.addEvent(streamEventMap, optional.get());

                                } catch (final RuntimeException e) {
                                    LOGGER.debug(e.getMessage(), e);
                                    receivers.values().forEach(receiver ->
                                            receiver.getErrorConsumer().accept(e));
                                }
                            } else {
                                // We got no values from the topic so if index search ois complete then we have finished
                                // mapping too.
                                streamMapCreatorCompletionState.complete();
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e.getMessage(), e);
                            throw e;
                        } finally {
                            // Tell the supplied executor that we are ready to deliver tasks.
                            signalAvailable();
                        }
                    }
                } catch (final InterruptedException e) {
                    LOGGER.debug(e.getMessage(), e);
                    // Continue to interrupt.
                    Thread.currentThread().interrupt();
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    streamMapCreatorCompletionState.complete();
                    tc.info(() -> "Finished creating extraction tasks");
                    LOGGER.debug("Finished creating extraction tasks");

                    // Tell the supplied executor that we are ready to deliver final tasks.
                    signalAvailable();
                }
            });
        };
        final Runnable runnable = taskContextFactory.context(parentContext, "Extraction Task Mapper", consumer);
        final Executor executor = executorProvider.get(ExtractionTaskExecutor.THREAD_POOL);
        CompletableFuture.runAsync(runnable, executor);
    }

    Receiver process() {
        // Attach to the supplied executor.
        attach();

        // Tell the supplied executor that we are ready to deliver tasks.
        signalAvailable();

        return new ReceiverImpl(
                this::addToStoredDataQueue,
                parentErrorConsumer,
                count -> {
                    indexSearchTotalValues.set(count);

                    // Add null values to signal completion.
                    addToStoredDataQueue(null);
                });
    }

    public void addToStoredDataQueue(final Val[] values) {
        try {
            storedDataQueue.put(Optional.ofNullable(values));
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();

            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    protected boolean isComplete() {
        return Thread.currentThread().isInterrupted() || tracker.isComplete();
    }

    @Override
    protected Consumer<TaskContext> getNext() {
        Consumer<TaskContext> task = null;

        if (!isComplete()) {
            task = taskQueue.poll();
            if (task == null) {
                final boolean completedEventMapping = this.streamMapCreatorCompletionState.isComplete();
                final Optional<Entry<Long, List<Event>>> optional = streamEventMap.get();
                if (optional.isPresent()) {
                    final Entry<Long, List<Event>> entry = optional.get();
                    createTasks(entry.getKey(), entry.getValue());

                } else if (completedEventMapping) {
                    // if we didn't get any events from the event map and we have completed event mapping then there are
                    // no more tasks to create.
                    tracker.finishedAddingTasks();
                    testComplete();
                }

                task = taskQueue.poll();
            }
        }

        return task;
    }

    private void createTasks(final long streamId, final List<Event> events) {
        final AtomicInteger tasksCreated = new AtomicInteger();

        final long[] eventIds = createEventIdArray(events, receivers);
        receivers.forEach((docRef, receiver) -> {
            tracker.incrementTasksTotal();

            Consumer<TaskContext> consumer;
            if (docRef != null) {
                consumer = (taskContext) -> {
                    try {
                        final ExtractionTaskHandler handler = handlerProvider.get();
                        handler.exec(taskContext, new ExtractionTask(streamId, eventIds, docRef, receiver));
                    } finally {
                        tracker.incrementTasksCompleted();
                        testComplete();
                    }
                };

            } else {
                consumer = (taskContext) -> {
                    try {
                        taskContext.info(() -> "Transferring " + events.size() + " records from stream " + streamId);
                        // Pass raw values to coprocessors that are not requesting values to be extracted.
                        for (final Event event : events) {
                            receiver.getValuesConsumer().accept(event.getValues());
                        }
                    } finally {
                        tracker.incrementTasksCompleted();
                        testComplete();
                    }
                };
            }

            taskQueue.offer(consumer);
            tasksCreated.incrementAndGet();
        });
    }

    private long[] createEventIdArray(final List<Event> events,
                                      final Map<DocRef, ExtractionReceiver> receivers) {
        // If we don't have any coprocessors that will perform extraction then don't bother sorting events.
        if (receivers.size() == 0 ||
                (receivers.size() == 1 && receivers.keySet().iterator().next() == null)) {
            return null;
        }

        // Get a list of the event ids we are extracting for this stream and sort them.
        final long[] eventIds = new long[events.size()];
        for (int i = 0; i < eventIds.length; i++) {
            eventIds[i] = events.get(i).getEventId();
        }
        // Sort the ids as the extraction expects them in order.
        Arrays.sort(eventIds);
        return eventIds;
    }

    private void testComplete() {
        if (isComplete()) {
            receivers.forEach((docRef, receiver) ->
                    receiver.getCompletionConsumer().accept(indexSearchTotalValues.get()));
        }
    }

    @Override
    public String toString() {
        return "ExtractionTaskProducer{" +
                "tracker=" + tracker +
                '}';
    }
}
