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

package stroom.explorer.impl;

import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ExplorerTreeModel {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerTreeModel.class);

    private static final long ONE_HOUR = 60 * 60 * 1000;
    private static final long TEN_MINUTES = 10 * 60 * 1000;

    private final ExplorerTreeDao explorerTreeDao;
    private final ExplorerSession explorerSession;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerActionHandlers explorerActionHandlers;

    private volatile TreeModel currentModel;
    private final AtomicLong minExplorerTreeModelBuildTime = new AtomicLong();
    private final AtomicLong currentId = new AtomicLong();
    private final AtomicInteger performingRebuild = new AtomicInteger();

    @Inject
    ExplorerTreeModel(final ExplorerTreeDao explorerTreeDao,
                      final ExplorerSession explorerSession,
                      final Executor executor,
                      final TaskContextFactory taskContextFactory,
                      final ExplorerActionHandlers explorerActionHandlers) {
        this.explorerTreeDao = explorerTreeDao;
        this.explorerSession = explorerSession;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.explorerActionHandlers = explorerActionHandlers;
    }

    TreeModel getModel() {
        final long currentId = this.currentId.get();
        final long now = System.currentTimeMillis();

        // Force synchronous rebuild of the tree model if it is older than the minimum build time for the current
        // session.
        final long minId = explorerSession.getMinExplorerTreeModelId().orElse(0L);
        final long oneHourAgo = now - ONE_HOUR;

        LOGGER.debug("currentId: {}, minId: {}", currentId, minId);

        final TreeModel model;

        // Create a model synchronously if it is currently null or hasn't been rebuilt for an hour or is old for the
        // current session.
        if (currentModel == null ||
                currentModel.getId() < minId ||
                currentModel.getCreationTime() < oneHourAgo) {
            LOGGER.debug("Synchronous model build");
            model = updateModel(currentId, now);

        } else {
            // If the model has not been rebuilt in the last 10 minutes for anybody then do so asynchronously.
            // Find out what the oldest tree model is that we will allow before performing an asynchronous rebuild.
            final long oldestAllowed = Math.max(minExplorerTreeModelBuildTime.get(), now - TEN_MINUTES);
            LOGGER.debug(() -> LogUtil.message("oldestAllowed: {}, model createTime: {}",
                    Instant.ofEpochMilli(oldestAllowed),
                    Instant.ofEpochMilli(currentModel.getCreationTime())));

            if (currentModel.getCreationTime() < oldestAllowed) {
                // Perform a build asynchronously if we aren't already building elsewhere.
                if (performingRebuild.compareAndSet(0, 1)) {
                    try {
                        LOGGER.debug("Creating async rebuild task");
                        final Runnable runnable = taskContextFactory.context("Update Explorer Tree Model",
                                taskContext -> {
                                    LOGGER.debug("Running async model rebuild");
                                    updateModel(currentId, now);
                                });
                        CompletableFuture
                                .runAsync(runnable, executor)
                                .thenRun(performingRebuild::decrementAndGet)
                                .exceptionally(t -> {
                                    performingRebuild.decrementAndGet();
                                    return null;
                                });
                    } catch (final RuntimeException e) {
                        performingRebuild.decrementAndGet();
                    }
                }
            }
            model = currentModel;
        }

        return model;
    }

    private TreeModel updateModel(final long id, final long creationTime) {
        TreeModel newModel;
        performingRebuild.incrementAndGet();
        try {
            newModel = explorerTreeDao.createModel(this::getIconClassName, id, creationTime);

            // Sort children.
            newModel.sort(this::getPriority);

            setCurrentModel(newModel);
        } finally {
            performingRebuild.decrementAndGet();
        }
        return newModel;
    }

    private String getIconClassName(final String type) {
        final DocumentType documentType = explorerActionHandlers.getType(type);
        if (documentType == null) {
            return null;
        }

        return documentType.getIconClassName();
    }

    private int getPriority(final ExplorerNode node) {
        final DocumentType documentType = explorerActionHandlers.getType(node.getType());
        if (documentType == null) {
            return Integer.MAX_VALUE;
        }

        return documentType.getGroup().getPriority();
    }

    private synchronized void setCurrentModel(final TreeModel treeModel) {
        if (currentModel == null || currentModel.getId() < treeModel.getId()) {

            LOGGER.debug(() -> LogUtil.message("Setting new model old id: {}, new id {}",
                    currentModel == null
                            ? "null"
                            : currentModel.getId(),
                    treeModel.getId()));

            currentModel = treeModel;
        }
    }

    void rebuild() {
        LOGGER.debug("rebuild called");
        final long now = System.currentTimeMillis();
        minExplorerTreeModelBuildTime.getAndUpdate(prev -> Math.max(prev, now));
        explorerSession.setMinExplorerTreeModelId(currentId.incrementAndGet());
    }

    void clear() {
        setCurrentModel(null);
    }
}
