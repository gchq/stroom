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

package stroom.explorer;

import fri.util.database.jpa.commons.DbSession;
import fri.util.database.jpa.tree.closuretable.ClosureTableTreeDao;
import fri.util.database.jpa.tree.closuretable.ClosureTableTreeNode;
import fri.util.database.jpa.tree.uniqueconstraints.UniqueConstraintViolationException;
import fri.util.database.jpa.tree.uniqueconstraints.UniqueTreeConstraint;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Transactional
class ExplorerTreeDaoImpl implements ExplorerTreeDao {
    private final DbSession session;
    private final ClosureTableTreeDao dao;

    @Inject
    ExplorerTreeDaoImpl(final DbSession session) {
        this.session = session;
        this.dao = new ClosureTableTreeDao(ExplorerTreeNode.class, ExplorerTreePath.class, false, session);
        this.dao.setRemoveReferencedNodes(true);
    }

    @Override
    public boolean isPersistent(final ExplorerTreeNode entity) {
        return dao.isPersistent(entity);
    }

    @Override
    public ExplorerTreeNode find(final Serializable id) {
        return (ExplorerTreeNode) dao.find(id);
    }

    @Override
    public void update(final ExplorerTreeNode entity) throws UniqueConstraintViolationException {
        dao.update(entity);
    }

    @Override
    public boolean isRoot(final ExplorerTreeNode entity) {
        return dao.isRoot(entity);
    }

    @Override
    public ExplorerTreeNode createRoot(final ExplorerTreeNode root) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.createRoot(root);
    }

    @Override
    public int size(final ExplorerTreeNode tree) {
        return dao.size(tree);
    }

    @Override
    public List<ExplorerTreeNode> getRoots() {
        return convertTo(dao.getRoots());
    }

    @Override
    public void removeAll() {
        dao.removeAll();
    }

    @Override
    public List<ExplorerTreeNode> getTree(final ExplorerTreeNode parent) {
        return convertTo(dao.getTree(parent));
    }

    @Override
    public List<ExplorerTreeNode> getTreeCacheable(final ExplorerTreeNode parent) {
        return convertTo(dao.getTreeCacheable(parent));
    }

    @Override
    public List<ExplorerTreeNode> findSubTree(final ExplorerTreeNode parent, final List<ExplorerTreeNode> treeCacheable) {
        return convertTo(dao.findSubTree(parent, convertFrom(treeCacheable)));
    }

    @Override
    public List<ExplorerTreeNode> findDirectChildren(final List<ExplorerTreeNode> treeCacheable) {
        return convertTo(dao.findDirectChildren(convertFrom(treeCacheable)));
    }

    @Override
    public boolean isLeaf(final ExplorerTreeNode node) {
        return dao.isLeaf(node);
    }

    @Override
    public int getChildCount(final ExplorerTreeNode parent) {
        return dao.getChildCount(parent);
    }

    @Override
    public List<ExplorerTreeNode> getChildren(final ExplorerTreeNode parent) {
        return convertTo(dao.getChildren(parent));
    }

    @Override
    public ExplorerTreeNode getRoot(final ExplorerTreeNode node) {
        return (ExplorerTreeNode) dao.getRoot(node);
    }

    @Override
    public ExplorerTreeNode getParent(final ExplorerTreeNode node) {
        return (ExplorerTreeNode) dao.getParent(node);
    }

    @Override
    public List<ExplorerTreeNode> getPath(final ExplorerTreeNode node) {
        return convertTo(dao.getPath(node));
    }

    @Override
    public int getLevel(final ExplorerTreeNode node) {
        return dao.getLevel(node);
    }

    @Override
    public boolean isEqualToOrChildOf(final ExplorerTreeNode child, final ExplorerTreeNode parent) {
        return dao.isEqualToOrChildOf(child, parent);
    }

    @Override
    public boolean isChildOf(final ExplorerTreeNode child, final ExplorerTreeNode parent) {
        return dao.isChildOf(child, parent);
    }

    @Override
    public ExplorerTreeNode addChild(final ExplorerTreeNode parent, final ExplorerTreeNode child) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.addChild(parent, child);
    }

    @Override
    public ExplorerTreeNode addChildAt(final ExplorerTreeNode parent, final ExplorerTreeNode child, final int position) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.addChildAt(parent, child, position);
    }

    @Override
    public ExplorerTreeNode addChildBefore(final ExplorerTreeNode sibling, final ExplorerTreeNode child) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.addChildBefore(sibling, child);
    }

    @Override
    public void remove(final ExplorerTreeNode node) {
        dao.remove(node);
    }

    @Override
    public void move(final ExplorerTreeNode node, final ExplorerTreeNode newParent) throws UniqueConstraintViolationException {
        dao.move(node, newParent);
    }

    @Override
    public void moveTo(final ExplorerTreeNode node, final ExplorerTreeNode parent, final int position) throws UniqueConstraintViolationException {
        dao.moveTo(node, parent, position);
    }

    @Override
    public void moveBefore(final ExplorerTreeNode node, final ExplorerTreeNode sibling) throws UniqueConstraintViolationException {
        dao.moveBefore(node, sibling);
    }

    @Override
    public void moveToBeRoot(final ExplorerTreeNode child) throws UniqueConstraintViolationException {
        dao.moveToBeRoot(child);
    }

    @Override
    public ExplorerTreeNode copy(final ExplorerTreeNode node, final ExplorerTreeNode parent, final ExplorerTreeNode copiedNodeTemplate) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.copy(node, parent, copiedNodeTemplate);
    }

    @Override
    public ExplorerTreeNode copyTo(final ExplorerTreeNode node, final ExplorerTreeNode parent, final int position, final ExplorerTreeNode copiedNodeTemplate) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.copyTo(node, parent, position, copiedNodeTemplate);
    }

    @Override
    public ExplorerTreeNode copyBefore(final ExplorerTreeNode node, final ExplorerTreeNode sibling, final ExplorerTreeNode copiedNodeTemplate) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.copyBefore(node, sibling, copiedNodeTemplate);
    }

    @Override
    public ExplorerTreeNode copyToBeRoot(final ExplorerTreeNode child, final ExplorerTreeNode copiedNodeTemplate) throws UniqueConstraintViolationException {
        return (ExplorerTreeNode) dao.copyToBeRoot(child, copiedNodeTemplate);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setCopiedNodeRenamer(final CopiedNodeRenamer<ExplorerTreeNode> copiedNodeRenamer) {
        dao.setCopiedNodeRenamer((CopiedNodeRenamer<ClosureTableTreeNode>) (CopiedNodeRenamer) copiedNodeRenamer);
    }

    @Override
    public List<ExplorerTreeNode> find(final ExplorerTreeNode parent, final Map<String, Object> criteria) {
        return convertTo(dao.find(parent, criteria));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setUniqueTreeConstraint(final UniqueTreeConstraint<ExplorerTreeNode> uniqueTreeConstraint) {
        dao.setUniqueTreeConstraint((UniqueTreeConstraint<ClosureTableTreeNode>) (UniqueTreeConstraint) uniqueTreeConstraint);
    }

    @Override
    public void setCheckUniqueConstraintOnUpdate(final boolean checkUniqueConstraintOnUpdate) {
        dao.setCheckUniqueConstraintOnUpdate(checkUniqueConstraintOnUpdate);
    }

    @Override
    public void checkUniqueConstraint(final ExplorerTreeNode cloneOfExistingNodeWithNewValues, final ExplorerTreeNode root, final ExplorerTreeNode originalNode) throws UniqueConstraintViolationException {
        dao.checkUniqueConstraint(cloneOfExistingNodeWithNewValues, root, originalNode);
    }

    @SuppressWarnings("unchecked")
    private List<ExplorerTreeNode> convertTo(final List<ClosureTableTreeNode> list) {
        return (List<ExplorerTreeNode>) (List) list;
    }

    @SuppressWarnings("unchecked")
    private List<ClosureTableTreeNode> convertFrom(final List<ExplorerTreeNode> list) {
        return (List<ClosureTableTreeNode>) (List) list;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExplorerTreeNode findByUUID(final String uuid) {
        if (uuid == null) {
            return null;
        }

        final List<ExplorerTreeNode> list = (List<ExplorerTreeNode>) this.session.queryList("select n from " + ExplorerTreeNode.class.getSimpleName() + " n where n.uuid = ?", new String[]{uuid});
        if (list == null || list.size() == 0) {
            return null;
        }

        if (list.size() > 1) {
            throw new RuntimeException("Found more than 1 tree node with uuid=" + uuid);
        }

        return list.get(0);
    }
}
