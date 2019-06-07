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

import apple.laf.JRSUIUtils;
import org.springframework.stereotype.Component;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.Insecure;
import stroom.servlet.HttpServletRequestHolder;
import stroom.util.task.TaskScopeRunnable;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
class ExplorerTreeModel {
    private static final String MIN_EXPLORER_TREE_MODEL_BUILD_TIME =  "MIN_EXPLORER_TREE_MODEL_BUILD_TIME";

    private final ExplorerTreeDao explorerTreeDao;
    private final ExplorerActionHandlersImpl explorerActionHandlers;
    private final HttpServletRequestHolder httpServletRequestHolder;

    private volatile TreeModel currentModel;

    @Inject
    ExplorerTreeModel(final ExplorerTreeDao explorerTreeDao,
                      final ExplorerActionHandlersImpl explorerActionHandlers,
                      final HttpServletRequestHolder httpServletRequestHolder) {
        this.explorerTreeDao = explorerTreeDao;
        this.explorerActionHandlers = explorerActionHandlers;
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    @Insecure
    public TreeModel getModel() {
        if (currentModel == null) {
            // Create a model synchronously if it is currently null.
            ensureModelExists();
        }




        TreeModel treeModel = null;

        final HttpSession session = getSession();
        final Object object = session.getAttribute(REBUILD_REQUIRED);
        if (object == null) {
            session.setAttribute(name, treeModel);
        } else {
            treeModel = (TreeModel) object;
        }

        return treeModel;
    }

    private synchronized boolean isRebuildRequired() {
        modelCache.setRebuildRequired();
        getSession().setAttribute(REBUILD_REQUIRED, Boolean.TRUE);
    }

    private void setRebuildRequired() {
        final long now = System.currentTimeMillis();
        modelCache.setRebuildRequired();
        setMinExplorerTreeModelBuildTime(now);
    }

    private Optional<Long> getMinExplorerTreeModelBuildTime() {
        final HttpSession session = getSession();
        final Object object = session.getAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME);
        return Optional.ofNullable((Long) object);
    }

    private void setMinExplorerTreeModelBuildTime(final long buildTime) {
        getSession().setAttribute(MIN_EXPLORER_TREE_MODEL_BUILD_TIME, buildTime);
    }

    private HttpSession getSession() {
        final HttpServletRequest request = httpServletRequestHolder.get();
        if (request == null) {
            throw new NullPointerException("Request holder has no current request");
        }
        return request.getSession();
    }

    private synchronized void ensureModelExists() {
        if (currentModel == null) {
            setCurrentModel(createModel());
        }
    }

    private synchronized void setCurrentModel(final TreeModel treeModel) {
        if (currentModel == null || currentModel.getCreationTime() < treeModel.getCreationTime()) {
            currentModel = treeModel;
        }
    }

    private TreeModel createModel() {
        final TreeModel newTreeModel = new TreeModelImpl();
        final List<ExplorerTreeNode> roots = explorerTreeDao.getRoots();
        addChildren(newTreeModel, sort(roots), null);
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

    void rebuild() {
        setRebuildRequired();
    }

    private ExplorerNode createExplorerNode(final ExplorerTreeNode explorerTreeNode) {
        return new ExplorerNode(explorerTreeNode.getType(), explorerTreeNode.getUuid(), explorerTreeNode.getName(), explorerTreeNode.getTags());
    }
}
