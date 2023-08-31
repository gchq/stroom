package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.NullSafe;

public class FilteredTreeModel extends AbstractTreeModel<ExplorerNodeKey> {

    public FilteredTreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

//    @Override
//    public void add(final ExplorerNode parent, final ExplorerNode child) {
//        final ExplorerNodeKey childKey = child != null
//                ? child.getUniqueKey()
//                : null;
//        final ExplorerNodeKey parentKey = parent != null
//                ? parent.getUniqueKey()
//                : null;
//
//        parentMap.put(childKey, parent);
//        childMap.computeIfAbsent(parentKey, k -> new LinkedHashSet<>()).add(child);
////        if (parent == null) {
////            roots.add(childKey);
////        }
//    }

//    public ExplorerNode getParent(final ExplorerNode child) {
//        if (child == null) {
//            return null;
//        }
//        return parentMap.get(child.getUniqueKey());
//    }

//    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
//        final Set<ExplorerNode> children = childMap.get(NullSafe.get(
//                parent,
//                ExplorerNode::getUniqueKey));
//        return NullSafe.get(
//                children,
//                children2 -> children2.stream().toList());
//    }

    @Override
    ExplorerNodeKey getNodeKey(final ExplorerNode node) {
        return NullSafe.get(node, ExplorerNode::getUniqueKey);
    }
}
