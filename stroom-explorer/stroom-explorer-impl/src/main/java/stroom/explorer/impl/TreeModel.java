package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.NullSafe;

import java.util.List;

/**
 * {@link AbstractTreeModel} keyed by doc UUID.
 */
public class TreeModel extends AbstractTreeModel<String> implements Cloneable {

    public TreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    public List<DocRef> getChildren(final DocRef parent) {
        final String parentUuid = NullSafe.get(parent, DocRef::getUuid);
        if (parentKeyToChildNodesMap.containsKey(parentUuid)) {
            return parentKeyToChildNodesMap.get(parentUuid)
                    .stream()
                    .map(ExplorerNode::getDocRef)
                    .toList();
        } else {
            return null;
        }
    }

    public ExplorerNode getNode(final DocRef docRef) {
        final String uuid = NullSafe.get(docRef, DocRef::getUuid);
        return keyToNodeMap.get(uuid);
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
