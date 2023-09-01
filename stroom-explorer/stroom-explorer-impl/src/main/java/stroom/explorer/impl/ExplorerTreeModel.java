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
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeInfo;
import stroom.importexport.api.ContentService;
import stroom.svg.shared.SvgImage;
import stroom.task.api.TaskContextFactory;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

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
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class ExplorerTreeModel {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerTreeModel.class);

    static final String BRANCH_NODE_INFO = "Descendants have issues";

    private static final long ONE_HOUR = 60 * 60 * 1000;
    private static final long TEN_MINUTES = 10 * 60 * 1000;
    private static final long BROKEN_DEPS_MAX_AGE_MS = 10_000L;

    private final ExplorerTreeDao explorerTreeDao;
    private final ExplorerSession explorerSession;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final Provider<ContentService> contentServiceProvider;

    private volatile UnmodifiableTreeModel currentModel;
    private final AtomicLong minExplorerTreeModelBuildTime = new AtomicLong();
    private final AtomicLong currentId = new AtomicLong();
    private final AtomicInteger performingRebuild = new AtomicInteger();

    private volatile Map<DocRef, Set<DocRef>> brokenDependenciesMap = Collections.emptyMap();
    private volatile long brokenDepsNextUpdateEpochMs = 0L;

    @Inject
    ExplorerTreeModel(final ExplorerTreeDao explorerTreeDao,
                      final ExplorerSession explorerSession,
                      final Executor executor,
                      final TaskContextFactory taskContextFactory,
                      final ExplorerActionHandlers explorerActionHandlers,
                      final Provider<ContentService> contentServiceProvider) {
        this.explorerTreeDao = explorerTreeDao;
        this.explorerSession = explorerSession;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.explorerActionHandlers = explorerActionHandlers;
        this.contentServiceProvider = contentServiceProvider;
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

        LOGGER.debug("currentId: {}, minId: {}", currentId, minId);

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

    private UnmodifiableTreeModel updateModel(final long id, final long creationTime) {
        TreeModel newModel;
        UnmodifiableTreeModel newUnmodifiableModel;
        performingRebuild.incrementAndGet();
        try {
            LOGGER.debug("Updating model for id {}", id);
            newModel = explorerTreeDao.createModel(this::getIcon, id, creationTime);

            decorateWithBrokenDeps(newModel);

            // Now make it immutable as this is our master model
            newUnmodifiableModel = UnmodifiableTreeModel.wrap(newModel);
            setCurrentModel(newUnmodifiableModel);
        } finally {
            performingRebuild.decrementAndGet();
        }
        return newUnmodifiableModel;
    }

    private void decorateWithBrokenDeps(final TreeModel treeModel) {
        final Map<DocRef, Set<DocRef>> brokenDepsMap = getBrokenDependenciesMap();

        treeModel.decorate((nodeKey, node) -> {
            LOGGER.debug(() -> "decorate called on node: " + node.getName());

            return cloneWithNodeInfo(treeModel, brokenDepsMap, node);
        });
    }

//    private void decorateLeavesWithBrokenDeps(final TreeModel treeModel) {
//        final Map<DocRef, Set<DocRef>> brokenDepsMap = getBrokenDependenciesMap();
//
//        treeModel.decorate((nodeKey, node) -> {
//            LOGGER.debug(() -> "decorate called on node: " + node.getName());
//
//            return cloneWithLeafNodeInfo(treeModel, brokenDepsMap, node);
//        });
//    }
//
//    <K> void decorateBranchesWithBrokenDeps(final AbstractTreeModel<K> treeModel) {
//        final Map<DocRef, Set<DocRef>> brokenDepsMap = getBrokenDependenciesMap();
//
//        treeModel.decorate((nodeKey, node) -> {
//            LOGGER.debug(() -> "decorate called on node: " + node.getName());
//
//            return cloneWithBranchNodeInfo(treeModel, brokenDepsMap, node);
//        });
//    }
//
//    ExplorerNode cloneWithLeafNodeInfo(final TreeModel treeModel,
//                                       final ExplorerNode node) {
//        final Map<DocRef, Set<DocRef>> brokenDepsMap = getBrokenDependenciesMap();
//        return cloneWithLeafNodeInfo(treeModel, brokenDepsMap, node);
//    }

    ExplorerNode cloneWithBranchNodeInfo(final TreeModel treeModel,
                                       final ExplorerNode node) {
        final Map<DocRef, Set<DocRef>> brokenDepsMap = getBrokenDependenciesMap();
        return cloneWithBranchNodeInfo(treeModel, brokenDepsMap, node);
    }

    private ExplorerNode cloneWithNodeInfo(final TreeModel treeModel,
                                           final Map<DocRef, Set<DocRef>> brokenDepsMap,
                                           final ExplorerNode node) {
        final DocRef docRef = node.getDocRef();
        final Set<NodeInfo> nodeInfos = buildNodeInfo(docRef, brokenDepsMap);
        final List<ExplorerNode> children = treeModel.getChildren(node);

        if (NullSafe.isEmptyCollection(children) && NullSafe.hasItems(nodeInfos)) {
            LOGGER.debug(() -> LogUtil.message("Leaf '{}' has issues", node.getName()));
            return node.copy()
                    .addNodeInfo(nodeInfos)
                    .build();
        } else {
            // Branch
            final Severity maxSeverity = Severity.getMaxSeverity(
                            NullSafe.stream(children)
                                    .flatMap(childNode -> NullSafe.stream(childNode.getNodeInfoList())
                                            .map(NodeInfo::getSeverity))
                                    .max(Severity.HIGH_TO_LOW_COMPARATOR)
                                    .orElse(null),
                            null)
                    .orElse(null);

            if (maxSeverity != null) {
                // At least one descendant has an issue, so mark this branch too
                LOGGER.debug(() -> LogUtil.message("Branch '{}' has issues, maxSeverity: {}",
                        node.getName(), maxSeverity));
                return node.copy()
                        .addNodeInfo(new NodeInfo(
                                maxSeverity,
                                BRANCH_NODE_INFO))
                        .build();
            } else {
                // No issues, return the node as is
                return node;
            }
        }
    }

//    private ExplorerNode cloneWithLeafNodeInfo(final TreeModel treeModel,
//                                               final Map<DocRef, Set<DocRef>> brokenDepsMap,
//                                               final ExplorerNode node) {
//        final DocRef docRef = node.getDocRef();
//        final Set<NodeInfo> nodeInfos = buildNodeInfo(docRef, brokenDepsMap);
//        final List<ExplorerNode> children = treeModel.getChildren(node);
//
//        if (NullSafe.isEmptyCollection(children) && NullSafe.hasItems(nodeInfos)) {
//            LOGGER.debug(() -> LogUtil.message("Leaf '{}' has issues", node.getName()));
//            return node.copy()
//                    .addNodeInfo(nodeInfos)
//                    .build();
//        } else {
//            return node;
//        }
//    }

    private <K> ExplorerNode cloneWithBranchNodeInfo(final AbstractTreeModel<K> treeModel,
                                                     final Map<DocRef, Set<DocRef>> brokenDepsMap,
                                                     final ExplorerNode node) {
        final DocRef docRef = node.getDocRef();
        final Set<NodeInfo> nodeInfos = buildNodeInfo(docRef, brokenDepsMap);
        final List<ExplorerNode> children = treeModel.getChildren(node);

        if (NullSafe.hasItems(children) && NullSafe.isEmptyCollection(nodeInfos)) {
            final Severity maxSeverity = Severity.getMaxSeverity(
                            NullSafe.stream(children)
                                    .flatMap(childNode -> NullSafe.stream(childNode.getNodeInfoList())
                                            .map(NodeInfo::getSeverity))
                                    .max(Severity.HIGH_TO_LOW_COMPARATOR)
                                    .orElse(null),
                            null)
                    .orElse(null);

            if (maxSeverity != null) {
                // At least one descendant has an issue, so mark this branch too
                LOGGER.debug(() -> LogUtil.message("Branch '{}' has issues, maxSeverity: {}",
                        node.getName(), maxSeverity));
                return node.copy()
                        .addNodeInfo(new NodeInfo(
                                maxSeverity,
                                BRANCH_NODE_INFO))
                        .build();
            } else {
                // No issues, return the node as is
                return node;
            }
        } else {
            return node;
        }
    }

    private Set<NodeInfo> buildNodeInfo(final DocRef docRef,
                                        final Map<DocRef, Set<DocRef>> brokenDepsMap) {
        final Set<DocRef> brokenDeps = brokenDepsMap.get(docRef);
        if (brokenDeps == null) {
            return null;
        } else {
            return brokenDeps.stream()
                    .map(missingDocRef -> {
                        final String msg = LogUtil.message(
                                "Missing dependency to {} '{}' ({})",
                                missingDocRef.getType(),
                                missingDocRef.getName(),
                                missingDocRef.getUuid());
                        return new NodeInfo(Severity.ERROR, msg);
                    })
                    .collect(Collectors.toSet());
        }
    }

    private Map<DocRef, Set<DocRef>> getBrokenDependenciesMap() {
        if (System.currentTimeMillis() > brokenDepsNextUpdateEpochMs) {
            synchronized (this) {
                if (System.currentTimeMillis() > brokenDepsNextUpdateEpochMs) {
                    LOGGER.debug("Updating broken dependencies map");
                    brokenDependenciesMap = contentServiceProvider.get().fetchBrokenDependencies();
                    brokenDepsNextUpdateEpochMs = System.currentTimeMillis() + BROKEN_DEPS_MAX_AGE_MS;
                }
            }
        }
        return brokenDependenciesMap;
    }

    private SvgImage getIcon(final String type) {
        final DocumentType documentType = explorerActionHandlers.getType(type);
        if (documentType == null) {
            return null;
        }

        return documentType.getIcon();
    }

    private synchronized void setCurrentModel(final UnmodifiableTreeModel treeModel) {
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
