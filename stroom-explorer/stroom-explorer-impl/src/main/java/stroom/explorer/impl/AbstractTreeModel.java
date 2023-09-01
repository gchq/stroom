package stroom.explorer.impl;

import stroom.explorer.shared.ExplorerNode;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public abstract class AbstractTreeModel<K> {

    protected final long id;
    protected final long creationTime;
    // Child key => parent
    protected Map<K, ExplorerNode> parentMap = new HashMap<>();
    // Parent key => Set<child>
    protected Map<K, Set<ExplorerNode>> childMap = new HashMap<>();
    // Node key => node
    protected Map<K, ExplorerNode> nodeMap = new HashMap<>();

    public AbstractTreeModel(final long id, final long creationTime) {
        this.id = id;
        this.creationTime = creationTime;
    }

    public long getId() {
        return id;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public Set<K> getAllParents() {
        return new LinkedHashSet<>(childMap.keySet());
    }

    public ExplorerNode getParent(final K childKey) {
        return NullSafe.get(childKey, parentMap::get);
    }

    /**
     * Get the node from the model with the supplied unique key
     */
    public ExplorerNode getNode(final K nodeKey) {
        return NullSafe.get(nodeKey, nodeMap::get);
    }

    /**
     * Get the node from the model with the unique key of the supplied node
     */
    public ExplorerNode getNode(final ExplorerNode node) {
        return getNode(getNodeKey(node));
    }

    public void addRoot(final ExplorerNode rootNode) {
        add(null, rootNode);
    }

    public void add(final ExplorerNode parent, final ExplorerNode child) {
        final K childKey = getNodeKey(child);
        final K parentKey = getNodeKey(parent);

        parentMap.putIfAbsent(childKey, parent);
        childMap.computeIfAbsent(parentKey, k -> new LinkedHashSet<>()).add(child);
        nodeMap.putIfAbsent(childKey, child);
        nodeMap.putIfAbsent(parentKey, parent);
//        if (parent == null) {
//            roots.add(childUuid);
//        }
    }

    /**
     * Replaces an existing node with the supplied one, based on the unique key
     * of the new node. The new node will be linked to the previous node's parent
     * and children. If the key is not found an exception will be thrown.
     * Intended for changing other properties of a node, e.g. the alerts on it.
     */
    public void replaceNode(final ExplorerNode newNode) {
        final K nodeKey = getNodeKey(newNode);
        replaceNode(nodeKey, newNode);
    }

    public boolean containsNode(final ExplorerNode node) {
        return nodeMap.containsKey(getNodeKey(node));
    }

    /**
     * Replaces an existing node with the supplied one. The new node will
     * be linked to the previous node's parent and children. If the key is
     * not found an exception will be thrown. Intended for changing other properties
     * of a node, e.g. the alerts on it.
     */
    public void replaceNode(final K key, final ExplorerNode newNode) {
        if (!nodeMap.containsKey(key)) {
            throw new RuntimeException(LogUtil.message("Key {} not found in tree model for newNode: {}", key, newNode));
        }
        final ExplorerNode parent = parentMap.get(key);
        final K parentKey = getNodeKey(parent);

        // Get the children or newNode's parent, i.e. siblings
        final Set<ExplorerNode> siblingNodes = childMap.get(parentKey);

        if (siblingNodes != null) {
            final Set<ExplorerNode> newSiblingNodes = siblingNodes.stream()
                    .map(node -> {
                        final K nodeKey = getNodeKey(node);
                        if (Objects.equals(nodeKey, key)) {
                            return newNode;
                        } else {
                            return node;
                        }
                    })
                    .collect(Collectors.toSet());
            childMap.put(parentKey, newSiblingNodes);
        }

        nodeMap.put(key, newNode);
    }

    public ExplorerNode getParent(final ExplorerNode child) {
        return NullSafe.get(child, child2 -> parentMap.get(getNodeKey(child2)));
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        final K parentKey = getNodeKey(parent);
        final Set<ExplorerNode> children = childMap.get(parentKey);
        return NullSafe.get(
                children,
                children2 -> children2.stream().toList());
    }

    /**
     * @param key
     * @return True if the node for this key has at least one child
     */
    public boolean hasChildren(final K key) {
        return NullSafe.hasItems(childMap.get(key));
    }

    public void sort(final ToIntFunction<ExplorerNode> priorityExtractor) {
        final Map<K, Set<ExplorerNode>> newChildMap = new HashMap<>();
        childMap.forEach((key, children) -> newChildMap.put(key, children
                .stream()
                .sorted(Comparator
                        .comparingInt(priorityExtractor)
                        .thenComparing(ExplorerNode::getType)
                        .thenComparing(ExplorerNode::getName))
                .collect(Collectors.toCollection(LinkedHashSet::new))));

        childMap = newChildMap;
    }

    /**
     * @param nodeDecorator Returns a decorated copy of the node passed to it. It MUST have the
     *                      same node key as the node passed in. Will be called for each node in
     *                      a depth first way, with siblings processed in no specific order.
     */
    public void decorate(final BiFunction<K, ExplorerNode, ExplorerNode> nodeDecorator) {
        final K nullKey = null;
        final Set<ExplorerNode> childNodes = childMap.get(nullKey);

        // All the root nodes
        if (NullSafe.hasItems(childNodes)) {
            final Set<ExplorerNode> newChildNodes = new HashSet<>(childNodes.size());
            boolean hasChildNodeChanged = false;
            for (final ExplorerNode childNode : NullSafe.set(childNodes)) {
                final K childNodeKey = getNodeKey(childNode);
                final ExplorerNode newChildNode = decorate(childNodeKey, childNode, nodeDecorator);
                newChildNodes.add(newChildNode);
                if (newChildNode != childNode) {
                    hasChildNodeChanged = true;
                    nodeMap.put(nullKey, newChildNode);
                }
            }

            if (hasChildNodeChanged) {
                childMap.put(nullKey, newChildNodes);
            }
        }
    }

    private ExplorerNode decorate(final K nodeKey,
                                  final ExplorerNode node,
                                  final BiFunction<K, ExplorerNode, ExplorerNode> nodeDecorator) {
        Objects.requireNonNull(node);

        final Set<ExplorerNode> childNodes = childMap.get(nodeKey);
        boolean hasAnyNodeChanged = false;
        final Set<ExplorerNode> newChildNodes = NullSafe.getOrElseGet(
                childNodes,
                childNodes2 -> new LinkedHashSet<>(childNodes2.size()),
                Collections::emptySet);

        if (NullSafe.hasItems(childNodes)) {
            // Branch, so recurse into each to decorate it
            for (final ExplorerNode childNode : childNodes) {
                if (childNode != null) {
                    final K childNodeKey = getNodeKey(childNode);
                    final ExplorerNode newChildNode = decorate(childNodeKey, childNode, nodeDecorator);
                    if (newChildNode != childNode) {
                        hasAnyNodeChanged = true;
                        nodeMap.put(childNodeKey, newChildNode);
                    }
                    newChildNodes.add(newChildNode);
                } else {
                    // Not sure we want this else block
                    newChildNodes.add(null);
                    nodeMap.put(null, null);
                }
            }
            if (hasAnyNodeChanged) {
                childMap.put(nodeKey, newChildNodes);
            }
        }

        // Decorate this node
        final ExplorerNode newNode = nodeDecorator.apply(nodeKey, node);
        if (newNode != node) {
            hasAnyNodeChanged = true;
            nodeMap.put(nodeKey, newNode);
        }

        if (hasAnyNodeChanged) {
            // Now link the new children (if any) to the new node (their parent)
            for (final ExplorerNode newChildNode : newChildNodes) {
                final K newChildNodeKey = getNodeKey(newChildNode);
                parentMap.put(newChildNodeKey, newNode);
            }
        }

        return newNode;
    }

    @Override
    public AbstractTreeModel<K> clone() {
        try {
            final AbstractTreeModel<K> treeModel = (AbstractTreeModel<K>) super.clone();
            treeModel.nodeMap = new HashMap<>(this.nodeMap);
            treeModel.parentMap = new HashMap<>(this.parentMap);
            treeModel.childMap = new HashMap<>(this.childMap.size());
            this.childMap.forEach((key, childNodes) ->
                    treeModel.childMap.put(key, new LinkedHashSet<>(childNodes)));
            return treeModel;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return The unique key for node, or null if node is null
     */
    abstract K getNodeKey(final ExplorerNode node);
}
