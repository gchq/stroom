package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeModel {
    private final long creationTime = System.currentTimeMillis();
    private final Map<ExplorerNode, ExplorerNode> parentMap = new HashMap<>();
    private final Map<ExplorerNode, List<ExplorerNode>> childMap = new HashMap<>();

    public void add(final ExplorerNode parent, final ExplorerNode child) {
        parentMap.put(child, parent);
        childMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
    }

    long getCreationTime() {
        return creationTime;
    }

    Map<ExplorerNode, ExplorerNode> getParentMap() {
        return parentMap;
    }

    Map<ExplorerNode, List<ExplorerNode>> getChildMap() {
        return childMap;
    }
}
