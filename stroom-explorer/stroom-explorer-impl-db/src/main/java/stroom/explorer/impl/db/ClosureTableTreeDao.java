package stroom.explorer.impl.db;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.and;
import static stroom.explorer.impl.db.stroom.tables.Explorertreenode.EXPLORERTREENODE;
import static stroom.explorer.impl.db.stroom.tables.Explorertreepath.EXPLORERTREEPATH;

public class ClosureTableTreeDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClosureTableTreeDao.class);

    //    private final Class<? extends ExplorerTreeNode> treeNodeEntityClass;
//    private final Class<? extendsExplorerTreePath>treePathEntityClass;
//    private final String treePathEntity;
    private final boolean orderIndexMatters;
    private boolean removeReferencedNodes;
    private final ConnectionProvider connectionProvider;

    private final stroom.explorer.impl.db.stroom.tables.Explorertreepath p = EXPLORERTREEPATH.as("p");
    private final stroom.explorer.impl.db.stroom.tables.Explorertreepath p1 = EXPLORERTREEPATH.as("p1");
    private final stroom.explorer.impl.db.stroom.tables.Explorertreepath p2 = EXPLORERTREEPATH.as("p2");
    private final stroom.explorer.impl.db.stroom.tables.Explorertreenode n = EXPLORERTREENODE.as("n");

//    public ClosureTableTreeDao(Class<? extends ExplorerTreeNode> treeNodeEntityClass, Class<? extendsExplorerTreePath>treePathsEntityClass, boolean orderIndexMatters, final ConnectionProvider connectionProvider) {
//        this(treeNodeEntityClass, treeNodeEntityClass.getSimpleName(), treePathsEntityClass, treePathsEntityClass.getSimpleName(), orderIndexMatters, connectionProvider);
//    }
//
//    public ClosureTableTreeDao(Class<? extends ExplorerTreeNode> treeNodeEntityClass, String treeNodeEntity, Class<? extendsExplorerTreePath>treePathEntityClass, String treePathEntity, boolean orderIndexMatters, final ConnectionProvider connectionProvider) {
//        removeReferencedNodes = false;
//
////        assert treeNodeEntityClass != null && treeNodeEntity != null && treePathEntityClass != null && treePathEntity != null;
////
////        treeNodeEntityClass = treeNodeEntityClass;
////        treePathEntityClass = treePathEntityClass;
////        treePathEntity = treePathEntity;
////        orderIndexMatters = orderIndexMatters;
//        connectionProvider = connectionProvider;
//    }


    public ClosureTableTreeDao(final boolean orderIndexMatters, final ConnectionProvider connectionProvider) {
        this.removeReferencedNodes = false;
        this.orderIndexMatters = orderIndexMatters;
        this.connectionProvider = connectionProvider;
    }

    public boolean isPersistent(ExplorerTreeNode entity) {
        return entity.getId() != null;
    }

    protected void assertUpdate(ExplorerTreeNode node) {
        if (!isPersistent(node)) {
            throw new IllegalArgumentException("Entity is not persistent! Use addChild() for " + node);
        }
    }

    public boolean isRemoveReferencedNodes() {
        return removeReferencedNodes;
    }

    public void setRemoveReferencedNodes(boolean removeReferencedNodes) {
        this.removeReferencedNodes = removeReferencedNodes;
    }

    public ExplorerTreePath getTreePathEntity(ExplorerTreeNode node) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final List<ExplorerTreePath> result = create
                    .selectFrom(p)
                    .where(p.ANCESTOR.eq(node.getId()))
                    .and(p.DESCENDANT.eq(node.getId()))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());

            if (result.size() <= 0) {
                return null;
            } else if (result.size() > 1) {
                throw new IllegalStateException("Found more than one path for node " + node + ", paths are: " + result);
            } else {
                return result.get(0);
            }

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select p from " + pathEntityName() + " p where p.ancestor = ?1 and p.descendant = ?2");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(node);
//        parameters.add(node);
//        beforeFindQuery("p", queryText, parameters, true);
//        List<ExplorerTreePath> result = session.queryList(queryText.toString(), parameters.toArray());

    }

    public ExplorerTreeNode find(Integer id) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(n)
                    .where(n.ID.eq(id))
                    .fetchOptional()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .orElse(null);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        return session.get(treeNodeEntityClass, id);
    }



    private ExplorerTreeNode createNode(ExplorerTreeNode node) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final int id = create
                    .insertInto(EXPLORERTREENODE)
                    .set(EXPLORERTREENODE.TYPE, node.getType())
                    .set(EXPLORERTREENODE.UUID, node.getUuid())
                    .set(EXPLORERTREENODE.NAME, node.getName())
                    .set(EXPLORERTREENODE.TAGS, node.getTags())
                    .returning(EXPLORERTREENODE.ID)
                    .fetchOne()
                    .getId();
            node.setId(id);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return node;
    }

    public ExplorerTreeNode updateNode(ExplorerTreeNode node) {
        assertUpdate(node);
//        if (shouldCheckUniqueConstraintOnUpdate()) {
//            checkUniqueConstraint(node, getRootForCheckUniqueness(node), node);
//        }

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .update(EXPLORERTREENODE)
                    .set(EXPLORERTREENODE.TYPE, node.getType())
                    .set(EXPLORERTREENODE.UUID, node.getUuid())
                    .set(EXPLORERTREENODE.NAME, node.getName())
                    .set(EXPLORERTREENODE.TAGS, node.getTags())
                    .where(EXPLORERTREENODE.ID.eq(node.getId()))
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return node;
    }

    private ExplorerTreePath createPath(ExplorerTreePath path) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .insertInto(EXPLORERTREEPATH)
                    .set(EXPLORERTREEPATH.ANCESTOR, path.getAncestor())
                    .set(EXPLORERTREEPATH.DESCENDANT, path.getDescendant())
                    .set(EXPLORERTREEPATH.DEPTH, path.getDepth())
                    .set(EXPLORERTREEPATH.ORDERINDEX, path.getOrderIndex())
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return path;
    }

    private ExplorerTreePath updatePath(ExplorerTreePath path) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .update(EXPLORERTREEPATH)
                    .set(EXPLORERTREEPATH.ANCESTOR, path.getAncestor())
                    .set(EXPLORERTREEPATH.DESCENDANT, path.getDescendant())
                    .set(EXPLORERTREEPATH.DEPTH, path.getDepth())
                    .set(EXPLORERTREEPATH.ORDERINDEX, path.getOrderIndex())
                    .where(EXPLORERTREEPATH.ANCESTOR.eq(path.getAncestor()))
                    .and(EXPLORERTREEPATH.DESCENDANT.eq(path.getDescendant()))
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return path;
    }

    public final boolean isRoot(ExplorerTreeNode node) {
        if (!isPersistent(node)) {
            return false;
        } else {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                final int count = create
                        .selectCount()
                        .from(p)
                        .where(p.DESCENDANT.eq(node.getId()))
                        .and(p.DEPTH.gt(0))
                        .fetchOne(0, int.class);

                return 0 == count;

            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }


//            StringBuilder queryText = new StringBuilder("select count(p) from " + pathEntityName() + " p where p.descendant = ?1 and p.depth > 0");
//            List<Object> parameters = new ArrayList<>();
//            parameters.add(node);
//            beforeFindQuery("p", queryText, parameters, true);
//            return 0 == session.queryCount(queryText.toString(), parameters.toArray());
        }
    }

    public ExplorerTreeNode createRoot(ExplorerTreeNode root) {
        return addChild(null, root);
    }

    public List<ExplorerTreeNode> getRoots() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(n)
                    .whereNotExists(create
                            .selectOne()
                            .from(p)
                            .where(p.DESCENDANT.eq(n.ID).and(p.DEPTH.gt(0))))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .collect(Collectors.toList());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select p.ancestor from " + pathEntityName() + " p where p.depth = 0");
//        List<Object> parameters = new ArrayList<>();
//        beforeFindQuery("p", queryText, parameters, true);
//        queryText.append(" and not exists (select 'x' from " + pathEntityName() + " p2 " + " where p2.descendant = p.descendant and p2.depth > 0)");
//        return session.queryList(queryText.toString(), parameters.toArray());
    }

    public synchronized void removeAll() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .deleteFrom(p)
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        Iterator i$ = session.queryList("select p from " + pathEntityName() + " p", (Object[])null).iterator();
//
//        while(i$.hasNext()) {
//           ExplorerTreePath path = (TreePath)i$.next();
//            removePath(path);
//        }

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .deleteFrom(n)
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

//        i$ = session.queryList("select n from " + nodeEntityName() + " n", (Object[])null).iterator();
//
//        while(i$.hasNext()) {
//            ExplorerTreeNode node = i$.next();
//            removeNode(node);
//        }

    }

    public List<ExplorerTreeNode> getTree(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(n)
                    .where(n.ID.in(create
                            .select(p.DESCENDANT)
                            .from(p)
                            .where(p.ANCESTOR.eq(parent.getId()))))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .collect(Collectors.toList());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select p.descendant from " + pathEntityName() + " p where p.ancestor = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryList(queryText.toString(), parameters.toArray());
    }

    public List<ExplorerTreeNode> getTreeCacheable(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final List<ExplorerTreePath> breadthFirstTree = create
                    .selectFrom(p)
                    .where(
                            p.DEPTH.eq(1)
                                    .or(
                                            p.DEPTH.eq(0)
                                                    .and(p.ANCESTOR.eq(parent.getId()))))
                    .and(p.DESCENDANT.in(
                            create
                                    .select(p1.DESCENDANT)
                                    .from(p1)
                                    .where(p1.ANCESTOR.eq(parent.getId()))
                    ))
                    .orderBy(p.DEPTH, p.ANCESTOR, p.ORDERINDEX)
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());

            return newCacheableTreeList(parent, breadthFirstTree);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

//
//        "select p from " + pathEntityName() + " p where (p.depth = 1 or (p.depth = 0 and p.ancestor = ?1)) and p.descendant in (select p1.descendant from \" + pathEntityName() + \" p1 where p1.ancestor = ?2) order by p.depth, p.ancestor, p.orderIndex"
//
//
//
//        "select p1.descendant from " + pathEntityName() + " p1 where p1.ancestor = ?2"
//
//        StringBuilder queryText = new StringBuilder("select p from " + pathEntityName() + " p where (p.depth = 1 or (p.depth = 0 and p.ancestor = ?1))");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        StringBuilder subQueryText = new StringBuilder("select p1.descendant from " + pathEntityName() + " p1 where p1.ancestor = ?2");
//        parameters.add(parent);
//        beforeFindQuery("p1", subQueryText, parameters, true);
//        queryText.append(" and p.descendant in (" + subQueryText + ")");
//        List<ExplorerTreePath> breadthFirstTree = session.queryList(queryText.append(" order by p.depth, p.ancestor, p.orderIndex").toString(), parameters.toArray());
//        return newCacheableTreeList(parent, breadthFirstTree);
    }

    protected CacheableTreeList newCacheableTreeList(ExplorerTreeNode parent, List<ExplorerTreePath> breadthFirstTree) {
        return (new CacheableTreeList()).init(parent, breadthFirstTree);
    }

    public List<ExplorerTreeNode> findSubTree(ExplorerTreeNode parent, List<ExplorerTreeNode> subNodes) {
        CacheableTreeList treeList = (CacheableTreeList) subNodes;
        return treeList.getSubTree(parent);
    }

    public List<ExplorerTreeNode> findDirectChildren(List<ExplorerTreeNode> subNodes) {
        CacheableTreeList treeList = (CacheableTreeList) subNodes;
        return (List) (treeList.size() > 0 ? treeList.getChildren(treeList.getRoot()) : new ArrayList<>());
    }

    public int getChildCount(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectCount()
                    .from(p)
                    .where(p.ANCESTOR.eq(parent.getId()))
                    .and(p.DEPTH.eq(1))
                    .fetchOne(0, int.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select count(p) from " + pathEntityName() + " p where p.ancestor = ?1 and p.depth = 1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryCount(queryText.toString(), parameters.toArray());
    }

    public List<ExplorerTreeNode> getChildren(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(n)
                    .where(n.ID.in(create
                            .select(p.DESCENDANT)
                            .from(p)
                            .where(p.ANCESTOR.eq(parent.getId()))
                            .and(p.DEPTH.eq(1))
                            .orderBy(p.ORDERINDEX)))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .collect(Collectors.toList());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select p.descendant from " + pathEntityName() + " p where p.ancestor = ?1 and p.depth = 1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryList(queryText.append(" order by p.orderIndex").toString(), parameters.toArray());
    }

    public ExplorerTreeNode getRoot(ExplorerTreeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Can not read root for a null node!");
        } else {
            List<ExplorerTreeNode> path = getPath(node);
            return path.size() > 0 ? path.get(0) : node;
        }
    }

    public ExplorerTreeNode getParent(ExplorerTreeNode child) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final List<ExplorerTreeNode> parents = create
                    .selectFrom(n)
                    .where(n.ID.in(create
                            .select(p.ANCESTOR)
                            .from(p)
                            .where(p.DESCENDANT.eq(child.getId()))
                            .and(p.DEPTH.eq(1))))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .collect(Collectors.toList());

            if (parents.size() == 1) {
                return parents.get(0);
            } else if (parents.size() == 0) {
                return null;
            } else {
                throw new IllegalArgumentException("More than one parent found: " + parents);
            }

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

//        StringBuilder queryText = new StringBuilder("select p.ancestor from " + pathEntityName() + " p where p.descendant = ?1 and p.depth = 1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(child);
//        beforeFindQuery("p", queryText, parameters, true);
//        List<ExplorerTreeNode> parents = session.queryList(queryText.toString(), parameters.toArray());
//        if (parents.size() == 1) {
//            return  parents.get(0);
//        } else if (parents.size() == 0) {
//            return null;
//        } else {
//            throw new IllegalArgumentException("More than one parent found: " + parents);
//        }
    }

    public List<ExplorerTreeNode> getPath(ExplorerTreeNode node) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final List<ExplorerTreeNode> path = create
                    .selectFrom(n)
                    .where(n.ID.in(create
                            .select(p.ANCESTOR)
                            .from(p)
                            .where(p.DESCENDANT.eq(node.getId()))
                            .orderBy(p.DEPTH.desc())))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .collect(Collectors.toList());
            path.remove(path.size() - 1);
            return path;

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

//
//        StringBuilder queryText = new StringBuilder("select p.ancestor from " + pathEntityName() + " p where p.descendant = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(node);
//        beforeFindQuery("p", queryText, parameters, true);
//        List<ExplorerTreeNode> path = session.queryList(queryText.append(" order by p.depth desc").toString(), parameters.toArray());
//        path.remove(path.size() - 1);
//        return path;
    }

    public int getLevel(ExplorerTreeNode node) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectCount()
                    .from(p)
                    .where(p.DESCENDANT.eq(node.getId()))
                    .fetchOne(0, int.class) - 1;
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select count(p) from " + pathEntityName() + " p where p.descendant = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(node);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryCount(queryText.toString(), parameters.toArray()) - 1;
    }

    public int size(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectCount()
                    .from(p)
                    .where(p.ANCESTOR.eq(parent.getId()))
                    .fetchOne(0, int.class);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

//        StringBuilder queryText = new StringBuilder("select count(p) from " + pathEntityName() + " p where p.ancestor = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryCount(queryText.toString(), parameters.toArray());
    }

    public boolean isLeaf(ExplorerTreeNode node) {
        return getChildCount(node) <= 0;
    }

    public boolean isEqualToOrChildOf(ExplorerTreeNode child, ExplorerTreeNode parent) {
        return Objects.equals(parent, child) || isChildOf(child, parent);
    }

    public boolean isChildOf(ExplorerTreeNode child, ExplorerTreeNode parent) {
        if (Objects.equals(parent, child)) {
            return false;
        } else {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                final int count = create
                        .selectCount()
                        .from(p)
                        .where(p.ANCESTOR.eq(parent.getId()))
                        .and(p.DESCENDANT.eq(child.getId()))
                        .fetchOne(0, int.class);

                if (count > 1) {
                    throw new IllegalStateException("Ambiguous ancestor/descendant, found " + count + " paths for parent " + parent + " and child " + child);
                } else {
                    return count == 1;
                }
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }


//            StringBuilder queryText = new StringBuilder("select count(p) from " + pathEntityName() + " p where p.ancestor = ?1 and p.descendant = ?2");
//            List<Object> parameters = new ArrayList<>();
//            parameters.add(parent);
//            parameters.add(child);
//            beforeFindQuery("p", queryText, parameters, true);
//            int count = session.queryCount(queryText.toString(), parameters.toArray());
//            if (count > 1) {
//                throw new IllegalStateException("Ambiguous ancestor/descendant, found " + count + " paths for parent " + parent + " and child " + child);
//            } else {
//                return count == 1;
//            }
        }
    }

    public ExplorerTreeNode addChild(ExplorerTreeNode parent, ExplorerTreeNode child) {
        return addChildAt(parent, child, -1);
    }

    public synchronized ExplorerTreeNode addChildAt(ExplorerTreeNode parent, ExplorerTreeNode child, int position) {
        return addChild(parent, null, child, position);
    }

    public synchronized ExplorerTreeNode addChildBefore(ExplorerTreeNode sibling, ExplorerTreeNode child) {
        return addChild(null, sibling, child, -1);
    }

    public synchronized void remove(ExplorerTreeNode node) {
        if (node != null && isPersistent(node)) {
            removeTree(node.getId());
        } else {
            throw new IllegalArgumentException("Node to remove is null or not persistent: " + node);
        }
    }

    public void move(ExplorerTreeNode node, ExplorerTreeNode parent) {
        moveTo(node, parent, -1);
    }

    public synchronized void moveTo(ExplorerTreeNode node, ExplorerTreeNode parent, int position) {
        move(node, parent, position, null);
    }

    public synchronized void moveBefore(ExplorerTreeNode node, ExplorerTreeNode sibling) {
        move(node, null, -1, sibling);
    }

    public synchronized void moveToBeRoot(ExplorerTreeNode child) {
        move(child, null, -1, null);
    }

    public ExplorerTreeNode copy(ExplorerTreeNode node, ExplorerTreeNode parent, ExplorerTreeNode copiedNodeTemplate) {
        return copyTo(node, parent, -1, copiedNodeTemplate);
    }

    public synchronized ExplorerTreeNode copyTo(ExplorerTreeNode node, ExplorerTreeNode parent, int position, ExplorerTreeNode copiedNodeTemplate) {
        return copy(node, parent, position, null, copiedNodeTemplate);
    }

    public synchronized ExplorerTreeNode copyBefore(ExplorerTreeNode node, ExplorerTreeNode sibling, ExplorerTreeNode copiedNodeTemplate) {
        return copy(node, null, -1, sibling, copiedNodeTemplate);
    }

    public synchronized ExplorerTreeNode copyToBeRoot(ExplorerTreeNode child, ExplorerTreeNode copiedNodeTemplate) {
        return copy(child, null, -1, null, copiedNodeTemplate);
    }

    public List<ExplorerTreeNode> find(ExplorerTreeNode parent, Map<String, Object> criteria) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            Condition condition = null;
            if (parent != null) {
                condition = and(p.ANCESTOR.eq(parent.getId()));
            }

            return create
                    .select(n.ID, n.TYPE, n.UUID, n.NAME, n.TAGS)
                    .from(n)
                    .join(p).on(p.DESCENDANT.eq(n.ID))
                    .where(condition)
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.component1(), r.component2(), r.component3(), r.component4(), r.component5()))
                    .collect(Collectors.toList());

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select n from " + nodeEntityName() + " n, " + pathEntityName() + " p " + " where p.descendant = n");
//        List<Object> parameters = new ArrayList<>();
//        if (parent != null) {
//            queryText.append(" and p.ancestor = ?1");
//            parameters.add(parent);
//        }
//
//        queryText.append(" ");
//        QueryBuilderUtil.appendCriteria(true, queryText, "n", parameters, criteria, true);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryList(queryText.toString(), parameters.toArray());
    }

//    protected void beforeFindQuery(String tableAlias, StringBuilder queryText, List<Object> parameters, boolean whereWasAppended) {
//    }

//    protected String pathEntityName() {
//        return treePathEntity;
//    }
//
//    protected ExplorerTreePath newTreePathInstance() {
//        try {
//            return ;
//        } catch (Exception var2) {
//            throw new RuntimeException(var2);
//        }
//    }

//    protected Object create(TreePath path) {
//        return session.create(path);
//    }

    protected boolean shouldCloseGapOnRemove() {
        return true;
    }

    protected void removeTree(Integer parentId) {
        boolean closeGap = shouldCloseGapOnRemove();
        List<ExplorerTreePath> pathSiblings = closeGap ? getAllTreePathSiblings(parentId) : null;
        Set<Integer> nodesToRemove = new HashSet<>();
        int orderIndex = -1;

        final List<ExplorerTreePath> paths = getPathsToRemove(parentId);
        for (ExplorerTreePath path : paths) {
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

    protected void removePath(ExplorerTreePath path) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create
                    .deleteFrom(p)
                    .where(p.ANCESTOR.eq(path.getAncestor()))
                    .and(p.DESCENDANT.eq(path.getDescendant()))
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        session.delete(path);
    }

    protected void removeNode(ExplorerTreeNode nodeToRemove) {
        removeNode(nodeToRemove.getId());
    }

    protected void removeNode(Integer id) {
        if (isRemoveReferencedNodes()) {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                create
                        .deleteFrom(n)
                        .where(n.ID.eq(id))
                        .execute();
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

//        if (isRemoveReferencedNodes()) {
//            session.delete(nodeToRemove);
//        }

    }

    protected final List<ExplorerTreePath> getPathsToRemove(Integer nodeId) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(p)
                    .where(p.DESCENDANT.in(create
                            .select(p1.DESCENDANT)
                            .from(p1)
                            .where(p1.ANCESTOR.eq(nodeId))))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select p from " + pathEntityName() + " p where p.descendant in (" + "select p1.descendant from " + pathEntityName() + " p1 where p1.ancestor = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(node);
//        beforeFindQuery("p1", queryText, parameters, true);
//        queryText.append(")");
//        return session.queryList(queryText.toString(), parameters.toArray());
    }

    private ExplorerTreeNode addChild(ExplorerTreeNode parent, ExplorerTreeNode sibling, ExplorerTreeNode child, int orderIndex) {
        if (child == null) {
            throw new IllegalArgumentException("Node to add is null!");
        } else if (isPersistent(child) && exists(child)) {
            throw new IllegalArgumentException("Node is already part of tree: " + child);
        } else {
            assertInsertParameters(parent, sibling, orderIndex);
//            checkUniqueness(Arrays.asList(child), treeActionLocation(parent, sibling, ActionType.INSERT));
            boolean relatedNodeIsParent = parent != null;
            List<ExplorerTreePath> pathsToClone = new ArrayList<>();
            orderIndex = getPositionAndPathsToConnectSubTree(relatedNodeIsParent, orderIndex, parent, sibling, pathsToClone);
            if (!isPersistent(child)) {
                child = createNode(child);
            }

            insertSelfReference(child);
            clonePaths(child.getId(), 0, orderIndex, pathsToClone, relatedNodeIsParent);
            return child;
        }
    }

    private boolean exists(ExplorerTreeNode node) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final int count = create
                    .selectCount()
                    .from(p)
                    .where(p.DESCENDANT.eq(node.getId()))
                    .fetchOne(0, int.class);
            return count > 0;

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select count(p) from " + pathEntityName() + " p where p.descendant = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(node);
//        beforeFindQuery("p", queryText, parameters, true);
//        return 0 < session.queryCount(queryText.toString(), parameters.toArray());
    }

    private void insertSelfReference(ExplorerTreeNode child) {
        final ExplorerTreePath newPath = new ExplorerTreePath(child.getId(), child.getId(), 0, -1);
        createPath(newPath);
    }

    private void move(ExplorerTreeNode nodeToMove, ExplorerTreeNode newParent, int position, ExplorerTreeNode sibling) {
        assertCopyOrMoveParameters(nodeToMove, newParent, position, sibling);
//        checkUniqueness(Arrays.asList(nodeToMove), treeActionLocation(newParent, sibling, ActionType.MOVE));
        disconnectSubTree(nodeToMove);
        if (newParent != null || sibling != null) {
            List<ExplorerTreePath> childPaths = getPathsIntoSubtree(nodeToMove);
            connectSubTree(newParent, position, sibling, childPaths);
        }

    }

    private void disconnectSubTree(ExplorerTreeNode node) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            final List<ExplorerTreePath> pathsToRemove = create
                    .selectFrom(p)
                    .where(p.DESCENDANT.in(create
                            .select(p1.DESCENDANT)
                            .from(p1)
                            .where(p1.ANCESTOR.eq(node.getId()))))
                    .and(p.ANCESTOR.notIn(create
                            .select(p2.DESCENDANT)
                            .from(p2)
                            .where(p2.ANCESTOR.eq(node.getId()))))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());

            List<ExplorerTreePath> pathSiblings = getAllTreePathSiblings(node.getId());
            int oldPosition = -1;

            for (final ExplorerTreePath path : pathsToRemove) {
                removePath(path);
                if (path.getDepth() == 1) {
                    oldPosition = path.getOrderIndex();
                }
            }

            closeGap(pathSiblings, oldPosition);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        String removeQueryText = "select p from " + pathEntityName() + " p " + "  where p.descendant in" + "    (select p1.descendant from " + pathEntityName() + " p1 where p1.ancestor = ?1)" + "  and p.ancestor not in " + "    (select p2.descendant from " + pathEntityName() + " p2 where p2.ancestor = ?2)";
//        List<ExplorerTreePath> pathsToRemove = session.queryList(removeQueryText, new Object[]{node, node});
//        List<ExplorerTreePath> pathSiblings = getAllTreePathSiblings(node);
//        int oldPosition = -1;
//
//        ExplorerTreePath path;
//        for (Iterator i$ = pathsToRemove.iterator(); i$.hasNext(); session.delete(path)) {
//            path = (TreePath) i$.next();
//            if (path.getDepth() == 1) {
//                oldPosition = path.getOrderIndex();
//            }
//        }
//
//        closeGap(pathSiblings, oldPosition);
    }

    private List<ExplorerTreePath> getPathsIntoSubtree(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(p)
                    .where(p.ANCESTOR.eq(parent.getId()))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder queryText = new StringBuilder("select p from " + pathEntityName() + " p where p.ancestor = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        beforeFindQuery("p", queryText, parameters, true);
//        return session.queryList(queryText.toString(), parameters.toArray());
    }

    private ExplorerTreeNode copy(ExplorerTreeNode node, ExplorerTreeNode newParent, int position, ExplorerTreeNode sibling, ExplorerTreeNode copiedNodeTemplate) {
        assertCopyOrMoveParameters(node, newParent, position, sibling);
        List<ExplorerTreePath> pathsToCopy = getSubTreePathsToCopy(node);
        List<ExplorerTreePath> childPaths = new ArrayList<>();
        ExplorerTreeNode copiedNode = copySubTree(pathsToCopy, node, copiedNodeTemplate, childPaths);
        if (newParent != null || sibling != null) {
            connectSubTree(newParent, position, sibling, childPaths);
        }

        return copiedNode;
    }

    private List<ExplorerTreePath> getSubTreePathsToCopy(ExplorerTreeNode parent) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(p)
                    .where(p.DESCENDANT.in(create
                            .select(p2.DESCENDANT)
                            .from(p2)
                            .where(p2.ANCESTOR.eq(parent.getId()))))
                    .and(p.ANCESTOR.in(create
                            .select(p2.DESCENDANT)
                            .from(p2)
                            .where(p2.ANCESTOR.eq(parent.getId()))))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        StringBuilder subTreeBelowNode = new StringBuilder("select p2.descendant from " + pathEntityName() + " p2 where p2.ancestor = ?1");
//        List<Object> parameters = new ArrayList<>();
//        parameters.add(parent);
//        beforeFindQuery("p2", subTreeBelowNode, parameters, true);
//        int paramCount = parameters.size();
//        parameters.addAll(new ArrayList(parameters));
//        StringBuilder copyQueryText = new StringBuilder("select p from " + pathEntityName() + " p " + "  where p.descendant in (" + subTreeBelowNode + ")" + "  and p.ancestor in (" + shiftParams(subTreeBelowNode, paramCount) + ")");
//        return session.queryList(copyQueryText.toString(), parameters.toArray());
    }

//    private String shiftParams(StringBuilder queryBuffer, int shiftCount) {
//        String query = queryBuffer.toString();
//
//        for (int i = shiftCount; i > 0; --i) {
//            query = query.replace("?" + i, "?" + (i + shiftCount));
//        }
//
//        return query;
//    }

    private ExplorerTreeNode copySubTree(List<ExplorerTreePath> pathsToCopy, ExplorerTreeNode node, ExplorerTreeNode copiedNodeTemplate, List<ExplorerTreePath> childPaths) {
        Map<Integer, ExplorerTreeNode> cloneMap = new HashMap<>();
        ExplorerTreeNode copiedNode = node;//cloneNodesCheckUniqueness(pathsToCopy, node, copiedNodeTemplate, cloneMap);
        Map<Integer, ExplorerTreeNode> mergedCloneMap = new HashMap<>();
//        Iterator i$ = cloneMap.entrySet().iterator();

        for (final Entry<Integer, ExplorerTreeNode> entry : cloneMap.entrySet()) {
            Integer originalId = entry.getKey();
            ExplorerTreeNode clonedNode = entry.getValue();
            ExplorerTreeNode mergedClonedNode = createNode(clonedNode);
            mergedCloneMap.put(originalId, mergedClonedNode);
            if (clonedNode == copiedNode) {
                copiedNode = mergedClonedNode;
            }
        }

        for (final ExplorerTreePath pathToCopy : pathsToCopy) {
            ExplorerTreePath clonedPath = new ExplorerTreePath(pathToCopy.getAncestor(), pathToCopy.getDescendant(), pathToCopy.getDepth(), pathToCopy.getOrderIndex());
            ExplorerTreePath mergedClonedPath = createPath(clonedPath);
            if (Objects.equals(pathToCopy.getAncestor(), node.getId())) {
                childPaths.add(mergedClonedPath);
            }
        }

        return copiedNode;
    }

//    private ExplorerTreeNode cloneNodesCheckUniqueness(List<ExplorerTreePath> pathsToCopy, ExplorerTreeNode node, ExplorerTreeNode copiedNodeTemplate, Map<Integer, ExplorerTreeNode> cloneMap) {
//        ExplorerTreeNode copiedNode = null;
////        List<ExplorerTreeNode> uniqueCheckList = new ArrayList<>();
//        Iterator<ExplorerTreePath> i$ = pathsToCopy.iterator();
//
//        while (true) {
//            Integer nodeToCopy;
//            do {
//                if (!i$.hasNext()) {
//                    assert copiedNode != null : "The node to copy was not in paths: " + pathsToCopy;
//
////                    checkUniqueness(uniqueCheckList, treeActionLocation);
//                    return copiedNode;
//                }
//
//                ExplorerTreePath pathToCopy =  i$.next();
//                nodeToCopy = pathToCopy.getDescendant();
//            } while (cloneMap.containsKey(nodeToCopy));
//
//            ExplorerTreeNode clone;
//            if (copiedNode == null && Objects.equals(nodeToCopy, node.getId())) {
//                clone = copiedNodeTemplate != null ? copiedNodeTemplate : nodeToCopy.clone();
//                copiedNode = clone;
////                uniqueCheckList.add(0, clone);
//            } else {
//                clone = nodeToCopy.clone();
////                uniqueCheckList.add(clone);
//            }
//
//            assert clone != null : "Need clone() to copy a node!";
//
//            cloneMap.put(nodeToCopy, clone);
////            applyCopiedNodeRenamer(clone);
//        }
//    }

    private void connectSubTree(ExplorerTreeNode newParent, int position, ExplorerTreeNode sibling, List<ExplorerTreePath> childPaths) {
        boolean relatedNodeIsParent = newParent != null;
        List<ExplorerTreePath> pathsToClone = new ArrayList<>();
        position = getPositionAndPathsToConnectSubTree(relatedNodeIsParent, position, newParent, sibling, pathsToClone);

        for (final ExplorerTreePath childPath : childPaths) {
            clonePaths(childPath.getDescendant(), childPath.getDepth(), position, pathsToClone, relatedNodeIsParent);
        }
    }

    private int getPositionAndPathsToConnectSubTree(boolean relatedNodeIsParent, int position, ExplorerTreeNode parent, ExplorerTreeNode sibling, List<ExplorerTreePath> pathsToClone) {
        assert pathsToClone != null && pathsToClone.size() <= 0;

        assert !relatedNodeIsParent || parent != null;

        if (relatedNodeIsParent) {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                final List<ExplorerTreePath> paths = create
                        .selectFrom(p)
                        .where(p.DESCENDANT.eq(parent.getId()))
                        .fetch()
                        .stream()
                        .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                        .collect(Collectors.toList());
                pathsToClone.addAll(paths);
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        } else if (sibling != null) {
            try (final Connection connection = connectionProvider.getConnection()) {
                final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
                final List<ExplorerTreePath> paths = create
                        .selectFrom(p)
                        .where(p.DESCENDANT.eq(parent.getId()))
                        .and(p.DEPTH.gt(0))
                        .orderBy(p.DEPTH)
                        .fetch()
                        .stream()
                        .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                        .collect(Collectors.toList());
                pathsToClone.addAll(paths);

                if (pathsToClone.size() <= 0) {
                    throw new IllegalArgumentException("Sibling seems not to be a child but a root: " + sibling);
                }

                position = pathsToClone.get(0).getOrderIndex();

                assert position >= 0 : "Position of first path is not valid: " + pathsToClone;

            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

//        String queryText = "select p from " + pathEntityName() + " p where p.descendant = ?1";
//        if (relatedNodeIsParent) {
//            Condition condition = and(p.DESCENDANT.eq(parent.getId());
//
//            pathsToClone.addAll(session.queryList(queryText, new Object[]{parent}));
//        } else if (sibling != null) {
//            pathsToClone.addAll(session.queryList(queryText + " and p.depth > 0 order by p.depth", new Object[]{sibling}));
//            if (pathsToClone.size() <= 0) {
//                throw new IllegalArgumentException("Sibling seems not to be a child but a root: " + sibling);
//            }
//
//            position = ((TreePath) pathsToClone.get(0)).getOrderIndex();
//
//            assert position >= 0 : "Position of first path is not valid: " + pathsToClone;
//        }

        return position;
    }

    private void clonePaths(final Integer childId,
                            final int addToDepth,
                            final int position,
                            final List<ExplorerTreePath> pathsToClone,
                            final boolean isParentPaths) {
        for (final ExplorerTreePath pathToClone : pathsToClone) {
            final int depth = pathToClone.getDepth() + addToDepth + (isParentPaths ? 1 : 0);
            final int newPosition = depth == 1 ? createGap(isParentPaths ? pathToClone.getDescendant() : pathToClone.getAncestor(), position) : -1;
            final ExplorerTreePath newPath = new ExplorerTreePath(pathToClone.getAncestor(), childId, depth, newPosition);
            createPath(newPath);
        }
    }

    private int createGap(Integer parentId, int position) {
        if (!orderIndexMatters) {
            return 0;
        } else {
            List<ExplorerTreePath> children = getAllDirectTreePathChildren(parentId);
            if (position == -1) {
                return children.size();
            } else {
                for (int i = children.size() - 1; i >= position; --i) {
                    final ExplorerTreePath treePath = children.get(i);
                    treePath.setOrderIndex(i + 1);
                    updatePath(treePath);
                }

                return position;
            }
        }
    }

    private void closeGap(List<ExplorerTreePath> siblings, int removedPosition) {
        if (orderIndexMatters) {
            if (removedPosition >= 0) {
                for (int i = removedPosition; i < siblings.size(); ++i) {
                    final ExplorerTreePath pathSibling = siblings.get(i);
                    pathSibling.setOrderIndex(i);
                    updatePath(pathSibling);
                }
            }

        }
    }

    private List<ExplorerTreePath> getAllDirectTreePathChildren(Integer parentId) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(p)
                    .where(p.ANCESTOR.eq(parentId))
                    .and(p.DEPTH.eq(1))
                    .orderBy(p.ORDERINDEX)
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

//
//
//        String queryText = "select p from " + pathEntityName() + " p where p.ancestor = ?1 and p.depth = 1 order by p.orderIndex";
//        return session.queryList(queryText, new Object[]{parent});
    }

    private List<ExplorerTreePath> getAllTreePathSiblings(Integer nodeId) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .selectFrom(p)
                    .where(p.DEPTH.eq(1))
                    .and(p.DESCENDANT.notEqual(nodeId))
                    .and(p.ANCESTOR.in(create
                            .select(p2.ANCESTOR)
                            .from(p2)
                            .where(p2.DESCENDANT.eq(nodeId))
                            .and(p2.DEPTH.eq(1))))
                    .orderBy(p.ORDERINDEX)
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreePath(r.getAncestor(), r.getDescendant(), r.getDepth(), r.getOrderindex()))
                    .collect(Collectors.toList());
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        String queryText = "select p from " + pathEntityName() + " p where p.depth = 1 and p.descendant != ?1 and p.ancestor in " + "    (select p2.ancestor from " + pathEntityName() + " p2 where p2.descendant = ?2 and p2.depth = 1)" + "  order by p.orderIndex";
//        return session.queryList(queryText, new Object[]{node, node});
    }

//    private TreeActionLocation<ExplorerTreeNode> treeActionLocation(ExplorerTreeNode parent, ExplorerTreeNode sibling) {
//        RelatedNodeType relatedNodeType = sibling != null ? RelatedNodeType.SIBLING : (parent != null ? RelatedNodeType.PARENT : null);
//        ExplorerTreeNode relatedNode = sibling != null ? sibling : (parent != null ? parent : null);
//        return new TreeActionLocation(getRootForCheckUniqueness(relatedNode), relatedNodeType, relatedNode, actionType);
//    }
//
//    private ExplorerTreeNode getRootForCheckUniqueness(ExplorerTreeNode node) {
//        return getUniqueTreeConstraint() != null && node != null && isPersistent(node) ? getRoot(node) : null;
//    }

    public ExplorerTreeNode findByUUID(final String uuid) {
        if (uuid == null) {
            return null;
        }

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final List<ExplorerTreeNode> list = create
                    .selectFrom(n)
                    .where(n.UUID.eq(uuid))
                    .fetch()
                    .stream()
                    .map(r -> new ExplorerTreeNode(r.getId(), r.getType(), r.getUuid(), r.getName(), r.getTags()))
                    .collect(Collectors.toList());

            if (list.size() == 0) {
                return null;
            }

            if (list.size() > 1) {
                throw new RuntimeException("Found more than 1 tree node with uuid=" + uuid);
            }

            return list.get(0);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }


//        final List<ExplorerTreeNode> list = (List<ExplorerTreeNode>) this.session.queryList("select n from " + ExplorerTreeNode.class.getSimpleName() + " n where n.uuid = ?1", new String[]{uuid});
//        if (list == null || list.size() == 0) {
//            return null;
//        }
//
//        if (list.size() > 1) {
//            throw new RuntimeException("Found more than 1 tree node with uuid=" + uuid);
//        }
//
//        return list.get(0);
    }

    private void assertInsertParameters(ExplorerTreeNode parent, ExplorerTreeNode sibling, int position) {
        assert parent == null || sibling == null;

        assert sibling == null || position == -1;

    }

    private void assertCopyOrMoveParameters(ExplorerTreeNode node, ExplorerTreeNode newParent, int position, ExplorerTreeNode sibling) {
        if (node != null && isPersistent(node)) {
            assertInsertParameters(newParent, sibling, position);
//            copyOrMovePreconditions(newParent != null ? newParent : sibling, node);
        } else {
            throw new IllegalArgumentException("Node to move is null or not persistent: " + node);
        }
    }
}
