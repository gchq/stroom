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
import stroom.docref.DocRef;
import stroom.query.common.v2.CompletionState;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.Receiver;
import stroom.search.coprocessor.ReceiverImpl;
import stroom.search.coprocessor.Values;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskExecutor;
import stroom.task.api.TaskProducer;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class ExtractionTaskProducer extends TaskProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionTaskProducer.class);
    private static final String TASK_NAME = "Extraction";

    private final Receiver parentReceiver;
    private final Map<DocRef, Receiver> receivers;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Queue<ExtractionRunnable> taskQueue = new ConcurrentLinkedQueue<>();

    private final CompletionState streamMapCreatorCompletionState = new CompletionState();
    private final Map<Long, List<Event>> streamEventMap = new ConcurrentHashMap<>();
    private final Topic<Values> topic;
    private final ExtractionProgressTracker tracker;

    ExtractionTaskProducer(final TaskExecutor taskExecutor,
                           final StreamMapCreator streamMapCreator,
                           final Receiver parentReceiver,
                           final Map<DocRef, Receiver> receivers,
                           final int maxStoredDataQueueSize,
                           final int maxThreadsPerTask,
                           final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory,
                           final TaskContext parentContext,
                           final Provider<ExtractionTaskHandler> handlerProvider,
                           final SecurityContext securityContext,
                           final ExtractionProgressTracker tracker) {
        super(taskExecutor, maxThreadsPerTask, taskContextFactory, parentContext, TASK_NAME);
        this.parentReceiver = parentReceiver;
        this.receivers = receivers;
        this.handlerProvider = handlerProvider;
        this.tracker = tracker;

        // Create a queue to receive values and store them for asynchronous processing.
        topic = new LinkedBlockingQueueTopic<>(maxStoredDataQueueSize);

//        // Group coprocessors by extraction pipeline.
//        final Map<DocRef, Set<NewCoprocessor>> map = new HashMap<>();
//        coprocessors.getSet().forEach(coprocessor ->
//                map.computeIfAbsent(coprocessor.getSettings().getExtractionPipeline(), k ->
//                        new HashSet<>()).add(coprocessor));
//
//        receiverMap = map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> {
//            Set<NewCoprocessor> coprocessorSet = e.getValue();
//
//            // Create a receiver that will send data to all coprocessors.
//            Receiver receiver;
//            if (e.getValue().size() == 1) {
//                receiver = coprocessorSet.iterator().next();
//            } else {
//                receiver = new MultiReceiver(coprocessorSet);
//            }
//            return receiver;
//        }));

        // Start mapping streams.
        final Consumer<TaskContext> consumer = tc -> {
            // Elevate permissions so users with only `Use` feed permission can `Read` streams.
            securityContext.asProcessingUser(() -> {
                LOGGER.debug("Starting extraction task producer");

                try {
                    while (!streamMapCreatorCompletionState.isComplete() && !Thread.currentThread().isInterrupted()) {
                        try {
                            // Poll for the next set of values.
                            final Values values = topic.get();
                            if (values != null) {
                                try {
                                    // If we have some values then map them.
                                    streamMapCreator.addEvent(streamEventMap, values.getValues());
                                } catch (final RuntimeException e) {
                                    LOGGER.debug(e.getMessage(), e);
                                    receivers.values().forEach(receiver -> {
                                        receiver.getErrorConsumer().accept(new Error(e.getMessage(), e));
                                        receiver.getCompletionCountConsumer().accept(1L);
                                    });
                                }
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.debug(e.getMessage(), e);
                        } finally {
                            // Tell the supplied executor that we are ready to deliver tasks.
                            signalAvailable();
                        }
                    }

                    // Clear the event map if we have terminated so that other processing does not occur.
                    if (Thread.currentThread().isInterrupted()) {
                        streamEventMap.clear();
                    }

                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    streamMapCreatorCompletionState.complete();

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

        return new ReceiverImpl(topic, parentReceiver.getErrorConsumer(), parentReceiver.getCompletionCountConsumer(), parentReceiver.getFieldIndexMap());
    }

    protected boolean isComplete() {
        return Thread.currentThread().isInterrupted() || tracker.isComplete();
    }

    @Override
    protected Consumer<TaskContext> getNext() {
        ExtractionRunnable task = null;

        if (!isComplete()) {
            task = taskQueue.poll();
            if (task == null) {
                if (addTasks()) {
                    tracker.finishedAddingTasks();
                }
                task = taskQueue.poll();
            }
        }

        return task;
    }

    private boolean addTasks() {
        final boolean completedEventMapping = this.streamMapCreatorCompletionState.isComplete();
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
        final AtomicInteger tasksCreated = new AtomicInteger();

        final long[] eventIds = createEventIdArray(events, receivers);
        receivers.forEach((docRef, receiver) -> {
            if (docRef != null) {
                tracker.incrementTasksTotal();
                final ExtractionTask task = new ExtractionTask(streamId, eventIds, docRef, receiver);
                taskQueue.offer(new ExtractionRunnable(task, handlerProvider, tracker));
                tasksCreated.incrementAndGet();

            } else {
                // Pass raw values to coprocessors that are not requesting values to be extracted.
                for (final Event event : events) {
                    receiver.getValuesConsumer().accept(event.getValues());
                }
                receiver.getCompletionCountConsumer().accept((long) events.size());
            }
        });

        return tasksCreated.get();
    }

    private long[] createEventIdArray(final List<Event> events,
                                      final Map<DocRef, Receiver> receivers) {
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

    @Override
    public String toString() {
        return "ExtractionTaskProducer{" +
                "tracker=" + tracker +
                '}';
    }

    private static class ExtractionRunnable implements Consumer<TaskContext> {
        private final ExtractionTask task;
        private final Provider<ExtractionTaskHandler> handlerProvider;
        private final ExtractionProgressTracker tracker;

        ExtractionRunnable(final ExtractionTask task,
                           final Provider<ExtractionTaskHandler> handlerProvider,
                           final ExtractionProgressTracker tracker) {
            this.task = task;
            this.handlerProvider = handlerProvider;
            this.tracker = tracker;
        }

        @Override
        public void accept(final TaskContext taskContext) {
            try {
                final ExtractionTaskHandler handler = handlerProvider.get();
                handler.exec(taskContext, task);
            } finally {
                tracker.incrementTasksCompleted();
            }
        }

        public ExtractionTask getTask() {
            return task;
        }
    }
}
