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

package stroom.search.impl;

import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.NodeResult;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.impl.TaskContextImpl;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class RemoteSearchManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchManager.class);
    private final RemoteSearchResults remoteSearchResults;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final TaskManager taskManager;
    private final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider;

    @Inject
    RemoteSearchManager(final RemoteSearchResults remoteSearchResults,
                        final SecurityContext securityContext,
                        final ExecutorProvider executorProvider,
                        final TaskContextFactory taskContextFactory,
                        final TaskManager taskManager,
                        final Provider<ClusterSearchTaskHandler> clusterSearchTaskHandlerProvider) {
        this.remoteSearchResults = remoteSearchResults;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.taskManager = taskManager;
        this.clusterSearchTaskHandlerProvider = clusterSearchTaskHandlerProvider;
    }

    public Boolean startSearch(final UserIdentity userIdentity, final TaskId sourceTaskId, final ClusterSearchTask clusterSearchTask) {
        LOGGER.debug(() -> "startSearch " + clusterSearchTask);
        return securityContext.asUserResult(userIdentity, () -> {
            final TaskContext parentContext = new TaskContextImpl(sourceTaskId, "Cluster Search Task", userIdentity);

            final RemoteSearchResultFactory remoteSearchResultFactory = new RemoteSearchResultFactory(taskManager);
            remoteSearchResults.put(clusterSearchTask.getKey(), remoteSearchResultFactory);

            final Runnable runnable = taskContextFactory.context(parentContext, clusterSearchTask.getTaskName(), taskContext -> {
                final ClusterSearchTaskHandler clusterSearchTaskHandler = clusterSearchTaskHandlerProvider.get();
                clusterSearchTaskHandler.exec(taskContext, clusterSearchTask, remoteSearchResultFactory);
            });

            final Executor executor = executorProvider.get();
            CompletableFuture.runAsync(runnable, executor);

            return true;
        });
    }

    public NodeResult poll(final UserIdentity userIdentity, final QueryKey key) {
        LOGGER.debug(() -> "poll " + key);
        return securityContext.asUserResult(userIdentity, () -> {
            final Optional<RemoteSearchResultFactory> factory = remoteSearchResults.get(key);
            return factory.map(RemoteSearchResultFactory::create).orElse(null);
        });
    }

    public Boolean destroy(final UserIdentity userIdentity, final QueryKey key) {
        LOGGER.debug(() -> "destroy " + key);
        return securityContext.asUserResult(userIdentity, () -> {
            remoteSearchResults.invalidate(key);
            return true;
        });
    }
}
