/*
 * Copyright 2016-2025 Crown Copyright
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

import java.util.List;

public interface ExplorerTreeDao {

    TreeModel createModel(long id,
                          long creationTime);

    void update(ExplorerTreeNode node);

    ExplorerTreeNode createRoot(ExplorerTreeNode node);

    boolean doesNodeExist(final ExplorerTreeNode root);

    List<ExplorerTreeNode> getRoots();

    ExplorerTreeNode getRoot(final ExplorerTreeNode node);

    void removeAll();

    List<ExplorerTreeNode> getTree(ExplorerTreeNode node);

    List<ExplorerTreeNode> getChildren(ExplorerTreeNode parent);

    List<ExplorerTreeNode> getChildrenByNameAndType(ExplorerTreeNode parent,
                                                    String name,
                                                    String type);

    ExplorerTreeNode getParent(ExplorerTreeNode node);

    List<ExplorerTreeNode> getPath(ExplorerTreeNode node);

    ExplorerTreeNode addChild(ExplorerTreeNode parent, ExplorerTreeNode child);

    void remove(ExplorerTreeNode node);

    void move(ExplorerTreeNode node, ExplorerTreeNode parent);

    ExplorerTreeNode findByUUID(final String uuid);

    List<ExplorerTreeNode> findByNames(final List<String> names, final boolean allowWildCards);

    List<ExplorerTreeNode> findByType(final String type);
}
