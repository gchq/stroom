package stroom.explorer.impl;

import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeInfo;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Collections;
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
    protected Map<K, ExplorerNode> childKeyToParentNodeMap = new HashMap<>();
    // Parent key => Set<child>
    protected Map<K, Set<ExplorerNode>> parentKeyToChildNodesMap = new HashMap<>();
    // Key => List<NodeInfo>
    protected Map<K, List<NodeInfo>> keyToNodeInfoMap = new HashMap<>();
    // Parent key => Set<child> (sub-set of children that have node info or have descendants that do)
    protected Map<K, Set<ExplorerNode>> parentKeyToChildNodesWithInfoMap = new HashMap<>();
    // Node key => node
    protected Map<K, ExplorerNode> keyToNodeMap = new HashMap<>();
    // Set of all tags seen in all nodes
    // TODO: 26/09/2023 At some point we may consider limiting the visibility of tags based on having
    //  Read permission on the node with the tag, in which case we won't be able to cache the set of
    //  tags like this.
    protected Set<String> allTags = new HashSet<>();

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
        return new LinkedHashSet<>(parentKeyToChildNodesMap.keySet());
    }

    public ExplorerNode getParent(final K childKey) {
        return NullSafe.get(childKey, childKeyToParentNodeMap::get);
    }

    /**
     * Get the node from the model with the supplied unique key
     */
    public ExplorerNode getNode(final K nodeKey) {
        return NullSafe.get(nodeKey, keyToNodeMap::get);
    }

    /**
     * @return A set of all tags that exist across all nodes
     */
    public Set<String> getAllTags() {
        return Collections.unmodifiableSet(allTags);
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
        recordNodeTags(parent, child);

        childKeyToParentNodeMap.putIfAbsent(childKey, parent);
        final Set<ExplorerNode> childNodes = parentKeyToChildNodesMap.computeIfAbsent(
                parentKey,
                k -> new LinkedHashSet<>());
        childNodes.add(child);

        keyToNodeMap.putIfAbsent(childKey, child);
        keyToNodeMap.putIfAbsent(parentKey, parent);
    }

    /**
     * Should only be called after all calls to {@link AbstractTreeModel#add(ExplorerNode, ExplorerNode)}
     * have been made so all nodes are available
     */
    public void addNodeInfo(final ExplorerNode node, final List<NodeInfo> nodeInfoList) {
        Objects.requireNonNull(node);
        if (NullSafe.hasItems(nodeInfoList)) {
            final K nodeKey = getNodeKey(node);
            keyToNodeInfoMap.put(nodeKey, nodeInfoList);
            recordNodeInfoPresence(nodeKey, node);
        }
    }

    public List<NodeInfo> getNodeInfo(final ExplorerNode node) {
        return NullSafe.list(NullSafe.get(node, this::getNodeKey, keyToNodeInfoMap::get));
    }

    private void recordNodeTags(final ExplorerNode... explorerNodes) {
        if (explorerNodes != null) {
            for (final ExplorerNode explorerNode : explorerNodes) {
                if (explorerNode != null) {
                    final Set<String> tags = explorerNode.getTags();
                    if (NullSafe.hasItems(tags)) {
                        allTags.addAll(tags);
                    }
                }
            }
        }
    }

    /**
     * Record a child node having node info (or one of its descendants having node info.
     */
    private void recordNodeInfoPresence(final K nodeKey,
                                        final ExplorerNode node) {

        final ExplorerNode parentNode = getParent(nodeKey);
        final K parentKey = getNodeKey(parentNode);
        final boolean wasAdded = parentKeyToChildNodesWithInfoMap.computeIfAbsent(parentKey, k -> new HashSet<>())
                .add(node);

        // Another descendant of parentNode may have already marked it as having child node info,
        // in which case nothing to do
        if (wasAdded && parentKey != null) {
            // Recurse up the chain of ancestors till we hit one with no parent
            recordNodeInfoPresence(parentKey, parentNode);
        }
    }

//    /**
//     * Replaces an existing node with the supplied one, based on the unique key
//     * of the new node. The new node will be linked to the previous node's parent
//     * and children. If the key is not found an exception will be thrown.
//     * Intended for changing other properties of a node, e.g. the alerts on it.
//     *
//     * MUST be done while under an exclusive lock on this model.
//     */
//    public void replaceNode(final ExplorerNode newNode) {
//        final K nodeKey = getNodeKey(newNode);
//        replaceNode(nodeKey, newNode);
//    }

    public boolean containsNode(final ExplorerNode node) {
        return keyToNodeMap.containsKey(getNodeKey(node));
    }

//    /**
//     * Replaces an existing node with the supplied one. The new node will
//     * be linked to the previous node's parent and children. If the key is
//     * not found an exception will be thrown. Intended for changing other properties
//     * of a node, e.g. the alerts on it.
//     *
//     * MUST be done while under an exclusive lock on this model.
//     */
//    public void replaceNode(final K key, final ExplorerNode newNode) {
//        if (!keyToNodeMap.containsKey(key)) {
//            throw new RuntimeException(LogUtil.message(
//            "Key {} not found in tree model for newNode: {}", key, newNode));
//        }
//        final ExplorerNode oldNode = keyToNodeMap.get(key);
//        final ExplorerNode parent = childKeyToParentNodeMap.get(key);
//        final K parentKey = getNodeKey(parent);
//
//        // Get the children or newNode's parent, i.e. siblings
//        final Set<ExplorerNode> siblingNodes = parentKeyToChildNodesMap.get(parentKey);
//
//        if (siblingNodes != null) {
//            final Set<ExplorerNode> newSiblingNodes = siblingNodes.stream()
//                    .map(node -> {
//                        final K nodeKey = getNodeKey(node);
//                        if (Objects.equals(nodeKey, key)) {
//                            return newNode;
//                        } else {
//                            return node;
//                        }
//                    })
//                    .collect(Collectors.toSet());
//            parentKeyToChildNodesMap.put(parentKey, newSiblingNodes);
//        }
//
//        keyToNodeMap.put(key, newNode);
//        if (!Objects.equals(oldNode.getTags(), newNode.getTags())) {
//            final Set<String> oldTags = NullSafe.set(oldNode.getTags());
//            final Set<String> newTags = NullSafe.set(newNode.getTags());
//
//            final Set<String> removedTags = new HashSet<>(oldTags);
//            removedTags.removeAll(newTags);
//            allTags.removeAll(removedTags);
//
//            allTags.addAll(newTags);
//        }
//
//        // TODO: 26/09/2023 Need to do something about parentKeyToChildNodesWithInfoMap
//    }

    public ExplorerNode getParent(final ExplorerNode child) {
        return NullSafe.get(child, child2 -> childKeyToParentNodeMap.get(getNodeKey(child2)));
    }

    public List<ExplorerNode> getChildren(final ExplorerNode parent) {
        final K parentKey = getNodeKey(parent);
        final Set<ExplorerNode> children = parentKeyToChildNodesMap.get(parentKey);
        return NullSafe.get(
                children,
                children2 -> children2.stream().toList());
    }

    public boolean hasChild(final ExplorerNode parent, final ExplorerNode child) {
        if (parent != null && child != null) {
            final K parentKey = getNodeKey(parent);
            final Set<ExplorerNode> children = parentKeyToChildNodesMap.get(parentKey);

            // Can't use contains due to hash/equals including rootNodeUuid which may be
            // present on one but not the other.
            return NullSafe.set(children).stream()
                    .anyMatch(node -> Objects.equals(node.getDocRef(), child.getDocRef()));
        } else {
            return false;
        }
    }

    /**
     * @param key
     * @return True if the node for this key has at least one child
     */
    public boolean hasChildren(final K key) {
        return NullSafe.hasItems(parentKeyToChildNodesMap.get(key));
    }

    public boolean hasChildren(final ExplorerNode parent) {
        final K nodeKey = getNodeKey(parent);
        return NullSafe.hasItems(parentKeyToChildNodesMap.get(nodeKey));
    }

    /**
     * @return True if any one of childNode has {@link NodeInfo} or any of its descendants do.
     */
    public boolean hasDescendantNodeInfo(final ExplorerNode parentNode, final Collection<ExplorerNode> childNodes) {
        final K parentKey = getNodeKey(parentNode);
        if (parentKey != null && NullSafe.hasItems(childNodes)) {
            final Set<ExplorerNode> childNodesWithInfo = NullSafe.set(parentKeyToChildNodesWithInfoMap.get(parentKey));
            return childNodes.stream()
                    .anyMatch(node -> {
                        // Can't use set.contains in case we are comparing nodes with a rootNodeUuid to those
                        // without
                        return childNodesWithInfo.stream()
                                .anyMatch(childNode -> Objects.equals(childNode.getDocRef(), node.getDocRef()));
                    });
        } else {
            return false;
        }
    }

    /**
     * @return The sub-set of childNodes that have node info or one of their descendants has it.
     */
    public Set<ExplorerNode> getChildrenWithDescendantInfo(final ExplorerNode parentNode,
                                                           final Collection<ExplorerNode> childNodes) {
        final K parentKey = getNodeKey(parentNode);
        if (parentKey != null && NullSafe.hasItems(childNodes)) {
            final Set<ExplorerNode> childNodesWithInfo = NullSafe.set(parentKeyToChildNodesWithInfoMap.get(parentKey));
            return childNodes.stream()
                    .filter(node -> {
                        // TODO: 14/09/2023 Change to use contains when we refactor to have two different node types
                        // Can't use set.contains in case we are comparing nodes with a rootNodeUuid to those
                        // without
                        return childNodesWithInfo.stream()
                                .anyMatch(childNode -> Objects.equals(childNode.getDocRef(), node.getDocRef()));
                    })
                    .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    public Set<ExplorerNode> getChildrenWithDescendantInfo(final ExplorerNode parent) {
        final K parentKey = getNodeKey(parent);
        return parentKeyToChildNodesMap.get(parentKey);
    }

    private boolean isFolderOrGitRepo(final String type) {
        return DocumentTypeRegistry.FOLDER_DOCUMENT_TYPE.getType().equals(type) ||
               DocumentTypeRegistry.GIT_REPO_DOCUMENT_TYPE.getType().equals(type);
    }

    public void sort(final ToIntFunction<ExplorerNode> priorityExtractor) {
        final Map<K, Set<ExplorerNode>> newChildMap = new HashMap<>();
        parentKeyToChildNodesMap.forEach((key, children) -> newChildMap.put(key, children
                .stream()
                .sorted((o1, o2) -> {
                    // If the types are the same then just compare by name.
                    if (o1.getType().equals(o2.getType())) {
                        return o1.getName().compareTo(o2.getName());
                    }

                    // If both types are folders or git repos then just compare by name.
                    if (isFolderOrGitRepo(o1.getType())) {
                        if (isFolderOrGitRepo(o2.getType())) {
                            return o1.getName().compareTo(o2.getName());
                        } else {
                            return 1;
                        }
                    } else if (isFolderOrGitRepo(o2.getType())) {
                        return -1;
                    }

                    // Compare by type priority.
                    final int p1 = priorityExtractor.applyAsInt(o1);
                    final int p2 = priorityExtractor.applyAsInt(o2);
                    if (p1 != p2) {
                        return Integer.compare(p1, p2);
                    }

                    // If type priority is the same then compare by name.
                    return o1.getName().compareTo(o2.getName());
                })
                .collect(Collectors.toCollection(LinkedHashSet::new))));

        parentKeyToChildNodesMap = newChildMap;
    }

    /**
     * @param nodeDecorator Returns a decorated copy of the node passed to it. It MUST have the
     *                      same node key as the node passed in. Will be called for each node in
     *                      a depth first way, with siblings processed in no specific order.
     */
    public void decorate(final BiFunction<K, ExplorerNode, ExplorerNode> nodeDecorator) {
        final K nullKey = null;
        final Set<ExplorerNode> childNodes = parentKeyToChildNodesMap.get(nullKey);

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
                    keyToNodeMap.put(nullKey, newChildNode);
                }
            }

            if (hasChildNodeChanged) {
                parentKeyToChildNodesMap.put(nullKey, newChildNodes);
            }
        }
    }

    private ExplorerNode decorate(final K nodeKey,
                                  final ExplorerNode node,
                                  final BiFunction<K, ExplorerNode, ExplorerNode> nodeDecorator) {
        Objects.requireNonNull(node);

        final Set<ExplorerNode> childNodes = parentKeyToChildNodesMap.get(nodeKey);
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
                        keyToNodeMap.put(childNodeKey, newChildNode);
                    }
                    newChildNodes.add(newChildNode);
                } else {
                    // Not sure we want this else block
                    newChildNodes.add(null);
                    keyToNodeMap.put(null, null);
                }
            }
            if (hasAnyNodeChanged) {
                parentKeyToChildNodesMap.put(nodeKey, newChildNodes);
            }
        }

        // Decorate this node
        final ExplorerNode newNode = nodeDecorator.apply(nodeKey, node);
        if (newNode != node) {
            hasAnyNodeChanged = true;
            keyToNodeMap.put(nodeKey, newNode);
        }

        if (hasAnyNodeChanged) {
            // Now link the new children (if any) to the new node (their parent)
            for (final ExplorerNode newChildNode : newChildNodes) {
                final K newChildNodeKey = getNodeKey(newChildNode);
                childKeyToParentNodeMap.put(newChildNodeKey, newNode);
            }
        }

        return newNode;
    }

    @Override
    public AbstractTreeModel<K> clone() {
        try {
            final AbstractTreeModel<K> treeModel = (AbstractTreeModel<K>) super.clone();
            treeModel.keyToNodeMap = new HashMap<>(this.keyToNodeMap);
            treeModel.childKeyToParentNodeMap = new HashMap<>(this.childKeyToParentNodeMap);
            treeModel.parentKeyToChildNodesMap = new HashMap<>(this.parentKeyToChildNodesMap.size());
            this.parentKeyToChildNodesMap.forEach((key, childNodes) ->
                    treeModel.parentKeyToChildNodesMap.put(key, new LinkedHashSet<>(childNodes)));
            return treeModel;
        } catch (final CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return The unique key for node, or null if node is null
     */
    abstract K getNodeKey(final ExplorerNode node);
}
