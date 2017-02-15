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

package stroom.statistics.server.common.search;

import stroom.query.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.Data;
import stroom.query.Payload;
import stroom.query.ResultHandler;
import stroom.query.Store;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskTerminatedException;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StatisticsSearchStore implements Store {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StatisticsSearchStore.class);

    private final TaskManager taskManager;
    private final Task<VoidResult> task;
    private final ResultHandler resultHandler;
    private final Set<String> errors = new HashSet<>();

    public StatisticsSearchStore(final TaskManager taskManager, final Task<VoidResult> task,
                                 final ResultHandler resultHandler) {
        this.taskManager = taskManager;
        this.task = task;
        this.resultHandler = resultHandler;
    }

    public void start() {
        // Start asynchronous search execution.
        taskManager.execAsync(task, new TaskCallback<VoidResult>() {
            @Override
            public void onSuccess(final VoidResult result) {
                // Do nothing here as the results go into the collector.
            }

            @Override
            public void onFailure(final Throwable t) {
                // We can expect some tasks to throw a task terminated exception
                // as they may be terminated before we even try to execute them.
                if (!(t instanceof TaskTerminatedException)) {
                    LOGGER.error(t.getMessage(), t);
                    addError(t.getMessage());
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
    }

    @Override
    public boolean isComplete() {
        return resultHandler.isComplete();
    }

    public void handle(final Map<CoprocessorKey, Payload> payloadMap) {
        if (payloadMap != null) {
            resultHandler.handle(payloadMap, task);
        }
    }

    @Override
    public Data getData(final String componentId) {
        return resultHandler.getResultStore(componentId);
    }

    public synchronized void addError(final String error) {
        errors.add(error);
    }

    public synchronized String[] getErrors() {
        if (errors == null || errors.size() == 0) {
            return null;
        }

        return errors.toArray(new String[errors.size()]);
    }

    @Override
    public String[] getHighlights() {
        return null;
    }

    public ResultHandler getResultHandler() {
        return resultHandler;
    }
}
