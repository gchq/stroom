/*
 * Copyright 2016 Crown Copyright
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

package stroom.search.server.extraction;

import stroom.dashboard.expression.FieldIndexMap;
import stroom.entity.shared.DocRef;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.search.server.ClusterSearchTask;
import stroom.search.server.Coprocessor;
import stroom.search.server.Event;
import stroom.search.server.extraction.ExtractionTask.ResultReceiver;
import stroom.search.server.shard.TransferList;
import stroom.search.server.taskqueue.AbstractTaskProducer;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.ThreadPoolImpl;
import stroom.util.shared.Severity;
import stroom.util.shared.ThreadPool;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ExtractionTaskProducer extends AbstractTaskProducer {
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stroom Data Extraction", 5, 0, Integer.MAX_VALUE);

    private final ClusterSearchTask clusterSearchTask;
    private final StreamMapCreator streamMapCreator;
    private final TransferList<String[]> storedData;
    private final FieldIndexMap extractionFieldIndexMap;
    private final Map<DocRef, Set<Coprocessor<?>>> extractionCoprocessorsMap;
    private final ErrorReceiver errorReceiver;
    private final Provider<ExtractionTaskHandler> handlerProvider;

    private final Queue<ExtractionRunnable> taskQueue = new ConcurrentLinkedQueue<>();

    public ExtractionTaskProducer(final ClusterSearchTask clusterSearchTask,
                                  final StreamMapCreator streamMapCreator,
                                  final TransferList<String[]> storedData,
                                  final FieldIndexMap extractionFieldIndexMap,
                                  final Map<DocRef, Set<Coprocessor<?>>> extractionCoprocessorsMap,
                                  final ErrorReceiver errorReceiver,
                                  final int maxThreadsPerTask,
                                  final ExecutorProvider executorProvider,
                                  final Provider<ExtractionTaskHandler> handlerProvider) {
        super(maxThreadsPerTask, executorProvider.getExecutor(THREAD_POOL));
        this.clusterSearchTask = clusterSearchTask;
        this.streamMapCreator = streamMapCreator;
        this.storedData = storedData;
        this.extractionFieldIndexMap = extractionFieldIndexMap;
        this.extractionCoprocessorsMap = extractionCoprocessorsMap;
        this.errorReceiver = errorReceiver;
        this.handlerProvider = handlerProvider;
    }

    public boolean isComplete() {
        if (getTasksTotal().get() != getTasksCompleted().get()) {
            return false;
        }
        final int added = fillTaskQueue();
        return added == 0 && getTasksTotal().get() == getTasksCompleted().get();
    }

    public int remainingTasks() {
        return getTasksTotal().get() - getTasksCompleted().get();
    }

    private int fillTaskQueue() {
        synchronized (taskQueue) {
            final CompletableFuture<Integer> completableFuture = CompletableFuture.supplyAsync(() -> {
                int added = 0;
                final List<String[]> data = storedData.swap();
                if (data != null && data.size() > 0) {
                    final Map<Long, List<Event>> streamMap = streamMapCreator.createEventMap(data);
                    for (final Entry<Long, List<Event>> entry : streamMap.entrySet()) {
                        final Long streamId = entry.getKey();
                        final List<Event> events = entry.getValue();

                        added += createTasks(streamId, events);
                    }
                }

                return added;
            }, getExecutor());
            return completableFuture.join();
        }
    }

    private int createTasks(final long streamId, final List<Event> events) {
        int created = 0;
        long[] eventIds = null;

        for (final Entry<DocRef, Set<Coprocessor<?>>> entry : extractionCoprocessorsMap.entrySet()) {
            final DocRef pipelineRef = entry.getKey();
            final Set<Coprocessor<?>> coprocessors = entry.getValue();

            if (pipelineRef != null) {
                // This set of coprocessors require result extraction so invoke
                // the extraction service.
                final ResultReceiver resultReceiver = values -> {
                    for (final Coprocessor<?> coprocessor : coprocessors) {
                        try {
                            coprocessor.receive(values);
                        } catch (final Exception e) {
                            error(e.getMessage(), e);
                        }
                    }
                };

                if (eventIds == null) {
                    // Get a list of the event ids we are extracting for this
                    // stream and sort them.
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
                created++;

            } else {
                // Pass raw values to coprocessors that are not requesting
                // values to be extracted.
                for (final Coprocessor<?> coprocessor : coprocessors) {
                    for (final Event event : events) {
                        coprocessor.receive(event.getValues());
                    }
                }
            }
        }

        return created;
    }

    @Override
    protected Runnable getNext() {
        if (clusterSearchTask.isTerminated()) {
            return null;
        }

        ExtractionRunnable task = null;
        if (!clusterSearchTask.isTerminated()) {
            task = taskQueue.poll();
            if (task == null) {
                fillTaskQueue();
                task = taskQueue.poll();
            }
        }

        if (clusterSearchTask.isTerminated()) {
            return null;
        }

        return task;
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
