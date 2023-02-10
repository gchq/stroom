package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TreeModel extends AbstractTreeModel<String> implements Cloneable {

    public TreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    @Override
    public void add(final ExplorerNode parent, final ExplorerNode child) {
        parentMap.put(child != null
                ? child.getUuid()
                : null, parent);
        childMap.computeIfAbsent(parent != null
                ? parent.getUuid()
                : null, k -> new ArrayList<>()).add(child);
    }

    public ExplorerNode getParent(final ExplorerNode child) {
        if (child == null) {
            return null;
        }
        return parentMap.get(child.getUuid());
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        if (parent == null) {
            return childMap.get(null);
        }
        return childMap.get(parent.getUuid());
    }

    public List<DocRef> getChildren(final DocRef parent) {
        if (parent == null) {
            return childMap.get(null)
                    .stream()
                    .map(ExplorerNode::getDocRef)
                    .toList();
        }
        return childMap.get(parent.getUuid())
                .stream()
                .map(ExplorerNode::getDocRef)
                .toList();
    }

    @Override
    public TreeModel clone() {
        try {
            final TreeModel treeModel = (TreeModel) super.clone();
            treeModel.parentMap = new HashMap<>(parentMap);
            treeModel.childMap = new HashMap<>();
            childMap.forEach((key, value) -> treeModel.childMap.put(key, new ArrayList<>(value)));
            return treeModel;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
