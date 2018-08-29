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

package stroom.explorer.impl.db;

import java.util.List;

public interface ExplorerTreeDao {
    void update(ExplorerTreeNode node);

    ExplorerTreeNode createRoot(ExplorerTreeNode node);

    List<ExplorerTreeNode> getRoots();

    void removeAll();

    List<ExplorerTreeNode> getTree(ExplorerTreeNode node);

    List<ExplorerTreeNode> getChildren(ExplorerTreeNode node);

    ExplorerTreeNode getParent(ExplorerTreeNode node);

    List<ExplorerTreeNode> getPath(ExplorerTreeNode node);

    ExplorerTreeNode addChild(ExplorerTreeNode parent, ExplorerTreeNode child);

    void remove(ExplorerTreeNode node);

    void move(ExplorerTreeNode node, ExplorerTreeNode parent);

    ExplorerTreeNode findByUUID(final String uuid);
}
