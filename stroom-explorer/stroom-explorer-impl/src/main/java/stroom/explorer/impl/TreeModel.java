package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeModel {
    private final long creationTime = System.currentTimeMillis();
    private final Map<String, ExplorerNode> parentMap = new HashMap<>();
    private final Map<String, List<ExplorerNode>> childMap = new HashMap<>();

    public void add(final ExplorerNode parent, final ExplorerNode child) {
        parentMap.put(child != null ? child.getUuid() : null, parent);
        childMap.computeIfAbsent(parent != null ? parent.getUuid() : null, k -> new ArrayList<>()).add(child);
    }

    long getCreationTime() {
        return creationTime;
    }

    Set<String> getAllParents() {
        return childMap.keySet();
    }

    ExplorerNode getParent(final String childUuid) {
        if (childUuid == null) {
            return null;
        }
        return parentMap.get(childUuid);
    }

    ExplorerNode getParent(final ExplorerNode child) {
        if (child == null) {
            return null;
        }
        return parentMap.get(child.getUuid());
    }

    List<ExplorerNode> getChildren(final ExplorerNode parent) {
        if (parent == null) {
            return childMap.get(null);
        }
        return childMap.get(parent.getUuid());
    }

    Collection<List<ExplorerNode>> values() {
        return childMap.values();
    }
}
