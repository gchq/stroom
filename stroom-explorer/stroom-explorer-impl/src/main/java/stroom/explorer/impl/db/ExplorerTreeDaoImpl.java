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

package stroom.explorer.impl.db;

import stroom.db.util.JooqUtil;
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.explorer.impl.ExplorerFlags;
import stroom.explorer.impl.ExplorerTreeDao;
import stroom.explorer.impl.ExplorerTreeNode;
import stroom.explorer.impl.ExplorerTreePath;
import stroom.explorer.impl.NodeTagSerialiser;
import stroom.explorer.impl.TreeModel;
import stroom.explorer.impl.db.jooq.tables.records.ExplorerNodeRecord;
import stroom.explorer.impl.db.jooq.tables.records.ExplorerPathRecord;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static stroom.explorer.impl.db.jooq.tables.ExplorerNode.EXPLORER_NODE;
import static stroom.explorer.impl.db.jooq.tables.ExplorerPath.EXPLORER_PATH;

class ExplorerTreeDaoImpl implements ExplorerTreeDao {

    private final boolean orderIndexMatters;
    private final boolean removeReferencedNodes;
    private final ExplorerDbConnProvider explorerDbConnProvider;

    private final stroom.explorer.impl.db.jooq.tables.ExplorerPath p = EXPLORER_PATH.as("p");
    private final stroom.explorer.impl.db.jooq.tables.ExplorerPath p1 = EXPLORER_PATH.as("p1");
    private final stroom.explorer.impl.db.jooq.tables.ExplorerPath p2 = EXPLORER_PATH.as("p2");
    private final stroom.explorer.impl.db.jooq.tables.ExplorerNode n = EXPLORER_NODE.as("n");

    @Inject
    ExplorerTreeDaoImpl(final ExplorerDbConnProvider explorerDbConnProvider) {
        this.removeReferencedNodes = true;
        this.orderIndexMatters = false;
        this.explorerDbConnProvider = explorerDbConnProvider;
    }

    private boolean isPersistent(final ExplorerTreeNode entity) {
        return entity.getId() != null;
    }

    private void assertUpdate(final ExplorerTreeNode node) {
        if (!isPersistent(node)) {
            throw new IllegalArgumentException("Entity is not persistent! Use addChild() for " + node);
        }
    }

    private boolean isRemoveReferencedNodes() {
        return removeReferencedNodes;
    }

    private ExplorerTreeNode create(final ExplorerTreeNode node) {
        final int id = JooqUtil.contextResult(explorerDbConnProvider, context ->
                context
                        .insertInto(EXPLORER_NODE)
                        .set(EXPLORER_NODE.TYPE, node.getType())
                        .set(EXPLORER_NODE.UUID, node.getUuid())
                        .set(EXPLORER_NODE.NAME, node.getName())
                        .set(EXPLORER_NODE.TAGS, NodeTagSerialiser.serialise(node.getTags()))
                        .returning(EXPLORER_NODE.ID)
                        .fetchOne()
                        .getId());
        node.setId(id);
        return node;
    }

    @Override
    public void update(final ExplorerTreeNode node) {
        assertUpdate(node);

        JooqUtil.context(explorerDbConnProvider, context -> context
                .update(EXPLORER_NODE)
                .set(EXPLORER_NODE.TYPE, node.getType())
                .set(EXPLORER_NODE.UUID, node.getUuid())
                .set(EXPLORER_NODE.NAME, node.getName())
                .set(EXPLORER_NODE.TAGS, NodeTagSerialiser.serialise(node.getTags()))
                .where(EXPLORER_NODE.ID.eq(node.getId()))
                .execute());
    }

    private ExplorerTreePath create(final ExplorerTreePath path) {
        JooqUtil.context(explorerDbConnProvider, context -> context
                .insertInto(EXPLORER_PATH)
                .set(EXPLORER_PATH.ANCESTOR, path.getAncestor())
                .set(EXPLORER_PATH.DESCENDANT, path.getDescendant())
                .set(EXPLORER_PATH.DEPTH, path.getDepth())
                .set(EXPLORER_PATH.ORDER_INDEX, path.getOrderIndex())
                .execute());
        return path;
    }

    private void update(final ExplorerTreePath path) {
        JooqUtil.context(explorerDbConnProvider, context -> context
                .update(EXPLORER_PATH)
                .set(EXPLORER_PATH.ANCESTOR, path.getAncestor())
                .set(EXPLORER_PATH.DESCENDANT, path.getDescendant())
                .set(EXPLORER_PATH.DEPTH, path.getDepth())
                .set(EXPLORER_PATH.ORDER_INDEX, path.getOrderIndex())
                .where(EXPLORER_PATH.ANCESTOR.eq(path.getAncestor()))
                .and(EXPLORER_PATH.DESCENDANT.eq(path.getDescendant()))
                .execute());
    }

    @Override
    public ExplorerTreeNode createRoot(final ExplorerTreeNode root) {
        return addChild(null, root);
    }

    @Override
    public boolean doesNodeExist(final ExplorerTreeNode root) {
        return JooqUtil.contextResult(explorerDbConnProvider, context ->
                context
                        .fetchCount(DSL.selectFrom(n)
                                .where(n.UUID.eq(root.getUuid()))
                                .and(n.TYPE.eq(root.getType())))) > 0;
    }

    @Override
    public List<ExplorerTreeNode> getRoots() {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(n)
                        .whereNotExists(context
                                .selectOne()
                                .from(p)
                                .where(p.DESCENDANT.eq(n.ID).and(p.DEPTH.gt(0))))
                        .fetch())
                .map(this::mapRecord);
    }

    /**
     * Get the root node containing a particular node, or the node itself if it is a root node.
     */
    @Override
    public ExplorerTreeNode getRoot(final ExplorerTreeNode node) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> Optional.ofNullable(context
                        .select(n.ID, n.TYPE, n.UUID, n.NAME, n.TAGS, p.DEPTH)
                        .from(n.innerJoin(p).on(n.ID.eq(p.ANCESTOR)))
                        .where(p.DESCENDANT.eq(node.getId()).and(p.ANCESTOR.ne(node.getId())))
                        .orderBy(p.DEPTH.desc())
                        .fetchAny()).map(r ->
                        new ExplorerTreeNode(r.get(n.ID),
                                r.get(n.TYPE),
                                r.get(n.UUID),
                                r.get(n.NAME),
                                NodeTagSerialiser.deserialise(r.get(n.TAGS))))
                .orElse(node));
    }

    @Override
    public TreeModel createModel(final long id,
                                 final long creationTime) {
        final TreeModel treeModel = new TreeModel(id, creationTime);

        final List<Integer> roots = JooqUtil.contextResult(explorerDbConnProvider, context -> context
                .select(p.ANCESTOR)
                .from(p)
                .where(p.DEPTH.eq(0))
                .andNotExists(
                        context
                                .select(DSL.val("x"))
                                .from(p2)
                                .where(p2.DESCENDANT.eq(p.DESCENDANT))
                                .and(p2.DEPTH.gt(0))
                )
                .fetch(p.ANCESTOR));
        if (roots.size() > 0) {
            final Map<Integer, ExplorerNode> nodeMap = JooqUtil.contextResult(explorerDbConnProvider,
                            context -> context
                                    .selectFrom(n)
                                    .fetch())
                    .stream()
                    .collect(Collectors.toMap(ExplorerNodeRecord::getId, rec ->
                            ExplorerNode.builder()
                                    .type(rec.getType())
                                    .uuid(rec.getUuid())
                                    .name(rec.getName())
                                    .tags(NodeTagSerialiser.deserialise(rec.getTags()))
                                    .addNodeFlag(ExplorerFlags.getStandardFlagByDocType(rec.getType())
                                            .orElse(null))
                                    .build()));

            // Add the roots.
            roots.forEach(rootId -> {
                final ExplorerNode root = nodeMap.get(rootId);
                if (root != null) {
                    // The nodes as pulled from the DB don't have the rootNodeUuid set so any equality
                    // test on the system or favourite nodes will fail. Thus use the ones from constants
                    // so, we are comparing like with like.
                    if (Objects.equals(ExplorerConstants.SYSTEM_NODE.getType(), root.getType())) {
                        treeModel.add(null, ExplorerConstants.SYSTEM_NODE);
                    } else if (Objects.equals(ExplorerConstants.FAVOURITES_NODE.getType(), root.getType())) {
                        treeModel.add(null, ExplorerConstants.FAVOURITES_NODE);
                    } else {
                        treeModel.add(null, root);
                    }
                }
            });

            // Add parents and children.
            JooqUtil.contextResult(explorerDbConnProvider, context -> context
                            .select(p.ANCESTOR, p.DESCENDANT)
                            .from(p)
                            .where(p.DEPTH.eq(1))
                            .fetch())
                    .forEach(r -> {
                        final int ancestorId = r.value1();
                        final int descendantId = r.value2();
                        final ExplorerNode ancestor = nodeMap.get(ancestorId);
                        final ExplorerNode descendant = nodeMap.get(descendantId);
                        if (ancestor != null && descendant != null) {
                            treeModel.add(ancestor, descendant);
                        }
                    });
        }

        return treeModel;
    }

    @Override
    public synchronized void removeAll() {
        JooqUtil.context(explorerDbConnProvider, context -> context
                .deleteFrom(p)
                .execute());

        JooqUtil.context(explorerDbConnProvider, context -> context
                .deleteFrom(n)
                .execute());
    }

    @Override
    public List<ExplorerTreeNode> getTree(final ExplorerTreeNode parent) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(n)
                        .where(n.ID.in(context
                                .select(p.DESCENDANT)
                                .from(p)
                                .where(p.ANCESTOR.eq(parent.getId()))))
                        .fetch())
                .map(this::mapRecord);
    }

    @Override
    public List<ExplorerTreeNode> getChildren(final ExplorerTreeNode parent) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(n)
                        .where(n.ID.in(context
                                .select(p.DESCENDANT)
                                .from(p)
                                .where(p.ANCESTOR.eq(parent.getId()))
                                .and(p.DEPTH.eq(1))
                                .orderBy(p.ORDER_INDEX)))
                        .fetch())
                .map(this::mapRecord);
    }

    @Override
    public List<ExplorerTreeNode> getChildrenByNameAndType(final ExplorerTreeNode parent,
                                                           final String name,
                                                           final String type) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(n)
                        .where(n.ID.in(context
                                .select(p.DESCENDANT)
                                .from(p)
                                .where(p.ANCESTOR.eq(parent.getId()))
                                .and(p.DEPTH.eq(1))
                                .orderBy(p.ORDER_INDEX)))
                        .and(n.TYPE.eq(type))
                        .and(n.NAME.eq(name))
                        .fetch())
                .map(this::mapRecord);
    }

    @Override
    public ExplorerTreeNode getParent(final ExplorerTreeNode child) {
        final List<ExplorerTreeNode> parents = JooqUtil.contextResult(explorerDbConnProvider, context ->
                        context
                                .selectFrom(n)
                                .where(n.ID.in(context
                                        .select(p.ANCESTOR)
                                        .from(p)
                                        .where(p.DESCENDANT.eq(child.getId()))
                                        .and(p.DEPTH.eq(1))))
                                .fetch())
                .map(this::mapRecord);

        if (parents.size() == 1) {
            return parents.get(0);
        } else if (parents.size() == 0) {
            return null;
        } else {
            throw new IllegalArgumentException("More than one parent found: " + parents);
        }
    }

    @Override
    public List<ExplorerTreeNode> getPath(final ExplorerTreeNode node) {
        return JooqUtil.contextResult(explorerDbConnProvider, context ->
                        context
                                .select(n.ID, n.TYPE, n.UUID, n.NAME, n.TAGS, p.DEPTH)
                                .from(n.innerJoin(p).on(n.ID.eq(p.ANCESTOR)))
                                .where(p.DESCENDANT.eq(node.getId()).and(p.ANCESTOR.ne(node.getId())))
                                .orderBy(p.DEPTH.desc())
                                .fetch())
                .map(r ->
                        new ExplorerTreeNode(r.get(n.ID),
                                r.get(n.TYPE),
                                r.get(n.UUID),
                                r.get(n.NAME),
                                NodeTagSerialiser.deserialise(r.get(n.TAGS))));
    }

    @Override
    public ExplorerTreeNode addChild(final ExplorerTreeNode parent,
                                     final ExplorerTreeNode child) {
        return addChildAt(parent, child, -1);
    }

    private synchronized ExplorerTreeNode addChildAt(final ExplorerTreeNode parent,
                                                     final ExplorerTreeNode child,
                                                     final int position) {
        return addChild(parent, null, child, position);
    }

    @Override
    public synchronized void remove(final ExplorerTreeNode node) {
        if (node != null && isPersistent(node)) {
            removeTree(node.getId());
        } else {
            throw new IllegalArgumentException("Node to remove is null or not persistent: " + node);
        }
    }

    @Override
    public void move(final ExplorerTreeNode node, final ExplorerTreeNode parent) {
        moveTo(node, parent, -1);
    }

    private synchronized void moveTo(final ExplorerTreeNode node,
                                     final ExplorerTreeNode parent,
                                     final int position) {
        move(node, parent, position, null);
    }

    private boolean shouldCloseGapOnRemove() {
        return true;
    }

    private void removeTree(final Integer parentId) {
        final boolean closeGap = shouldCloseGapOnRemove();
        final List<ExplorerTreePath> pathSiblings = closeGap
                ? getAllTreePathSiblings(parentId)
                : null;
        final Set<Integer> nodesToRemove = new HashSet<>();
        int orderIndex = -1;

        final List<ExplorerTreePath> paths = getPathsToRemove(parentId);
        for (final ExplorerTreePath path : paths) {
            removePath(path);
            nodesToRemove.add(path.getDescendant());
            if (closeGap && path.getDepth() == 1 && Objects.equals(path.getDescendant(), parentId)) {
                assert orderIndex == -1 : "Found second path with depth = 1 where node is descendant: " + path;
                orderIndex = path.getOrderIndex();
            }
        }

        if (closeGap) {
            closeGap(pathSiblings, orderIndex);
        }

        for (final Integer id : nodesToRemove) {
            removeNode(id);
        }
    }

    private void removePath(final ExplorerTreePath path) {
        JooqUtil.context(explorerDbConnProvider, context -> context
                .deleteFrom(p)
                .where(p.ANCESTOR.eq(path.getAncestor()))
                .and(p.DESCENDANT.eq(path.getDescendant()))
                .execute());
    }

    private void removeNode(final Integer id) {
        if (isRemoveReferencedNodes()) {
            JooqUtil.context(explorerDbConnProvider, context -> context
                    .deleteFrom(n)
                    .where(n.ID.eq(id))
                    .execute());
        }
    }

    private List<ExplorerTreePath> getPathsToRemove(final Integer nodeId) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(p)
                        .where(p.DESCENDANT.in(context
                                .select(p1.DESCENDANT)
                                .from(p1)
                                .where(p1.ANCESTOR.eq(nodeId))))
                        .fetch())
                .map(ExplorerTreeDaoImpl::mapExplorerPathRecord);
    }

    private ExplorerTreeNode addChild(final ExplorerTreeNode parent,
                                      final ExplorerTreeNode sibling,
                                      ExplorerTreeNode child,
                                      int orderIndex) {
        if (child == null) {
            throw new IllegalArgumentException("Node to add is null!");
        } else if (isPersistent(child) && exists(child)) {
            throw new IllegalArgumentException("Node is already part of tree: " + child);
        } else {
            assertInsertParameters(parent, sibling, orderIndex);
            final boolean relatedNodeIsParent = parent != null;
            final List<ExplorerTreePath> pathsToClone = new ArrayList<>();
            orderIndex = getPositionAndPathsToConnectSubTree(relatedNodeIsParent,
                    orderIndex,
                    parent,
                    sibling,
                    pathsToClone);
            if (!isPersistent(child)) {
                child = create(child);
            }

            insertSelfReference(child);
            clonePaths(child.getId(), 0, orderIndex, pathsToClone, relatedNodeIsParent);
            return child;
        }
    }

    private boolean exists(final ExplorerTreeNode node) {
        return JooqUtil.contextResult(explorerDbConnProvider, context ->
                context
                        .selectCount()
                        .from(p)
                        .where(p.DESCENDANT.eq(node.getId()))
                        .fetchOne(0, int.class)) > 0;
    }

    private void insertSelfReference(final ExplorerTreeNode child) {
        final ExplorerTreePath newPath = new ExplorerTreePath(child.getId(), child.getId(), 0, -1);
        create(newPath);
    }

    private void move(final ExplorerTreeNode nodeToMove,
                      final ExplorerTreeNode newParent,
                      final int position,
                      final ExplorerTreeNode sibling) {
        assertCopyOrMoveParameters(nodeToMove, newParent, position, sibling);
        disconnectSubTree(nodeToMove);
        if (newParent != null || sibling != null) {
            final List<ExplorerTreePath> childPaths = getPathsIntoSubtree(nodeToMove);
            connectSubTree(newParent, position, sibling, childPaths);
        }
    }

    private void disconnectSubTree(final ExplorerTreeNode node) {
        final List<ExplorerTreePath> pathsToRemove = JooqUtil.contextResult(explorerDbConnProvider, context ->
                        context
                                .selectFrom(p)
                                .where(p.DESCENDANT.in(context
                                        .select(p1.DESCENDANT)
                                        .from(p1)
                                        .where(p1.ANCESTOR.eq(node.getId()))))
                                .and(p.ANCESTOR.notIn(context
                                        .select(p2.DESCENDANT)
                                        .from(p2)
                                        .where(p2.ANCESTOR.eq(node.getId()))))
                                .fetch())
                .map(ExplorerTreeDaoImpl::mapExplorerPathRecord);

        final List<ExplorerTreePath> pathSiblings = getAllTreePathSiblings(node.getId());
        int oldPosition = -1;

        for (final ExplorerTreePath path : pathsToRemove) {
            removePath(path);
            if (path.getDepth() == 1) {
                oldPosition = path.getOrderIndex();
            }
        }

        closeGap(pathSiblings, oldPosition);
    }

    private List<ExplorerTreePath> getPathsIntoSubtree(final ExplorerTreeNode parent) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(p)
                        .where(p.ANCESTOR.eq(parent.getId()))
                        .fetch())
                .map(ExplorerTreeDaoImpl::mapExplorerPathRecord);
    }

    private void connectSubTree(final ExplorerTreeNode newParent,
                                int position,
                                final ExplorerTreeNode sibling,
                                final List<ExplorerTreePath> childPaths) {
        final boolean relatedNodeIsParent = newParent != null;
        final List<ExplorerTreePath> pathsToClone = new ArrayList<>();
        position = getPositionAndPathsToConnectSubTree(relatedNodeIsParent, position, newParent, sibling, pathsToClone);

        for (final ExplorerTreePath childPath : childPaths) {
            clonePaths(childPath.getDescendant(), childPath.getDepth(), position, pathsToClone, relatedNodeIsParent);
        }
    }

    private int getPositionAndPathsToConnectSubTree(final boolean relatedNodeIsParent,
                                                    int position,
                                                    final ExplorerTreeNode parent,
                                                    final ExplorerTreeNode sibling,
                                                    final List<ExplorerTreePath> pathsToClone) {
        assert pathsToClone != null && pathsToClone.size() <= 0;

        assert !relatedNodeIsParent || parent != null;

        if (relatedNodeIsParent) {
            pathsToClone.addAll(JooqUtil.contextResult(explorerDbConnProvider, context ->
                            context
                                    .selectFrom(p)
                                    .where(p.DESCENDANT.eq(parent.getId()))
                                    .fetch())
                    .map(ExplorerTreeDaoImpl::mapExplorerPathRecord));
        } else if (sibling != null) {
            pathsToClone.addAll(JooqUtil.contextResult(explorerDbConnProvider, context ->
                            context
                                    .selectFrom(p)
                                    .where(p.DESCENDANT.eq(parent.getId()))
                                    .and(p.DEPTH.gt(0))
                                    .orderBy(p.DEPTH)
                                    .fetch())
                    .map(ExplorerTreeDaoImpl::mapExplorerPathRecord));

            if (pathsToClone.size() <= 0) {
                throw new IllegalArgumentException("Sibling seems not to be a child but a root: " + sibling);
            }

            position = pathsToClone.get(0).getOrderIndex();

            assert position >= 0 : "Position of first path is not valid: " + pathsToClone;
        }

        return position;
    }

    private void clonePaths(final Integer childId,
                            final int addToDepth,
                            final int position,
                            final List<ExplorerTreePath> pathsToClone,
                            final boolean isParentPaths) {
        for (final ExplorerTreePath pathToClone : pathsToClone) {
            final int depth = pathToClone.getDepth() + addToDepth + (isParentPaths
                    ? 1
                    : 0);
            final int newPosition = depth == 1
                    ? createGap(isParentPaths
                    ? pathToClone.getDescendant()
                    : pathToClone.getAncestor(), position)
                    : -1;
            final ExplorerTreePath newPath = new ExplorerTreePath(
                    pathToClone.getAncestor(),
                    childId,
                    depth,
                    newPosition);
            create(newPath);
        }
    }

    private int createGap(final Integer parentId,
                          final int position) {
        if (!orderIndexMatters) {
            return 0;
        } else {
            final List<ExplorerTreePath> children = getAllDirectTreePathChildren(parentId);
            if (position == -1) {
                return children.size();
            } else {
                for (int i = children.size() - 1; i >= position; --i) {
                    final ExplorerTreePath treePath = children.get(i);
                    treePath.setOrderIndex(i + 1);
                    update(treePath);
                }

                return position;
            }
        }
    }

    private void closeGap(final List<ExplorerTreePath> siblings,
                          final int removedPosition) {
        if (orderIndexMatters) {
            if (removedPosition >= 0) {
                for (int i = removedPosition; i < siblings.size(); ++i) {
                    final ExplorerTreePath pathSibling = siblings.get(i);
                    pathSibling.setOrderIndex(i);
                    update(pathSibling);
                }
            }

        }
    }

    private List<ExplorerTreePath> getAllDirectTreePathChildren(final Integer parentId) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(p)
                        .where(p.ANCESTOR.eq(parentId))
                        .and(p.DEPTH.eq(1))
                        .orderBy(p.ORDER_INDEX)
                        .fetch())
                .map(ExplorerTreeDaoImpl::mapExplorerPathRecord);
    }

    private List<ExplorerTreePath> getAllTreePathSiblings(final Integer nodeId) {
        return JooqUtil.contextResult(explorerDbConnProvider, context -> context
                        .selectFrom(p)
                        .where(p.DEPTH.eq(1))
                        .and(p.DESCENDANT.notEqual(nodeId))
                        .and(p.ANCESTOR.in(context
                                .select(p2.ANCESTOR)
                                .from(p2)
                                .where(p2.DESCENDANT.eq(nodeId))
                                .and(p2.DEPTH.eq(1))))
                        .orderBy(p.ORDER_INDEX)
                        .fetch())
                .map(ExplorerTreeDaoImpl::mapExplorerPathRecord);
    }

    private static ExplorerTreePath mapExplorerPathRecord(final ExplorerPathRecord record) {
        return new ExplorerTreePath(
                record.getAncestor(),
                record.getDescendant(),
                record.getDepth(),
                record.getOrderIndex());
    }

    @Override
    public ExplorerTreeNode findByUUID(final String uuid) {
        if (uuid == null) {
            return null;
        }

        final List<ExplorerTreeNode> list = JooqUtil.contextResult(explorerDbConnProvider, context ->
                        context
                                .selectFrom(n)
                                .where(n.UUID.eq(uuid))
                                .fetch())
                .map(this::mapRecord);

        if (list.isEmpty()) {
            return null;
        }

        if (list.size() > 1) {
            throw new RuntimeException("Found more than 1 tree node with uuid=" + uuid);
        }

        return list.get(0);
    }

    @Override
    public List<ExplorerTreeNode> findByNames(final List<String> names,
                                              final boolean allowWildCards) {
        final Condition nameConditions = JooqUtil.createWildCardedStringsCondition(
                n.UUID, names, allowWildCards, BooleanOperator.OR);

        return JooqUtil.contextResult(explorerDbConnProvider, context ->
                        context
                                .selectFrom(n)
                                .where(nameConditions)
                                .fetch())
                .map(this::mapRecord);
    }

    @Override
    public List<ExplorerTreeNode> findByType(final String type) {

        return JooqUtil.contextResult(explorerDbConnProvider, context ->
                        context
                                .selectFrom(n)
                                .where(n.TYPE.eq(type))
                                .fetch())
                .map(this::mapRecord);
    }

    private ExplorerTreeNode mapRecord(final ExplorerNodeRecord record) {
        return new ExplorerTreeNode(
                record.getId(),
                record.getType(),
                record.getUuid(),
                record.getName(),
                NodeTagSerialiser.deserialise(record.getTags()));
    }

    private void assertInsertParameters(final ExplorerTreeNode parent,
                                        final ExplorerTreeNode sibling,
                                        final int position) {
        assert parent == null || sibling == null;

        assert sibling == null || position == -1;

    }

    private void assertCopyOrMoveParameters(final ExplorerTreeNode node,
                                            final ExplorerTreeNode newParent,
                                            final int position,
                                            final ExplorerTreeNode sibling) {
        if (node != null && isPersistent(node)) {
            assertInsertParameters(newParent, sibling, position);
        } else {
            throw new IllegalArgumentException("Node to move is null or not persistent: " + node);
        }
    }
}
