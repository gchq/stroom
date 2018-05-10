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

import stroom.query.Payload;
import stroom.query.ResultHandler;
import stroom.query.ResultStore;
import stroom.query.SearchResultCollector;
import stroom.query.shared.CoprocessorSettings;
import stroom.query.shared.Search;
import stroom.security.SecurityContext;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskTerminatedException;
import stroom.util.logging.StroomLogger;

import javax.inject.Provider;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StatStoreSearchResultCollector implements SearchResultCollector {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StatStoreSearchResultCollector.class);

    private final String userToken;
    private final TaskManager taskManager;
    private final TaskContext taskContext;
    private final String searchName;
    private final Search search;
    private final StatisticStoreEntity entity;
    private final Map<Integer, CoprocessorSettings> coprocessorMap;
    private final Provider<StatStoreSearchTaskHandler> statStoreSearchTaskHandlerProvider;
    private final ResultHandler resultHandler;
    private final Set<String> errors = new HashSet<>();

    public StatStoreSearchResultCollector(final String userToken,
                                          final TaskManager taskManager,
                                          final TaskContext taskContext,
                                          final String searchName,
                                          final Search search,
                                          final StatisticStoreEntity entity,
                                          final Map<Integer, CoprocessorSettings> coprocessorMap,
                                          final Provider<StatStoreSearchTaskHandler> statStoreSearchTaskHandlerProvider,
                                          final ResultHandler resultHandler) {
        this.userToken = userToken;
        this.taskManager = taskManager;
        this.taskContext = taskContext;
        this.searchName = searchName;
        this.search = search;
        this.entity = entity;
        this.coprocessorMap = coprocessorMap;
        this.statStoreSearchTaskHandlerProvider = statStoreSearchTaskHandlerProvider;
        this.resultHandler = resultHandler;
    }

    @Override
    public void start() {
        // Start asynchronous search execution.
        final GenericServerTask genericServerTask = GenericServerTask.create(null, userToken, "Stat Store Search", null);
        final Runnable runnable = () -> {
            try {
                final StatStoreSearchTaskHandler statStoreSearchTaskHandler = statStoreSearchTaskHandlerProvider.get();
                statStoreSearchTaskHandler.exec(searchName, search, entity, coprocessorMap, StatStoreSearchResultCollector.this);
            } catch (final RuntimeException e) {
                resultHandler.setComplete(true);

                // We can expect some tasks to throw a task terminated exception
                // as they may be terminated before we even try to execute them.
                if (!(e instanceof TaskTerminatedException)) {
                    LOGGER.error(e.getMessage(), e);
                    addError(e.getMessage());
                    resultHandler.setComplete(true);
                    throw e;
                }
            } finally {
                genericServerTask.setRunnable(null);
            }
        };
        genericServerTask.setRunnable(runnable);
        taskManager.execAsync(genericServerTask);
    }

    @Override
    public void destroy() {
        taskContext.terminate();
    }

    @Override
    public boolean isComplete() {
        return resultHandler.isComplete();
    }

    public void handle(final Map<Integer, Payload> payloadMap) {
        if (payloadMap != null && !payloadMap.isEmpty()) {
            resultHandler.handle(payloadMap, taskContext);
        }
    }

    @Override
    public ResultStore getResultStore(final String componentId) {
        return resultHandler.getResultStore(componentId);
    }

    public synchronized void addError(final String error) {
        errors.add(error);
    }

    @Override
    public synchronized String getErrors() {
        final StringBuilder sb = new StringBuilder();
        for (final String error : errors) {
            sb.append(error);
            sb.append("\n");
        }

        // Remove last new line.
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    @Override
    public Set<String> getHighlights() {
        return null;
    }

    public ResultHandler getResultHandler() {
        return resultHandler;
    }
}
