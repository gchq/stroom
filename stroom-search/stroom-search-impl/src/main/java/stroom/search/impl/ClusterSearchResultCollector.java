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

package stroom.search.impl;

import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ErrorConsumerUtil;
import stroom.query.common.v2.NodeResultSerialiser;
import stroom.query.common.v2.Store;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Provider;

public class ClusterSearchResultCollector implements Store {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchResultCollector.class);
    private static final String TASK_NAME = "AsyncSearchTask";

    private final ConcurrentHashMap<String, Set<Throwable>> errors = new ConcurrentHashMap<>();
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider;
    private final AsyncSearchTask task;
    private final String nodeName;
    private final Set<String> highlights;
    private final Coprocessors coprocessors;

    private volatile AsyncSearchTaskHandler asyncSearchTaskHandler;
    private volatile TaskContext taskContext;
    private volatile boolean complete;

    public ClusterSearchResultCollector(final Executor executor,
                                        final TaskContextFactory taskContextFactory,
                                        final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider,
                                        final AsyncSearchTask task,
                                        final String nodeName,
                                        final Set<String> highlights,
                                        final Coprocessors coprocessors) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.asyncSearchTaskHandlerProvider = asyncSearchTaskHandlerProvider;
        this.task = task;
        this.nodeName = nodeName;
        this.highlights = highlights;
        this.coprocessors = coprocessors;
    }

    public Coprocessors getCoprocessors() {
        return coprocessors;
    }

    public void start() {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            this.taskContext = taskContext;
            this.asyncSearchTaskHandler = asyncSearchTaskHandlerProvider.get();

            // Don't begin execution if we have been asked to complete already.
            if (!complete) {
                asyncSearchTaskHandler.exec(taskContext, task);
            }
        });
        CompletableFuture
                .runAsync(runnable, executor)
                .whenComplete((result, t) -> {
                    if (t != null) {
                        while (t instanceof CompletionException) {
                            t = t.getCause();
                        }

                        // We can expect some tasks to throw a task terminated exception
                        // as they may be terminated before we even try to execute them.
                        if (!(t instanceof TaskTerminatedException)) {
                            LOGGER.error(t::getMessage, t);
                            onFailure(nodeName, t);
                            coprocessors.getCompletionState().signalComplete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        coprocessors.getCompletionState().signalComplete();
                    }
                });
    }

    @Override
    public void destroy() {
        LOGGER.trace(() -> "destroy()", new RuntimeException("destroy"));
        complete = true;
        if (asyncSearchTaskHandler != null) {
            asyncSearchTaskHandler.terminateTasks(task, taskContext.getTaskId());
        }

        LOGGER.trace(() -> "coprocessors.clear()");
        coprocessors.clear();
    }

    public void complete() {
        LOGGER.trace(() -> "complete()");
        coprocessors.getCompletionState().signalComplete();
    }

    @Override
    public boolean isComplete() {
        return coprocessors.getCompletionState().isComplete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        coprocessors.getCompletionState().awaitCompletion();
    }

    @Override
    public boolean awaitCompletion(final long timeout,
                                   final TimeUnit unit) throws InterruptedException {
        return coprocessors.getCompletionState().awaitCompletion(timeout, unit);
    }

    public synchronized boolean onSuccess(final String nodeName,
                                          final InputStream inputStream) {
        // If we have already completed the finish immediately without worrying about this data.
        if (isComplete()) {
            return true;
        }
        boolean complete = true;

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamUtil.streamToStream(inputStream, byteArrayOutputStream);

        try (final Input input = new Input(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            final Set<Throwable> errors = new HashSet<>();
            final Consumer<Throwable> errorConsumer = (error) -> {
                LOGGER.debug(error::getMessage, error);
                if (!ErrorConsumerUtil.isInterruption(error)) {
                    errors.add(error);
                }
            };

            complete = NodeResultSerialiser.read(input, coprocessors, errorConsumer);
            addErrors(nodeName, errors);
        } catch (final KryoException e) {
            // Expected as sometimes the output stream is closed by the receiving node.
            LOGGER.debug(e::getMessage, e);
        } catch (final RuntimeException e) {
            onFailure(nodeName, e);
        }

        // If the result collector rejected the result it is because we have already collected enough data and can
        // therefore consider search complete.
        return isComplete() || complete;
    }

    public synchronized void onFailure(final String nodeName,
                                       final Throwable throwable) {
        LOGGER.debug(throwable::getMessage, throwable);
        if (!ErrorConsumerUtil.isInterruption(throwable)) {
            addErrors(nodeName, Collections.singleton(throwable));
        }
    }

    private void addErrors(final String nodeName,
                           final Set<Throwable> newErrors) {
        if (newErrors != null && newErrors.size() > 0) {
            final Set<Throwable> errorSet = errors.computeIfAbsent(nodeName, k ->
                    Collections.newSetFromMap(new ConcurrentHashMap<>()));
            for (final Throwable error : newErrors) {
                LOGGER.debug(error::getMessage, error);
                errorSet.add(error);
            }
        }
    }

    @Override
    public List<String> getErrors() {
        if (errors.size() == 0) {
            return null;
        }

        final List<String> err = new ArrayList<>();
        for (final Entry<String, Set<Throwable>> entry : errors.entrySet()) {
            final String nodeName = entry.getKey();
            final Set<Throwable> errors = entry.getValue();

            if (errors.size() > 0) {
                err.add("Node: " + nodeName);

                for (final Throwable error : errors) {
                    err.add("\t" + ExceptionStringUtil.getMessage(error));
                }
            }
        }

        return err;
    }

    @Override
    public List<String> getHighlights() {
        if (highlights == null || highlights.size() == 0) {
            return null;
        }
        return new ArrayList<>(highlights);
    }

    @Override
    public DataStore getData(final String componentId) {
        return coprocessors.getData(componentId);
    }

    @Override
    public String toString() {
        return "ClusterSearchResultCollector{" +
                "task=" + task +
                ", complete=" + coprocessors.getCompletionState() +
                '}';
    }
}
