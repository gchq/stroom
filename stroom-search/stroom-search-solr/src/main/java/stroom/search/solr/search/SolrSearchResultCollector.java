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

package stroom.search.solr.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.query.common.v2.CompletionListener;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.search.resultsender.NodeResult;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskTerminatedException;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SolrSearchResultCollector implements Store, CompletionListener, TaskCallback<NodeResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchResultCollector.class);

    public static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";

    private final Set<String> errors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final TaskManager taskManager;
    private final Task<VoidResult> task;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;
    private final Sizes defaultMaxResultsSizes;
    private final Sizes storeSize;

    private volatile boolean terminated;

    private final Queue<CompletionListener> completionListeners = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> changeListeners = new ConcurrentLinkedQueue<>();

    private SolrSearchResultCollector(final TaskManager taskManager,
                                      final Task<VoidResult> task,
                                      final Set<String> highlights,
                                      final ResultHandler resultHandler,
                                      final Sizes defaultMaxResultsSizes,
                                      final Sizes storeSize) {
        this.taskManager = taskManager;
        this.task = task;
        this.highlights = highlights;
        this.resultHandler = resultHandler;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.resultHandler.registerCompletionListener(this);
    }

    public static SolrSearchResultCollector create(final TaskManager taskManager,
                                                   final Task<VoidResult> task,
                                                   final Set<String> highlights,
                                                   final ResultHandler resultHandler,
                                                   final Sizes defaultMaxResultsSizes,
                                                   final Sizes storeSize) {
        return new SolrSearchResultCollector(taskManager, task, highlights,
                resultHandler, defaultMaxResultsSizes, storeSize);
    }

    public void start() {
        // Start asynchronous search execution.
        taskManager.execAsync(task, new TaskCallback<VoidResult>() {
            @Override
            public void onSuccess(final VoidResult result) {
                // Do nothing here as the results go into the collector
            }

            @Override
            public void onFailure(final Throwable t) {
                // We can expect some tasks to throw a task terminated exception
                // as they may be terminated before we even try to execute them.
                if (!(t instanceof TaskTerminatedException)) {
                    LOGGER.error(t.getMessage(), t);
                    getErrorSet().add(t.getMessage());
                    resultHandler.setComplete(true);
                    throw new RuntimeException(t.getMessage(), t);
                }

                resultHandler.setComplete(true);
            }
        });
    }

    @Override
    public void destroy() {
        task.terminate();
        completionListeners.clear();
    }

    @Override
    public boolean isComplete() {
        return terminated || resultHandler.isComplete();
    }

    @Override
    public void onSuccess(final NodeResult result) {
        final Map<CoprocessorKey, Payload> payloadMap = result.getPayloadMap();
        final List<String> errors = result.getErrors();

        if (payloadMap != null) {
            resultHandler.handle(payloadMap, task);
        }
        if (errors != null) {
            getErrorSet().addAll(errors);
        }
        if (result.isComplete()) {
            resultHandler.setComplete(true);
        }
        notifyListenersOfChange();
    }

    @Override
    public void onFailure(final Throwable throwable) {
        resultHandler.setComplete(true);
        errors.add(throwable.getMessage());
        notifyListenersOfChange();
    }

    public void terminate() {
        terminated = true;
        notifyListenersOfCompletion();
        notifyListenersOfChange();
    }

    public Set<String> getErrorSet() {
        return errors;
    }

    @Override
    public List<String> getErrors() {
        if (errors.size() == 0) {
            return null;
        }

        final List<String> err = new ArrayList<>();
        for (final String error : errors) {
            err.add("\t" + error);
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
    public Sizes getDefaultMaxResultsSizes() {
        return defaultMaxResultsSizes;
    }

    @Override
    public Sizes getStoreSize() {
        return storeSize;
    }


    @Override
    public Data getData(final String componentId) {
        return resultHandler.getResultStore(componentId);
    }

    public ResultHandler getResultHandler() {
        return resultHandler;
    }

    @Override
    public void registerCompletionListener(final CompletionListener completionListener) {
        if (isComplete()) {
            //immediate notification
            completionListener.onCompletion();
        } else {
            completionListeners.add(Objects.requireNonNull(completionListener));
        }
    }

    @Override
    public void onCompletion() {
        //fired on completion of the resultHandler that we registered an in interest in
        //'this' is both a completion listener (on the ResultHandler) and has completionListeners of its own
        notifyListenersOfCompletion();
    }

    private void notifyListenersOfCompletion() {
        //Call isComplete to ensure we are complete and not terminated
        if (isComplete()) {
            for (CompletionListener listener; (listener = completionListeners.poll()) != null; ) {
                //when notified they will check isComplete
                LOGGER.debug("Notifying {} {} that we are complete", listener.getClass().getName(), listener);
                listener.onCompletion();
            }
        }
    }

    public void registerChangeListner(final Runnable changeListener) {
        changeListeners.add(Objects.requireNonNull(changeListener));
        notifyListenersOfChange();
    }

    private void notifyListenersOfChange() {
        changeListeners.forEach(Runnable::run);
    }

    @Override
    public String toString() {
        return "ClusterSearchResultCollector{" +
                "task=" + task +
                ", terminated=" + terminated +
                '}';
    }
}
