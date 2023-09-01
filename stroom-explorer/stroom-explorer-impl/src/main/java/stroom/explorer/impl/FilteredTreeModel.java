package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.NullSafe;

public class FilteredTreeModel extends AbstractTreeModel<ExplorerNodeKey> {

    public FilteredTreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    @Override
    ExplorerNodeKey getNodeKey(final ExplorerNode node) {
        return NullSafe.get(node, ExplorerNode::getUniqueKey);
    }

    @Override
    public FilteredTreeModel clone() {
        final AbstractTreeModel<ExplorerNodeKey> clone = super.clone();
        return (FilteredTreeModel) clone;
    }
}
