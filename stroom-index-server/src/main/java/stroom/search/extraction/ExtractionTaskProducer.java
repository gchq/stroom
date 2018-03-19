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

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.query.api.v2.DocRef;
import stroom.query.common.v2.Coprocessor;
import stroom.search.ClusterSearchTask;
import stroom.search.Event;
import stroom.search.extraction.ExtractionTask.ResultReceiver;
import stroom.search.taskqueue.TaskExecutor;
import stroom.search.taskqueue.TaskProducer;
import stroom.task.ExecutorProvider;
import stroom.task.ThreadPoolImpl;
import stroom.util.shared.Severity;
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
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ExtractionTaskProducer extends TaskProducer {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Extraction", 5, 0, Integer.MAX_VALUE);

    private final ClusterSearchTask clusterSearchTask;
    private final FieldIndexMap extractionFieldIndexMap;
    private final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap;
    private final ErrorReceiver errorReceiver;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Queue<ExtractionRunnable> taskQueue = new ConcurrentLinkedQueue<>();

    private final CompletableFuture<Void> streamEventMapperCompletableFuture;
    private final Map<Long, List<Event>> streamEventMap = new ConcurrentHashMap<>();

    private volatile boolean finishedAddingTasks;

    public ExtractionTaskProducer(final TaskExecutor taskExecutor,
                                  final ClusterSearchTask clusterSearchTask,
                                  final StreamMapCreator streamMapCreator,
                                  final LinkedBlockingQueue<Optional<String[]>> storedData,
                                  final FieldIndexMap extractionFieldIndexMap,
                                  final Map<DocRef, Set<Coprocessor>> extractionCoprocessorsMap,
                                  final ErrorReceiver errorReceiver,
                                  final int maxThreadsPerTask,
                                  final ExecutorProvider executorProvider,
                                  final Provider<ExtractionTaskHandler> handlerProvider,
                                  final TaskProducer searchTaskProducer) {
        super(taskExecutor, maxThreadsPerTask, executorProvider.getExecutor(THREAD_POOL));
        this.clusterSearchTask = clusterSearchTask;
        this.extractionFieldIndexMap = extractionFieldIndexMap;
        this.extractionCoprocessorsMap = extractionCoprocessorsMap;
        this.errorReceiver = errorReceiver;
        this.handlerProvider = handlerProvider;

        // Start mapping streams.
        final Executor executor = executorProvider.getExecutor(THREAD_POOL);
        streamEventMapperCompletableFuture = CompletableFuture.runAsync(() -> {
            try {
                boolean complete = false;
                while (!complete && !clusterSearchTask.isTerminated()) {
                    // Check if search is finished before attempting to add to the stream map.
                    final boolean searchFinished = searchTaskProducer.isComplete();
                    // Poll for the next set of values.
                    final Optional<String[]> values = storedData.poll(1, TimeUnit.SECONDS);

                    if (values != null) {
                        if (values.isPresent()) {
                            // If we have some values then map them.
                            streamMapCreator.addEvent(streamEventMap, values.get());
                        } else {
                            complete = true;
                        }

                        // Tell the supplied executor that we are ready to deliver tasks.
                        signalAvailable();
                    } else {
                        // If we did not get any values then there are no more to get if the search task producer is complete.
                        complete = searchFinished;
                    }
                }

                // Clear the event map if we have terminated so that other processing does not occur.
                if (clusterSearchTask.isTerminated()) {
                    streamEventMap.clear();
                }

                // Tell the supplied executor that we are ready to deliver final tasks.
                signalAvailable();

            } catch (final Throwable t) {
                error(t.getMessage(), t);
            }
        }, executor);

        // Attach to the supplied executor.
        attach();

        // Tell the supplied executor that we are ready to deliver tasks.
        signalAvailable();
    }

    @Override
    public boolean isComplete() {
        // If we haven't finished mapping all of the streams then we aren't complete.
        return clusterSearchTask.isTerminated() || (finishedAddingTasks && super.isComplete());
    }

    @Override
    protected Runnable getNext() {
        ExtractionRunnable task = null;

        if (clusterSearchTask.isTerminated()) {
            finishedAddingTasks = true;

            // Drain the queue and increment the complete task count.
            while (taskQueue.poll() != null) {
                getTasksCompleted().getAndIncrement();
            }
        } else {
            task = taskQueue.poll();
            if (task == null) {
                finishedAddingTasks = addTasks();
                task = taskQueue.poll();
            }
        }

        return task;
    }

    private boolean addTasks() {
        final boolean completedEventMapping = streamEventMapperCompletableFuture.isDone();
        for (final Entry<Long, List<Event>> entry : streamEventMap.entrySet()) {
            if (streamEventMap.remove(entry.getKey(), entry.getValue())) {
                createTasks(entry.getKey(), entry.getValue());
                return false;
            }
        }
        return completedEventMapping;
    }

    private void createTasks(final long streamId, final List<Event> events) {
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
                        } catch (final Exception e) {
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

                getTasksTotal().incrementAndGet();
                final ExtractionTask task = new ExtractionTask(streamId, eventIds, pipelineRef, extractionFieldIndexMap, resultReceiver, errorReceiver);
                taskQueue.offer(new ExtractionRunnable(task, handlerProvider));

            } else {
                // Pass raw values to coprocessors that are not requesting values to be extracted.
                for (final Coprocessor coprocessor : coprocessors) {
                    for (final Event event : events) {
                        coprocessor.receive(event.getValues());
                    }
                }
            }
        }
    }

    private void error(final String message, final Throwable t) {
        errorReceiver.log(Severity.ERROR, null, null, message, t);
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
