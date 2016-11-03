package stroom.explorer.server;

import stroom.explorer.shared.ExplorerData;

import java.util.*;

public class TreeModelImpl implements TreeModel {
    private final Map<ExplorerData, ExplorerData> parentMap = new HashMap<>();
    private final Map<ExplorerData, List<ExplorerData>> childMap = new HashMap<>();

    @Override
    public void add(final ExplorerData parent, final ExplorerData child) {
        parentMap.put(child, parent);

        List<ExplorerData> children = childMap.get(parent);
        if (children == null) {
            children = new ArrayList<>();
            childMap.put(parent, children);
        }
        children.add(child);
    }

    @Override
    public Map<ExplorerData, ExplorerData> getParentMap() {
        return parentMap;
    }

    @Override
    public Map<ExplorerData, List<ExplorerData>> getChildMap() {
        return childMap;
    }
}
