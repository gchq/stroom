package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public abstract class AbstractTreeModel<K> {

    protected final long id;
    protected final long creationTime;
    protected Map<K, ExplorerNode> parentMap = new HashMap<>();
    protected Map<K, Set<ExplorerNode>> childMap = new HashMap<>();

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
        return new LinkedHashSet<>(childMap.keySet());
    }

    public ExplorerNode getParent(final K childKey) {
        if (childKey == null) {
            return null;
        }
        return parentMap.get(childKey);
    }

    public void sort(final ToIntFunction<ExplorerNode> priorityExtractor) {
        final Map<K, Set<ExplorerNode>> newChildMap = new HashMap<>();
        childMap.forEach((key, children) -> newChildMap.put(key, children
                .stream()
                .sorted(Comparator
                        .comparingInt(priorityExtractor)
                        .thenComparing(ExplorerNode::getType)
                        .thenComparing(ExplorerNode::getName))
                .collect(Collectors.toCollection(LinkedHashSet::new))));

        childMap = newChildMap;
    }

    public abstract void add(final ExplorerNode parent, final ExplorerNode child);

    public abstract ExplorerNode getParent(final ExplorerNode child);

    public abstract List<ExplorerNode> getChildren(final ExplorerNode parent);
}
