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

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.DELETE,
        EntityAction.CREATE_EXPLORER_NODE,
        EntityAction.UPDATE_EXPLORER_NODE,
        EntityAction.DELETE_EXPLORER_NODE})
class ExplorerTreeModel implements EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerTreeModel.class);

    private static final long ONE_HOUR = 60 * 60 * 1000;
    private static final long TEN_MINUTES = 10 * 60 * 1000;

    private final ExplorerTreeDao explorerTreeDao;
    private final ExplorerSession explorerSession;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final BrokenDependenciesCache brokenDependenciesCache;
    private final SecurityContext securityContext;
    private final Provider<ExplorerConfig> explorerConfigProvider;

    private volatile UnmodifiableTreeModel currentModel;
    private final AtomicLong minExplorerTreeModelBuildTime = new AtomicLong();
    private final AtomicLong currentId = new AtomicLong();
    private final AtomicInteger performingRebuild = new AtomicInteger();

    @Inject
    ExplorerTreeModel(final ExplorerTreeDao explorerTreeDao,
                      final ExplorerSession explorerSession,
                      final Executor executor,
                      final TaskContextFactory taskContextFactory,
                      final ExplorerActionHandlers explorerActionHandlers,
                      final BrokenDependenciesCache brokenDependenciesCache,
                      final SecurityContext securityContext,
                      final Provider<ExplorerConfig> explorerConfigProvider) {
        this.explorerTreeDao = explorerTreeDao;
        this.explorerSession = explorerSession;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.explorerActionHandlers = explorerActionHandlers;
        this.brokenDependenciesCache = brokenDependenciesCache;
        this.securityContext = securityContext;
        this.explorerConfigProvider = explorerConfigProvider;
    }

    private boolean isSynchronousUpdateRequired(final long minId, final long now) {
        return currentModel == null ||
               currentModel.getId() < minId ||
               currentModel.getCreationTime() < now - ONE_HOUR;
    }

    UnmodifiableTreeModel getModel() {
        final long currentId = this.currentId.get();
        final long now = System.currentTimeMillis();

        // Force synchronous rebuild of the tree model if it is older than the minimum build time for the current
        // session.
        long minId = explorerSession.getMinExplorerTreeModelId().orElse(0L);

        LOGGER.trace("getModel() - currentId: {}, minId: {}", currentId, minId);

        UnmodifiableTreeModel model = null;

        // Create a model synchronously if it is currently null or hasn't been rebuilt for an hour or is old for the
        // current session.
        if (isSynchronousUpdateRequired(minId, now)) {
            synchronized (this) {
                minId = explorerSession.getMinExplorerTreeModelId().orElse(0L);
                if (isSynchronousUpdateRequired(minId, now)) {
                    LOGGER.debug("Synchronous model build");
                    model = updateModel(currentId, now);
                } else {
                    LOGGER.debug("Another thread beat us, we can use their model");
                }
            }
        }

        if (model == null) {
            // If the model has not been rebuilt in the last 10 minutes for anybody then do so asynchronously.
            // Find out what the oldest tree model is that we will allow before performing an asynchronous rebuild.
            final long oldestAllowed = Math.max(minExplorerTreeModelBuildTime.get(), now - TEN_MINUTES);
            LOGGER.trace(() -> LogUtil.message("oldestAllowed: {}, model createTime: {}",
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

    private UnmodifiableTreeModel updateModel(final long id, final long creationTime) {
        return securityContext.asProcessingUserResult(() -> {
            final TreeModel newModel;
            final UnmodifiableTreeModel newUnmodifiableModel;
            performingRebuild.incrementAndGet();
            try {
                LOGGER.debug("Updating model for id {}", id);
                newModel = LOGGER.logDurationIfDebugEnabled(() ->
                                explorerTreeDao.createModel(id, creationTime),
                        "Create model");

                // Make sure the cache is fresh for our new model
                if (explorerConfigProvider.get().getDependencyWarningsEnabled()) {
                    brokenDependenciesCache.invalidate();
                    LOGGER.logDurationIfDebugEnabled(() ->
                                    addBrokenDependencies(newModel),
                            "Add dependencies to model");
                }

                // Now make it immutable as this is our master model
                newUnmodifiableModel = UnmodifiableTreeModel.wrap(newModel);
                setCurrentModel(newUnmodifiableModel);
            } finally {
                performingRebuild.decrementAndGet();
            }
            return newUnmodifiableModel;
        });
    }

    private void addBrokenDependencies(final TreeModel treeModel) {
        final Map<DocRef, Set<DocRef>> brokenDepsMap = NullSafe.map(brokenDependenciesCache.getMap());
        brokenDepsMap.forEach((nodeDocRef, missingDepDocRefs) -> {
            final ExplorerNode node = treeModel.getNode(nodeDocRef);
            if (node != null) {
                final List<NodeInfo> nodeInfos = buildNodeInfo(missingDepDocRefs);
                if (!nodeInfos.isEmpty()) {
                    treeModel.addNodeInfo(node, nodeInfos);
                }
            }
        });
    }

    private List<NodeInfo> buildNodeInfo(final Set<DocRef> brokenDeps) {
        if (NullSafe.hasItems(brokenDeps)) {
            return brokenDeps.stream()
                    .map(missingDocRef -> {
                        final String msg = LogUtil.message(
                                "Missing dependency to {} '{}' ({})",
                                missingDocRef.getType(),
                                missingDocRef.getName(),
                                missingDocRef.getUuid());
                        return new NodeInfo(Severity.ERROR, msg);
                    })
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private synchronized void setCurrentModel(final UnmodifiableTreeModel treeModel) {
        if (currentModel == null
            || treeModel == null
            || currentModel.getId() < treeModel.getId()) {

            LOGGER.debug(() -> LogUtil.message("Setting new model old id: {}, new id {}",
                    NullSafe.toStringOrElse(currentModel, UnmodifiableTreeModel::getId, "null"),
                    NullSafe.toStringOrElse(treeModel, UnmodifiableTreeModel::getId, "null")));
            currentModel = treeModel;
        }
    }

    /**
     * Set state such that the next call to getModel will trigger an update
     */
    void rebuild() {
        final long now = System.currentTimeMillis();
        final long newTimeMs = minExplorerTreeModelBuildTime.getAndUpdate(prev -> Math.max(prev, now));
        final long newId = currentId.incrementAndGet();
        LOGGER.trace(() -> LogUtil.message("rebuild called, newTime: {}, newId: {}",
                Instant.ofEpochMilli(newTimeMs), newId));
        explorerSession.setMinExplorerTreeModelId(newId);
    }

    void clear() {
        setCurrentModel(null);
    }

    @Override
    public void onChange(final EntityEvent event) {
        NullSafe.consume(event, EntityEvent::getAction, action -> {
            switch (action) {
                // The model doesn't care about UPDATE as that is an update of the content of the doc
                // rather than an update to the node (e.g. tags).
                case CREATE,
                        DELETE,
                        UPDATE_EXPLORER_NODE,
                        DELETE_EXPLORER_NODE,
                        CREATE_EXPLORER_NODE -> {
                    // E.g. tags on a node have changed
                    LOGGER.debug("Rebuilding tree model due to entity event {}", event);
                    rebuild();
                }
            }
        });
    }
}
