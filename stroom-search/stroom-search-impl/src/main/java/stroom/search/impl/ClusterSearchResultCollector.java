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

import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CompletionStateImpl;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.NodeResultSerialiser;
import stroom.query.common.v2.Store;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.io.StreamUtil;

import com.esotericsoftware.kryo.io.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Provider;

public class ClusterSearchResultCollector implements Store {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchResultCollector.class);
    private static final String TASK_NAME = "AsyncSearchTask";

    private final ConcurrentHashMap<String, Set<String>> errors = new ConcurrentHashMap<>();
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider;
    private final AsyncSearchTask task;
    private final String nodeName;
    private final Set<String> highlights;
    private final Coprocessors coprocessors;
    private final CompletionState completionState = new CompletionStateImpl();

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
            // Don't begin execution if we have been asked to complete already.
            if (!coprocessors.getCompletionState().isComplete()) {
                final AsyncSearchTaskHandler asyncSearchTaskHandler = asyncSearchTaskHandlerProvider.get();
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
                            LOGGER.error(t.getMessage(), t);
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
        coprocessors.clear();
    }

    public void complete() {
        completionState.signalComplete();
    }

    @Override
    public boolean isComplete() {
        return completionState.isComplete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        completionState.awaitCompletion();
    }

    @Override
    public boolean awaitCompletion(final long timeout,
                                   final TimeUnit unit) throws InterruptedException {
        return completionState.awaitCompletion(timeout, unit);
    }

    public synchronized boolean onSuccess(final String nodeName,
                                          final InputStream inputStream) {
        final AtomicBoolean complete = new AtomicBoolean();

        boolean success = true;

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamUtil.streamToStream(inputStream, byteArrayOutputStream);

        try (final Input input = new Input(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()))) {
            final Set<String> errors = new HashSet<>();
            success = NodeResultSerialiser.read(input, coprocessors, errors::add, complete::set);
            if (errors.size() > 0) {
                getErrorSet(nodeName).addAll(errors);
            }
        } catch (final RuntimeException e) {
            onFailure(nodeName, e);
        }

        // If we were told this payload belongs to a completed node then wait for this payload to be added.
        if (complete.get()) {
            try {
                boolean consumed = false;
                while (!consumed && !Thread.currentThread().isInterrupted()) {
                    consumed = coprocessors.awaitTransfer(1, TimeUnit.MINUTES);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug(e.getMessage(), e);
            }

            return true;
        }

        // If the result collector rejected the result it is because we have already collected enough data and can
        // therefore consider search complete.
        return !success;
    }

    public synchronized void onFailure(final String nodeName,
                                       final Throwable throwable) {
        getErrorSet(nodeName).add(throwable.getMessage());
    }

    private Set<String> getErrorSet(final String nodeName) {
        Set<String> errorSet = errors.get(nodeName);
        if (errorSet == null) {
            errorSet = new HashSet<>();
            final Set<String> existing = errors.putIfAbsent(nodeName, errorSet);
            if (existing != null) {
                errorSet = existing;
            }
        }
        return errorSet;
    }

    @Override
    public List<String> getErrors() {
        if (errors.size() == 0) {
            return null;
        }

        final List<String> err = new ArrayList<>();
        for (final Entry<String, Set<String>> entry : errors.entrySet()) {
            final String nodeName = entry.getKey();
            final Set<String> errors = entry.getValue();

            if (errors.size() > 0) {
                err.add("Node: " + nodeName);

                for (final String error : errors) {
                    err.add("\t" + error);
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
