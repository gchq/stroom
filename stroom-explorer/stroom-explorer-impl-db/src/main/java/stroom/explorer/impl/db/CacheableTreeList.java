package stroom.explorer.impl.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class CacheableTreeList extends ArrayList<ExplorerTreeNode> {
    private ExplorerTreeNode root;
    private Map<Integer, List<ExplorerTreeNode>> hierarchy;
    private boolean ready;

    CacheableTreeList() {
    }

    protected CacheableTreeList(ExplorerTreeNode root, Map<Integer, List<ExplorerTreeNode>> hierarchy) {
        assert root != null && hierarchy != null;

        this.root = root;
        this.hierarchy = hierarchy;
    }

    CacheableTreeList init(ExplorerTreeNode root, List<ExplorerTreePath> paths) {
        assert paths.size() <= 0 || ((ExplorerTreePath)paths.get(0)).getAncestor().equals(((ExplorerTreePath)paths.get(0)).getAncestor()) : "Incorrect ExplorerTreePath list, does not contain root at position 0: " + paths.get(0);

        this.root = root;
        this.hierarchy = new HashMap(paths.size());
        this.loopChildren(paths, paths);

        assert this.size() == paths.size() : "Something went wrong on building tree-list with " + paths.size() + " paths, having only " + this.size() + " nodes!";

        this.ready = true;
        return this;
    }

    public ExplorerTreeNode getRoot() {
        return this.root;
    }

    public List<ExplorerTreeNode> getChildren(ExplorerTreeNode parent) {
        if (parent.getId() == null) {
            throw new IllegalArgumentException("Parent to retrieve children for is not persistent: " + parent);
        } else {
            return (List)this.hierarchy.get(parent.getId());
        }
    }

    public List<ExplorerTreeNode> getSubTree(ExplorerTreeNode parent) {
        if (parent.getId() == null) {
            throw new IllegalArgumentException("Parent to retrieve sub-tree for is not persistent: " + parent);
        } else {
            CacheableTreeList subTree = this.newCacheableTreeList(parent);
            this.addSubTreeRecursive(parent, this.getChildren(parent), subTree);
            subTree.ready = true;
            return subTree;
        }
    }

    protected CacheableTreeList newCacheableTreeList(ExplorerTreeNode parent) {
        return new CacheableTreeList(parent, new HashMap());
    }

    protected void putToHierarchy(CacheableTreeList treeList, ExplorerTreePath path, ExplorerTreeNode node, List<ExplorerTreeNode> children) {
        treeList.hierarchy.put(node.getId(), children);
    }

    private void loopChildren(List<ExplorerTreePath> paths, List<ExplorerTreePath> allPaths) {
        Iterator i$ = paths.iterator();

        while(i$.hasNext()) {
            ExplorerTreePath path = (ExplorerTreePath)i$.next();
            this.checkDependency(path, allPaths);
        }

    }

    private void checkDependency(ExplorerTreePath path, List<ExplorerTreePath> allPaths) {
//        List<ExplorerTreeNode> children = this.hierarchy.get(path.getDescendant());
//        if (children == null) {
//            List<ExplorerTreeNode> children = new ArrayList();
//            ExplorerTreeNode node = path.getDescendant();
//            this.putToHierarchy(this, path, node, children);
//            this.add(node);
//            List<ExplorerTreePath> childPaths = this.getChildPaths(node, allPaths);
//            Iterator i$ = childPaths.iterator();
//
//            while(i$.hasNext()) {
//                ExplorerTreePath childPath = (ExplorerTreePath)i$.next();
//                children.add(childPath.getDescendant());
//            }
//
//            this.loopChildren(childPaths, allPaths);
//        }

    }

    private List<ExplorerTreePath> getChildPaths(ExplorerTreeNode parent, List<ExplorerTreePath> paths) {
        List<ExplorerTreePath> childPaths = new ArrayList();
        Iterator i$ = paths.iterator();

        while(i$.hasNext()) {
            ExplorerTreePath path = (ExplorerTreePath)i$.next();
            if (path.getAncestor().equals(parent) && path.getDepth() == 1) {
                childPaths.add(path);
            }
        }

        return childPaths;
    }

    private void addSubTreeRecursive(ExplorerTreeNode parent, List<ExplorerTreeNode> children, CacheableTreeList subTree) {
        subTree.add(parent);
        this.putToHierarchy(subTree, (ExplorerTreePath)null, parent, children);
        Iterator i$ = children.iterator();

        while(i$.hasNext()) {
            ExplorerTreeNode node = (ExplorerTreeNode)i$.next();
            this.addSubTreeRecursive(node, this.getChildren(node), subTree);
        }

    }

    public boolean add(ExplorerTreeNode n) {
        if (this.ready) {
            throw new RuntimeException("Can not modify this list!");
        } else {
            return super.add(n);
        }
    }

    public void add(int index, ExplorerTreeNode n) {
        throw new RuntimeException("Can not modify this list!");
    }

    public ExplorerTreeNode remove(int index) {
        throw new RuntimeException("Can not modify this list!");
    }

    public boolean remove(Object o) {
        throw new RuntimeException("Can not modify this list!");
    }

    public boolean removeAll(Collection<?> c) {
        throw new RuntimeException("Can not modify this list!");
    }

    public boolean addAll(Collection<? extends ExplorerTreeNode> c) {
        throw new RuntimeException("Can not modify this list!");
    }

    public boolean addAll(int index, Collection<? extends ExplorerTreeNode> c) {
        throw new RuntimeException("Can not modify this list!");
    }

    public void clear() {
        throw new RuntimeException("Can not modify this list!");
    }

    public boolean retainAll(Collection<?> c) {
        throw new RuntimeException("Can not modify this list!");
    }

    public ExplorerTreeNode set(int index, ExplorerTreeNode element) {
        throw new RuntimeException("Can not modify this list!");
    }
}
