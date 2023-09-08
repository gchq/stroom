package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

public class UnmodifiableTreeModel {

    private final TreeModel treeModel;

    private UnmodifiableTreeModel(final TreeModel treeModel) {
        this.treeModel = treeModel;
    }

    public static UnmodifiableTreeModel wrap(final TreeModel treeModel) {
        return new UnmodifiableTreeModel(treeModel);
    }

    public long getId() {
        return treeModel.getId();
    }

    public long getCreationTime() {
        return treeModel.getCreationTime();
    }

    public Set<String> getAllParents() {
        return treeModel.getAllParents();
    }

    public ExplorerNode getParent(final String childKey) {
        return treeModel.getParent(childKey);
    }

    public ExplorerNode getNode(final String nodeKey) {
        return treeModel.getNode(nodeKey);
    }

    public ExplorerNode getNode(final ExplorerNode node) {
        return treeModel.getNode(node);
    }

    public void addRoot(final ExplorerNode rootNode) {
        treeModel.addRoot(rootNode);
    }

    public void add(final ExplorerNode parent, final ExplorerNode child) {
        throw new UnsupportedOperationException("Mutations not supported");
    }

    public void replaceNode(final ExplorerNode newNode) {
        throw new UnsupportedOperationException("Mutations not supported");
    }

    public boolean containsNode(final ExplorerNode node) {
        return treeModel.containsNode(node);
    }

    public void replaceNode(final String key, final ExplorerNode newNode) {
        throw new UnsupportedOperationException("Mutations not supported");
    }

    public ExplorerNode getParent(final ExplorerNode child) {
        return treeModel.getParent(child);
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        return treeModel.getChildren(parent);
    }

    public boolean hasChildren(final String key) {
        return treeModel.hasChildren(key);
    }

    public void sort(final ToIntFunction<ExplorerNode> priorityExtractor) {
        throw new UnsupportedOperationException("Mutations not supported");
    }

    public void decorate(final BiFunction<String, ExplorerNode, ExplorerNode> nodeDecorator) {
        throw new UnsupportedOperationException("Mutations not supported");
    }

    public List<DocRef> getChildren(final DocRef parent) {
        return treeModel.getChildren(parent);
    }

    public String getNodeKey(final ExplorerNode node) {
        return treeModel.getNodeKey(node);
    }

    public TreeModel createMutableCopy() {
        return treeModel.clone();
    }
}
