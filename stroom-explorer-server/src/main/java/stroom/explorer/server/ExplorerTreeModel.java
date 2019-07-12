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

package stroom.explorer.server;

import org.springframework.stereotype.Component;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.Insecure;
import stroom.task.server.ExecutorProvider;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
class ExplorerTreeModel {
    private static final long ONE_HOUR = 60 * 60 * 1000;
    private static final long TEN_MINUTES = 10 * 60 * 1000;

    private final ExplorerTreeDao explorerTreeDao;
    private final ExplorerSession explorerSession;
    private final ExecutorProvider executorProvider;

    private volatile TreeModel currentModel;
    private final AtomicLong minExplorerTreeModelBuildTime = new AtomicLong();
    private final AtomicInteger performingRebuild = new AtomicInteger();

    @Inject
    ExplorerTreeModel(final ExplorerTreeDao explorerTreeDao,
                      final ExplorerSession explorerSession,
                      final ExecutorProvider executorProvider) {
        this.explorerTreeDao = explorerTreeDao;
        this.explorerSession = explorerSession;
        this.executorProvider = executorProvider;
    }

    @Insecure
    public TreeModel getModel() {
        final long now = System.currentTimeMillis();
        final AtomicBoolean done = new AtomicBoolean();

        // Create a model synchronously if it is currently null or hasn't been rebuilt for an hour.
        final long oneHourAgo = now - ONE_HOUR;
        if (requiresSynchronousRebuild(oneHourAgo)) {
            done.set(ensureModelExists(oneHourAgo));
        }

        // Force synchronous rebuild of the tree model if it is older than the minimum build time for the current session.
        if (!done.get()) {
            explorerSession.getMinExplorerTreeModelBuildTime().ifPresent(buildTime -> {
                if (buildTime > currentModel.getCreationTime()) {
                    done.set(updateModel());
                }
            });
        }

        // If the model has not been rebuilt in the last 10 minutes for anybody then do so asynchronously.
        if (!done.get()) {
            // Find out what the oldest tree model is that we will allow before performing an asynchronous rebuild.
            final long oldestAllowed = Math.max(minExplorerTreeModelBuildTime.get(), now - TEN_MINUTES);
            if (currentModel.getCreationTime() < oldestAllowed) {
                // Perform a build asynchronously if we aren't already building elsewhere.
                if (performingRebuild.compareAndSet(0, 1)) {
                    final Executor executor = executorProvider.getExecutor();
                    CompletableFuture
                            .runAsync(this::updateModel, executor)
                            .thenRun(performingRebuild::decrementAndGet);
                }
            }
        }

        return currentModel;
    }

    private boolean requiresSynchronousRebuild(final long oldestAllowedForSynchronousRebuild) {
        return currentModel == null || currentModel.getCreationTime() < oldestAllowedForSynchronousRebuild;
    }

    private synchronized boolean ensureModelExists(final long oldestAllowedForSynchronousRebuild) {
        if (requiresSynchronousRebuild(oldestAllowedForSynchronousRebuild)) {
            return updateModel();
        }
        return false;
    }

    private boolean updateModel() {
        performingRebuild.incrementAndGet();
        try {
            setCurrentModel(createModel());
        } finally {
            performingRebuild.decrementAndGet();
        }
        return true;
    }

    TreeModel createModel() {
        return explorerTreeDao.createModel();
    }

    private synchronized void setCurrentModel(final TreeModel treeModel) {
        if (currentModel == null || currentModel.getCreationTime() < treeModel.getCreationTime()) {
            currentModel = treeModel;
        }
    }

    void rebuild() {
        final long now = System.currentTimeMillis();

        minExplorerTreeModelBuildTime.getAndUpdate(prev -> {
            if (prev < now) {
                return now;
            }
            return prev;
        });

        explorerSession.setMinExplorerTreeModelBuildTime(now);
    }

    void clear() {
        currentModel = null;
    }
}
