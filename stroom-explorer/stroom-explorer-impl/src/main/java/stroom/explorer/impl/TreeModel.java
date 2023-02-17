package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TreeModel extends AbstractTreeModel<String> implements Cloneable {

    public TreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    @Override
    public void add(final ExplorerNode parent, final ExplorerNode child) {
        final String childUuid = child != null ? child.getUuid() : null;
        final String parentUuid = parent != null ? parent.getUuid() : null;

        parentMap.putIfAbsent(childUuid, parent);
        childMap.computeIfAbsent(parentUuid, k -> new LinkedHashSet<>()).add(child);
    }

    public ExplorerNode getParent(final ExplorerNode child) {
        if (child == null) {
            return null;
        }
        return parentMap.get(child.getUuid());
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        final Set<ExplorerNode> children = childMap.get(parent != null ? parent.getUuid() : null);
        return children != null ? children.stream().toList() : null;
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
    public TreeModel clone() {
        try {
            final TreeModel treeModel = (TreeModel) super.clone();
            treeModel.parentMap = new HashMap<>(parentMap);
            treeModel.childMap = new HashMap<>();
            childMap.forEach((key, value) -> treeModel.childMap.put(key, new LinkedHashSet<>(value)));
            return treeModel;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
