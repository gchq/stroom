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
import stroom.util.task.TaskScopeRunnable;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

@Component
class ExplorerTreeModel {
    private static final long TEN_MINUTES = 1000 * 60 * 10;

    private final ExplorerTreeDao explorerTreeDao;
    private final ExplorerActionHandlersImpl explorerActionHandlers;
    private final ReentrantLock treeBuildLock = new ReentrantLock();

    private volatile boolean isBuilding = false;
    private volatile boolean rebuildRequired = true;
    private volatile TreeModel treeModel;
    private volatile long lastBuildTime;

    @Inject
    ExplorerTreeModel(final ExplorerTreeDao explorerTreeDao, final ExplorerActionHandlersImpl explorerActionHandlers) {
        this.explorerTreeDao = explorerTreeDao;
        this.explorerActionHandlers = explorerActionHandlers;
    }

    private boolean isRebuildRequired() {
        // If the tree is more than 10 minutes old then rebuild it.
        // OR if something has requested a rebuild after the last rebuild
        return (lastBuildTime < System.currentTimeMillis() - TEN_MINUTES)
                || rebuildRequired
                || isBuilding;
    }

    @Insecure
    public TreeModel getModel() {
        if (isRebuildRequired()) {
            // Try and get the map under lock.
            treeBuildLock.lock();
            isBuilding = true;

            try {

                // If calls are made to 'rebuildTree' while a build is happening, it will force it to go round again
                while (rebuildRequired) {
                    rebuildRequired = false;

                    // Record the last time we built the full tree.
                    treeModel = createModel();

                    lastBuildTime = System.currentTimeMillis();
                }
            } finally {
                isBuilding = false;
                treeBuildLock.unlock();
            }
        }

        return treeModel;
    }

    private TreeModel createModel() {
        final TreeModel newTreeModel = new TreeModelImpl();
        final TaskScopeRunnable runnable = new TaskScopeRunnable(null) {
            @Override
            protected void exec() {
                final List<ExplorerTreeNode> roots = explorerTreeDao.getRoots();
                addChildren(newTreeModel, sort(roots), null);
            }
        };

        runnable.run();
        return newTreeModel;
    }

    private void addChildren(final TreeModel treeModel, final List<ExplorerTreeNode> children, final ExplorerNode parentNode) {
        for (final ExplorerTreeNode child : children) {
            final ExplorerNode explorerNode = createExplorerNode(child);
            explorerNode.setIconUrl(getIconUrl(child.getType()));
            treeModel.add(parentNode, explorerNode);

            final List<ExplorerTreeNode> subChildren = explorerTreeDao.getChildren(child);
            if (subChildren != null && subChildren.size() > 0) {
                addChildren(treeModel, sort(subChildren), explorerNode);
            }
        }
    }

    private List<ExplorerTreeNode> sort(final List<ExplorerTreeNode> list) {
        list.sort((o1, o2) -> {
            if (!o1.getType().equals(o2.getType())) {
                final int p1 = getPriority(o1.getType());
                final int p2 = getPriority(o2.getType());
                return Integer.compare(p1, p2);
            }

            return o1.getName().compareTo(o2.getName());
        });
        return list;
    }

    private String getIconUrl(final String type) {
        final DocumentType documentType = explorerActionHandlers.getType(type);
        if (documentType == null) {
            return null;
        }

        return documentType.getIconUrl();
    }

    private int getPriority(final String type) {
        final DocumentType documentType = explorerActionHandlers.getType(type);
        if (documentType == null) {
            return Integer.MAX_VALUE;
        }

        return documentType.getPriority();
    }

    void rebuildTree() {
        this.rebuildRequired = true;
    }

    private ExplorerNode createExplorerNode(final ExplorerTreeNode explorerTreeNode) {
        return new ExplorerNode(explorerTreeNode.getType(), explorerTreeNode.getUuid(), explorerTreeNode.getName(), explorerTreeNode.getTags());
    }
}
