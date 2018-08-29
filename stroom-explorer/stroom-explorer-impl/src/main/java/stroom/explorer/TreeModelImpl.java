package stroom.explorer;

import stroom.explorer.shared.ExplorerNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TreeModelImpl implements TreeModel {
    private final Map<ExplorerNode, ExplorerNode> parentMap = new HashMap<>();
    private final Map<ExplorerNode, List<ExplorerNode>> childMap = new HashMap<>();

    @Override
    public void add(final ExplorerNode parent, final ExplorerNode child) {
        parentMap.put(child, parent);
        childMap.computeIfAbsent(parent, k -> new ArrayList<>()).add(child);
    }

    @Override
    public Map<ExplorerNode, ExplorerNode> getParentMap() {
        return parentMap;
    }

    @Override
    public Map<ExplorerNode, List<ExplorerNode>> getChildMap() {
        return childMap;
    }
}
