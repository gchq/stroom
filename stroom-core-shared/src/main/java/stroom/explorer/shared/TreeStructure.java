package stroom.explorer.shared;

import stroom.util.shared.SharedObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TreeStructure implements SharedObject {
    private ExplorerData root;
    private Map<ExplorerData, ExplorerData> parentMap = new HashMap<>();
    private Map<ExplorerData, List<ExplorerData>> childMap = new HashMap<>();

    public TreeStructure() {
        // Default constructor necessary for GWT serialisation.
    }

    public void add(final ExplorerData parent, final ExplorerData child) {
        if (parent == null) {
            root = child;
        }

        parentMap.put(child, parent);

        List<ExplorerData> children = childMap.get(parent);
        if (children == null) {
            children = new ArrayList<>();
            childMap.put(parent, children);
        }
        children.add(child);
    }

    public ExplorerData getRoot() {
        return root;
    }

    public ExplorerData getParent(final ExplorerData child) {
        return parentMap.get(child);
    }

    public List<ExplorerData> getChildren(final ExplorerData parent) {
        return childMap.get(parent);
    }
}
