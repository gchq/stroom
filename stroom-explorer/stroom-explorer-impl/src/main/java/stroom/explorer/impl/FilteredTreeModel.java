package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;

import java.util.ArrayList;
import java.util.List;

public class FilteredTreeModel extends AbstractTreeModel<ExplorerNodeKey> {

    public FilteredTreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    @Override
    public void add(final ExplorerNode parent, final ExplorerNode child) {
        parentMap.put(child != null
                ? child.getUniqueKey()
                : null, parent);
        childMap.computeIfAbsent(parent != null
                ? parent.getUniqueKey()
                : null, k -> new ArrayList<>()).add(child);
    }

    public ExplorerNode getParent(final ExplorerNode child) {
        if (child == null) {
            return null;
        }
        return parentMap.get(child.getUniqueKey());
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        if (parent == null) {
            return childMap.get(null);
        }
        return childMap.get(parent.getUniqueKey());
    }
}
