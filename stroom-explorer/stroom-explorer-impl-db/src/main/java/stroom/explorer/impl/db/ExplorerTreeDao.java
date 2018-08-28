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
import java.util.Map;

public interface ExplorerTreeDao {
    int UNDEFINED_POSITION = -1;

    boolean isPersistent(ExplorerTreeNode var1);

    ExplorerTreeNode find(Integer var1);

    void update(ExplorerTreeNode var1);

    boolean isRoot(ExplorerTreeNode var1);

    ExplorerTreeNode createRoot(ExplorerTreeNode var1);

    int size(ExplorerTreeNode var1);

    List<ExplorerTreeNode> getRoots();

    void removeAll();

    List<ExplorerTreeNode> getTree(ExplorerTreeNode var1);

    List<ExplorerTreeNode> getTreeCacheable(ExplorerTreeNode var1);

    List<ExplorerTreeNode> findSubTree(ExplorerTreeNode var1, List<ExplorerTreeNode> var2);

    List<ExplorerTreeNode> findDirectChildren(List<ExplorerTreeNode> var1);

    boolean isLeaf(ExplorerTreeNode var1);

    int getChildCount(ExplorerTreeNode var1);

    List<ExplorerTreeNode> getChildren(ExplorerTreeNode var1);

    ExplorerTreeNode getRoot(ExplorerTreeNode var1);

    ExplorerTreeNode getParent(ExplorerTreeNode var1);

    List<ExplorerTreeNode> getPath(ExplorerTreeNode var1);

    int getLevel(ExplorerTreeNode var1);

    boolean isEqualToOrChildOf(ExplorerTreeNode var1, ExplorerTreeNode var2);

    boolean isChildOf(ExplorerTreeNode var1, ExplorerTreeNode var2);

    ExplorerTreeNode addChild(ExplorerTreeNode var1, ExplorerTreeNode var2);

    ExplorerTreeNode addChildAt(ExplorerTreeNode var1, ExplorerTreeNode var2, int var3);

    ExplorerTreeNode addChildBefore(ExplorerTreeNode var1, ExplorerTreeNode var2);

    void remove(ExplorerTreeNode var1);

    void move(ExplorerTreeNode var1, ExplorerTreeNode var2);

    void moveTo(ExplorerTreeNode var1, ExplorerTreeNode var2, int var3);

    void moveBefore(ExplorerTreeNode var1, ExplorerTreeNode var2);

    void moveToBeRoot(ExplorerTreeNode var1);

    ExplorerTreeNode copy(ExplorerTreeNode var1, ExplorerTreeNode var2, ExplorerTreeNode var3);

    ExplorerTreeNode copyTo(ExplorerTreeNode var1, ExplorerTreeNode var2, int var3, ExplorerTreeNode var4);

    ExplorerTreeNode copyBefore(ExplorerTreeNode var1, ExplorerTreeNode var2, ExplorerTreeNode var3);

    ExplorerTreeNode copyToBeRoot(ExplorerTreeNode var1, ExplorerTreeNode var2);

//    void setCopiedNodeRenamer(TreeDao.CopiedNodeRenamer<ExplorerTreeNode> var1);

    List<ExplorerTreeNode> find(ExplorerTreeNode var1, Map<String, Object> var2);

//    void setUniqueTreeConstraint(UniqueTreeConstraint<ExplorerTreeNode> var1);
//
//    void setCheckUniqueConstraintOnUpdate(boolean var1);

//    void checkUniqueConstraint(ExplorerTreeNode var1, ExplorerTreeNode var2, ExplorerTreeNode var3) ;
//
//    public interface CopiedNodeRenamer<ExplorerTreeNode extends TreeNode> {
//        void renameCopiedNode(ExplorerTreeNode var1);
//    }

    ExplorerTreeNode findByUUID(final String uuid);
}
