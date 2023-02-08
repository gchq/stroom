package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractTreeModel<K> {

    protected final long id;
    protected final long creationTime;
    protected Map<K, ExplorerNode> parentMap = new HashMap<>();
    protected Map<K, List<ExplorerNode>> childMap = new HashMap<>();

    public AbstractTreeModel(final long id, final long creationTime) {
        this.id = id;
        this.creationTime = creationTime;
    }

    public long getId() {
        return id;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public Set<K> getAllParents() {
        return new HashSet<>(childMap.keySet());
    }

    public ExplorerNode getParent(final K childKey) {
        if (childKey == null) {
            return null;
        }
        return parentMap.get(childKey);
    }

    public Collection<List<ExplorerNode>> values() {
        return childMap.values();
    }

    public abstract void add(final ExplorerNode parent, final ExplorerNode child);

    public abstract ExplorerNode getParent(final ExplorerNode child);

    public abstract List<ExplorerNode> getChildren(final ExplorerNode parent);
}
