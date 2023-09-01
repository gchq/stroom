package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.NullSafe;

import java.util.List;

public class TreeModel extends AbstractTreeModel<String> implements Cloneable {

    public TreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    public List<DocRef> getChildren(final DocRef parent) {
        final String parentUuid = parent != null ? parent.getUuid() : null;
        if (childMap.containsKey(parentUuid)) {
            return childMap.get(parentUuid)
                    .stream()
                    .map(ExplorerNode::getDocRef)
                    .toList();
        } else {
            return null;
        }
    }


    @Override
    String getNodeKey(final ExplorerNode node) {
        return NullSafe.get(node, ExplorerNode::getUuid);
    }

    @Override
    public TreeModel clone() {
        final AbstractTreeModel<String> clone = super.clone();
        return (TreeModel) clone;
    }
}
