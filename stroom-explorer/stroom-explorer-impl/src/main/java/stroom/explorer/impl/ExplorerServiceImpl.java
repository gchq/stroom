/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.explorer.impl;

import stroom.collection.api.CollectionService;
import stroom.docref.DocRef;
import stroom.docstore.fav.api.DocFavService;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeState;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.explorer.shared.PermissionInheritance;
import stroom.explorer.shared.StandardTagNames;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class ExplorerServiceImpl implements ExplorerService, CollectionService, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerServiceImpl.class);

    private static final FilterFieldMappers<DocRef> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(ExplorerTreeFilter.FIELD_DEF_NAME, DocRef::getName),
            FilterFieldMapper.of(ExplorerTreeFilter.FIELD_DEF_TYPE, DocRef::getType),
            FilterFieldMapper.of(ExplorerTreeFilter.FIELD_DEF_UUID, DocRef::getUuid));

    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;
    private final Provider<ExplorerDecorator> explorerDecoratorProvider;
    private final Provider<DocFavService> docFavService;

    @Inject
    ExplorerServiceImpl(final ExplorerNodeService explorerNodeService,
                        final ExplorerTreeModel explorerTreeModel,
                        final ExplorerActionHandlers explorerActionHandlers,
                        final SecurityContext securityContext,
                        final ExplorerEventLog explorerEventLog,
                        final Provider<ExplorerDecorator> explorerDecoratorProvider,
                        final Provider<DocFavService> docFavService) {
        this.explorerNodeService = explorerNodeService;
        this.explorerTreeModel = explorerTreeModel;
        this.explorerActionHandlers = explorerActionHandlers;
        this.securityContext = securityContext;
        this.explorerEventLog = explorerEventLog;
        this.explorerDecoratorProvider = explorerDecoratorProvider;
        this.docFavService = docFavService;

        explorerNodeService.ensureRootNodeExists();
    }

    @Override
    public FetchExplorerNodeResult getData(final FindExplorerNodeCriteria criteria) {
        try {
            List<ExplorerNode> rootNodes;
            List<ExplorerNodeKey> openedItems = new ArrayList<>();
            Set<ExplorerNodeKey> temporaryOpenItems;

            final ExplorerTreeFilter filter = criteria.getFilter();
            final String qualifiedFilterInput = QuickFilterPredicateFactory.fullyQualifyInput(
                    filter.getNameFilter(), FIELD_MAPPERS);

            // Get the master tree model.
            final TreeModel masterTreeModel = explorerTreeModel.getModel().clone();

            buildFavouritesNode(masterTreeModel);

            // See if we need to open any more folders to see nodes we want to ensure are visible.
            final Set<ExplorerNodeKey> forcedOpenItems = getForcedOpenItems(masterTreeModel, criteria);

            final Set<ExplorerNodeKey> allOpenItems = new HashSet<>();
            allOpenItems.addAll(criteria.getOpenItems());
            allOpenItems.addAll(criteria.getTemporaryOpenedItems());
            allOpenItems.addAll(forcedOpenItems);

            final FilteredTreeModel filteredModel = new FilteredTreeModel(
                    masterTreeModel.getId(),
                    masterTreeModel.getCreationTime());
            // Create the predicate for the current filter value
            final Predicate<DocRef> fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                    filter.getNameFilter(), FIELD_MAPPERS);

            addDescendants(
                    null,
                    null,
                    masterTreeModel,
                    filteredModel,
                    filter,
                    fuzzyMatchPredicate,
                    false,
                    allOpenItems,
                    0);

            // If the name filter has changed then we want to temporarily expand all nodes.
            if (filter.isNameFilterChange()) {
                if (filter.getNameFilter() == null) {
                    temporaryOpenItems = new HashSet<>();
                } else {
                    temporaryOpenItems = new HashSet<>(filteredModel.getAllParents());
                }

                rootNodes = addRoots(
                        filteredModel,
                        criteria.getOpenItems(),
                        forcedOpenItems,
                        temporaryOpenItems,
                        openedItems);
            } else {
                temporaryOpenItems = null;

                rootNodes = addRoots(
                        filteredModel,
                        criteria.getOpenItems(),
                        forcedOpenItems,
                        criteria.getTemporaryOpenedItems(),
                        openedItems);
            }

            rootNodes = decorateTree(
                    criteria,
                    rootNodes,
                    fuzzyMatchPredicate);

            // Ensure root nodes are open if they have items
            for (final ExplorerNode rootNode : rootNodes) {
                if (rootNode.getChildren() != null
                        && !rootNode.getChildren().isEmpty()
                        && NodeState.CLOSED.equals(rootNode.getNodeState())) {

                    rootNodes = rootNodes
                            .stream()
                            .map(node -> {
                                if (node == rootNode) {
                                    return node.copy().nodeState(NodeState.OPEN).build();
                                }
                                return node;
                            })
                            .collect(Collectors.toList());
                }
            }

            return new FetchExplorerNodeResult(rootNodes, openedItems, temporaryOpenItems, qualifiedFilterInput);
        } catch (Exception e) {
            LOGGER.error("Error fetching nodes with criteria {}", criteria, e);
            throw e;
        }
    }

    private void buildFavouritesNode(final TreeModel masterTreeModel) {
        final ExplorerNode.Builder favNodeBuilder = ExplorerNode.builder()
                .type(ExplorerConstants.FAVOURITES_DOC_REF.getType())
                .uuid(ExplorerConstants.FAVOURITES_DOC_REF.getUuid())
                .name(ExplorerConstants.FAVOURITES_DOC_REF.getName())
                .depth(0)
                .iconClassName(DocumentType.DOC_IMAGE_CLASS_NAME + ExplorerConstants.FAVOURITES);
        final ExplorerNode favNode = favNodeBuilder.build();

        for (final DocRef favDocRef : docFavService.get().fetchDocFavs()) {
            final ExplorerNode childNode = favNode.copy()
                    .type(favDocRef.getType())
                    .uuid(favDocRef.getUuid())
                    .name(favDocRef.getName())
                    .depth(1)
                    .iconClassName(DocumentType.DOC_IMAGE_CLASS_NAME + favDocRef.getType())
                    .isFavourite(true)
                    .rootNodeUuid(favNode)
                    .build();
            masterTreeModel.add(favNode, childNode);
        }

        masterTreeModel.add(null, favNode);
    }

    private List<ExplorerNode> decorateTree(final FindExplorerNodeCriteria criteria,
                                            final List<ExplorerNode> rootNodes,
                                            final Predicate<DocRef> fuzzyMatchPredicate) {
        if (rootNodes.size() > 0) {
            final ExplorerNode rootNode = rootNodes.get(0);
            return rootNodes
                    .stream()
                    .map(node -> {
                        if (node == rootNode) {
                            return replaceRootNode(criteria, node, fuzzyMatchPredicate);
                        }
                        return node;
                    })
                    .collect(Collectors.toList());
        } else {
            return Collections.singletonList(replaceRootNode(criteria, null, fuzzyMatchPredicate));
        }
    }

    private ExplorerNode replaceRootNode(final FindExplorerNodeCriteria criteria,
                                         final ExplorerNode rootNode,
                                         final Predicate<DocRef> fuzzyMatchPredicate) {
        final ExplorerNode.Builder builder;
        if (rootNode != null) {
            builder = rootNode.copy();
        } else {
            builder = explorerNodeService.getRoot()
                    .map(node -> {
                        final ExplorerNode.Builder root = node.copy();
                        Optional.ofNullable(explorerActionHandlers.getType(ExplorerConstants.SYSTEM))
                                .map(DocumentType::getIconClassName)
                                .ifPresent(root::iconClassName);
                        return root;
                    })
                    .orElseGet(ExplorerNode::builder);
        }

        if (criteria.getFilter() != null &&
                criteria.getFilter().getTags() != null &&
                criteria.getFilter().getTags().contains(StandardTagNames.DATA_SOURCE)) {

            final ExplorerDecorator explorerDecorator = explorerDecoratorProvider.get();
            if (explorerDecorator != null) {
                final List<DocRef> additionalDocRefs = explorerDecorator.list()
                        .stream()
                        .filter(fuzzyMatchPredicate)
                        .collect(Collectors.toList());

                if (!additionalDocRefs.isEmpty()) {
                    if (rootNode == null) {
                        throw new RuntimeException("Missing root node");
                    }

                    additionalDocRefs.forEach(docRef -> {
                        final ExplorerNode node = ExplorerNode
                                .builder()
                                .type(docRef.getType())
                                .uuid(docRef.getUuid())
                                .name(docRef.getName())
                                .tags(StandardTagNames.DATA_SOURCE)
                                .nodeState(NodeState.LEAF)
                                .depth(1)
                                .iconClassName(DocumentType.DOC_IMAGE_CLASS_NAME + "searchable")
                                .build();
                        builder.addChild(node);
                    });
                }
            }
        }
        return builder.build();
    }

    @Override
    public Set<DocRef> getChildren(final DocRef folder, final String type) {
        return getDescendants(folder, type, 0);
    }

    @Override
    public Set<DocRef> getDescendants(final DocRef folder, final String type) {
        return getDescendants(folder, type, Integer.MAX_VALUE);
    }

    private Set<DocRef> getDescendants(final DocRef folder, final String type, final int maxDepth) {
        final TreeModel masterTreeModel = explorerTreeModel.getModel();
        if (masterTreeModel != null) {
            final Set<DocRef> refs = new HashSet<>();
            addChildren(ExplorerNode.create(folder), type, 0, maxDepth, masterTreeModel, refs);
            return refs;
        }

        return Collections.emptySet();
    }

    private void addChildren(final ExplorerNode parent,
                             final String type,
                             final int depth,
                             final int maxDepth,
                             final TreeModel treeModel,
                             final Set<DocRef> refs) {
        final List<ExplorerNode> childNodes = treeModel.getChildren(parent);
        if (childNodes != null) {
            childNodes.forEach(node -> {
                if (node.getType().equals(type)) {
                    if (securityContext.hasDocumentPermission(node.getUuid(), DocumentPermissionNames.USE)) {
                        refs.add(node.getDocRef());
                    }
                }

                if (depth < maxDepth) {
                    addChildren(node, type, depth + 1, maxDepth, treeModel, refs);
                }
            });
        }
    }

    private Set<ExplorerNodeKey> getForcedOpenItems(final TreeModel masterTreeModel,
                                                    final FindExplorerNodeCriteria criteria) {
        final Set<ExplorerNodeKey> forcedOpen = new HashSet<>();

        // Add parents of  nodes that we have been requested to ensure are visible.
        if (criteria.getEnsureVisible() != null && criteria.getEnsureVisible().size() > 0) {
            for (final ExplorerNodeKey ensureVisible : criteria.getEnsureVisible()) {
                ExplorerNode parent = masterTreeModel.getParent(ensureVisible.getUuid());
                while (parent != null) {
                    forcedOpen.add(ExplorerNode.builder()
                            .docRef(parent.getDocRef())
                            .rootNodeUuid(ensureVisible.getRootNodeUuid())
                            .build()
                            .getUniqueKey());
                    parent = masterTreeModel.getParent(parent);
                }
            }
        }

        // Add nodes that should be forced open because they are deeper than the minimum expansion depth.
        if (criteria.getMinDepth() != null && criteria.getMinDepth() > 0) {
            forceMinDepthOpen(masterTreeModel, forcedOpen, null, null,
                    criteria.getMinDepth(), 1);
        }

        return forcedOpen;
    }

    private void forceMinDepthOpen(final TreeModel masterTreeModel,
                                   final Set<ExplorerNodeKey> forcedOpen,
                                   final ExplorerNode rootNode,
                                   final ExplorerNode parent,
                                   final int minDepth,
                                   final int depth) {
        final List<ExplorerNode> children = masterTreeModel.getChildren(parent);
        if (children != null) {
            for (final ExplorerNode child : children) {
                final ExplorerNode childWithRootNode = child.copy()
                        .rootNodeUuid(Objects.requireNonNullElse(rootNode, child))
                        .build();
                forcedOpen.add(childWithRootNode.getUniqueKey());
                if (minDepth > depth) {
                    forceMinDepthOpen(masterTreeModel, forcedOpen, rootNode == null ? child : rootNode,
                            child, minDepth, depth + 1);
                }
            }
        }
    }

    private boolean addDescendants(final ExplorerNode rootNode,
                                   final ExplorerNode parent,
                                   final TreeModel treeModelIn,
                                   final FilteredTreeModel treeModelOut,
                                   final ExplorerTreeFilter filter,
                                   final Predicate<DocRef> filterPredicate,
                                   final boolean ignoreNameFilter,
                                   final Set<ExplorerNodeKey> allOpenItems,
                                   final int currentDepth) {
        int added = 0;

        final List<ExplorerNode> children = treeModelIn.getChildren(parent);
        if (children != null) {
            // Add all children if the name filter has changed or the parent item is open.
            final boolean addAllChildren = (filter.isNameFilterChange() && filter.getNameFilter() != null)
                    || parent == null || allOpenItems.contains(parent.getUniqueKey());

            // We need to add at least one item to the tree to be able to determine if the parent is a leaf node.
            final Iterator<ExplorerNode> iterator = children.iterator();
            while (iterator.hasNext() && (addAllChildren || added == 0)) {
                final ExplorerNode child = iterator.next();

                // Decorate the child with the root parent node, so the same child can be referenced in multiple
                // parent roots (e.g. System or Favourites)
                final ExplorerNode childWithParent = child.copy()
                        .rootNodeUuid(Objects.requireNonNullElse(rootNode, child))
                        .build();

                // We don't want to filter child items if the parent folder matches the name filter.
                final boolean ignoreChildNameFilter = filterPredicate.test(childWithParent.getDocRef());

                // Recurse right down to find out if a descendant is being added and therefore if we need to
                // include this as an ancestor.
                final boolean hasChildren = addDescendants(
                        Objects.requireNonNullElse(rootNode, childWithParent),
                        childWithParent,
                        treeModelIn,
                        treeModelOut,
                        filter,
                        filterPredicate,
                        ignoreChildNameFilter,
                        allOpenItems,
                        currentDepth + 1);
                if (hasChildren) {
                    treeModelOut.add(parent, childWithParent);
                    added++;

                } else if (checkType(childWithParent, filter.getIncludedTypes())
                        && checkTags(childWithParent, filter.getTags())
                        && (ignoreNameFilter || filterPredicate.test(childWithParent.getDocRef()))
                        && checkSecurity(childWithParent, filter.getRequiredPermissions())) {
                    treeModelOut.add(parent, childWithParent);
                    added++;
                }
            }
        }

        return added > 0;
    }

    private boolean checkSecurity(final ExplorerNode explorerNode, final Set<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.size() == 0) {
            return false;
        }

        final String uuid = explorerNode.getDocRef().getUuid();
        for (final String permission : requiredPermissions) {
            if (!securityContext.hasDocumentPermission(uuid, permission)) {
                return false;
            }
        }

        return true;
    }

    static boolean checkType(final ExplorerNode explorerNode, final Set<String> types) {
        return types == null || types.contains(explorerNode.getType());
    }

    static boolean checkTags(final ExplorerNode explorerNode, final Set<String> tags) {
        if (tags == null) {
            return true;
        } else if (explorerNode.getTags() != null && explorerNode.getTags().length() > 0 && tags.size() > 0) {
            for (final String tag : tags) {
                if (explorerNode.getTags().contains(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    private List<ExplorerNode> addRoots(final FilteredTreeModel filteredModel,
                                        final Set<ExplorerNodeKey> openItems,
                                        final Set<ExplorerNodeKey> forcedOpenItems,
                                        final Set<ExplorerNodeKey> temporaryOpenItems,
                                        final List<ExplorerNodeKey> openedItems) {
        final List<ExplorerNode> rootNodes = new ArrayList<>();
        final List<ExplorerNode> children = filteredModel.getChildren(null);

        if (children != null) {
            final List<ExplorerNode> sortedChildren = children
                    .stream()
                    .sorted(Comparator.comparing(ExplorerNode::getName))
                    .toList();
            for (final ExplorerNode child : sortedChildren) {
                final ExplorerNode copy =
                        addChildren(
                                child,
                                child,
                                filteredModel,
                                openItems,
                                forcedOpenItems,
                                temporaryOpenItems,
                                0,
                                openedItems);
                rootNodes.add(copy);
            }
        }
        return rootNodes;
    }

    private ExplorerNode addChildren(final ExplorerNode rootNode,
                                     final ExplorerNode parent,
                                     final FilteredTreeModel filteredModel,
                                     final Set<ExplorerNodeKey> openItems,
                                     final Set<ExplorerNodeKey> forcedOpenItems,
                                     final Set<ExplorerNodeKey> temporaryOpenItems,
                                     final int currentDepth,
                                     final List<ExplorerNodeKey> openedItems) {
        ExplorerNode.Builder builder = parent.copy();
        builder.depth(currentDepth);

        final ExplorerNodeKey parentNodeKey = parent.getUniqueKey();

        // See if we need to force this item open.
        boolean force = false;
        if (forcedOpenItems.contains(parentNodeKey)) {
            force = true;
            openedItems.add(parentNodeKey);
        } else if (temporaryOpenItems != null && temporaryOpenItems.contains(parentNodeKey)) {
            force = true;
        }

        final List<ExplorerNode> children = filteredModel.getChildren(parent);
        if (children == null) {
            builder.nodeState(NodeState.LEAF);
        } else if (force || openItems.contains(parentNodeKey)) {
            final List<ExplorerNode> newChildren = new ArrayList<>();
            for (final ExplorerNode child : children) {
                final ExplorerNode copy = addChildren(
                        rootNode,
                        child,
                        filteredModel,
                        openItems,
                        forcedOpenItems,
                        temporaryOpenItems,
                        currentDepth + 1,
                        openedItems);
                newChildren.add(copy);
            }

            builder.nodeState(NodeState.OPEN);
            builder.children(newChildren);
            builder.rootNodeUuid(rootNode);

        } else {
            builder.nodeState(NodeState.CLOSED);
        }

        return builder.build();
    }

    @Override
    public ExplorerNode create(final String type,
                               final String name,
                               final ExplorerNode destinationFolder,
                               final PermissionInheritance permissionInheritance) {
        final DocRef folderRef = getDestinationFolderRef(destinationFolder);
        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);

        DocRef result;

        // Create the document.
        try {
            // Check that the user is allowed to create an item of this type in the destination folder.
            checkCreatePermission(getUUID(folderRef), type);
            // Create an item of the specified type in the destination folder.
            result = handler.createDocument(name);
            explorerEventLog.create(type, name, result.getUuid(), folderRef, permissionInheritance, null);
        } catch (final RuntimeException e) {
            explorerEventLog.create(type, name, null, folderRef, permissionInheritance, e);
            throw e;
        }

        // Create the explorer node.
        explorerNodeService.createNode(result, folderRef, permissionInheritance);

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return ExplorerNode.builder()
                .docRef(result)
                .rootNodeUuid(destinationFolder != null ? destinationFolder.getRootNodeUuid() : null)
                .build();
    }

    /**
     * Returns the DocRef of a destination folder
     * @param destinationFolder If null, looks up the default root node
     */
    private DocRef getDestinationFolderRef(final ExplorerNode destinationFolder) {
        if (destinationFolder != null) {
            return destinationFolder.getDocRef();
        }

        final ExplorerNode rootNode = explorerNodeService.getRoot().orElse(null);
        return rootNode != null ? rootNode.getDocRef() : null;
    }

    @Override
    public BulkActionResult copy(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
                                 final PermissionInheritance permissionInheritance) {
        final StringBuilder resultMessage = new StringBuilder();

        // Create a map to store source to destination document references.
        final Map<ExplorerNode, ExplorerNode> remappings = new HashMap<>();

        // Discover all affected folder children.
        final Map<ExplorerNode, List<ExplorerNode>> childMap = new HashMap<>();
        createChildMap(explorerNodes, childMap);

        // Perform a copy on the selected items.
        explorerNodes.forEach(sourceNode -> copy(
                sourceNode,
                destinationFolder,
                permissionInheritance,
                resultMessage,
                remappings));

        // Recursively copy any selected folders.
        explorerNodes.forEach(sourceNode -> recurseCopy(
                sourceNode,
                permissionInheritance,
                resultMessage,
                remappings,
                childMap));

        // Remap all dependencies for the copied items.
        remappings.values().forEach(newExplorerNode -> {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(newExplorerNode.getType());
            if (handler != null) {
                final HashMap<DocRef, DocRef> docRefRemappings = new HashMap<>();
                for (final var remapping : remappings.entrySet()) {
                    docRefRemappings.put(remapping.getKey().getDocRef(), remapping.getValue().getDocRef());
                }
                handler.remapDependencies(newExplorerNode.getDocRef(), docRefRemappings);
            }
        });

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(new ArrayList<>(remappings.values()), resultMessage.toString());
    }

    /**
     * Copy an item into a destination folder.
     *
     * @param sourceNode            The explorer node being copied
     * @param destinationFolder     The destination folder explorer node
     * @param permissionInheritance The mode of permission inheritance being used for the whole operation
     * @param resultMessage         Allow contribution to result message
     * @param remappings            A map to store source to destination document references
     */
    private void copy(final ExplorerNode sourceNode,
                      final ExplorerNode destinationFolder,
                      final PermissionInheritance permissionInheritance,
                      final StringBuilder resultMessage,
                      final Map<ExplorerNode, ExplorerNode> remappings) {

        final DocRef destinationFolderRef = getDestinationFolderRef(destinationFolder);

        try {
            // Ensure we haven't already copied this item as part of a folder copy.
            if (!remappings.containsKey(sourceNode)) {
                // Get a handler to perform the copy.
                final ExplorerActionHandler handler = explorerActionHandlers.getHandler(sourceNode.getType());

                // Check that the user is allowed to create an item of this type in the destination folder.
                checkCreatePermission(getUUID(destinationFolderRef), sourceNode.getType());

                // Find out names of other items in the destination folder.
                final Set<String> otherDestinationChildrenNames = explorerNodeService.getChildren(destinationFolderRef)
                        .stream()
                        .map(ExplorerNode::getDocRef)
                        .filter(docRef -> docRef.getType().equals(sourceNode.getType()))
                        .map(DocRef::getName)
                        .collect(Collectors.toSet());

                // Copy the item to the destination folder.
                final DocRef destinationDocRef = handler.copyDocument(sourceNode.getDocRef(),
                        otherDestinationChildrenNames);
                explorerEventLog.copy(sourceNode.getDocRef(), destinationFolderRef, permissionInheritance,
                        null);

                // Create the explorer node
                if (destinationDocRef != null) {
                    // Copy the explorer node.
                    explorerNodeService.copyNode(sourceNode.getDocRef(),
                            destinationDocRef,
                            destinationFolderRef,
                            permissionInheritance);

                    // Record where the document got copied from -> to.
                    remappings.put(sourceNode, ExplorerNode.builder()
                            .docRef(destinationDocRef)
                            .rootNodeUuid(destinationFolder != null ? destinationFolder.getRootNodeUuid() : null)
                            .build());
                }
            }

        } catch (final RuntimeException e) {
            explorerEventLog.copy(sourceNode.getDocRef(), destinationFolderRef, permissionInheritance, e);
            resultMessage.append("Unable to copy '");
            resultMessage.append(sourceNode.getName());
            resultMessage.append("' ");
            resultMessage.append(e.getMessage());
            resultMessage.append("\n");
        }
    }

    /**
     * Copy the contents of a folder recursively
     *
     * @param sourceFolder          The explorer node being copied
     * @param permissionInheritance The mode of permission inheritance being used for the whole operation
     * @param resultMessage         Allow contribution to result message
     * @param remappings            A map to store source to destination explorer nodes
     * @param childMap              A map of folders and their child items.
     */
    private void recurseCopy(final ExplorerNode sourceFolder,
                             final PermissionInheritance permissionInheritance,
                             final StringBuilder resultMessage,
                             final Map<ExplorerNode, ExplorerNode> remappings,
                             final Map<ExplorerNode, List<ExplorerNode>> childMap) {
        final ExplorerNode destinationFolder = remappings.get(sourceFolder);
        if (destinationFolder != null) {
            final List<ExplorerNode> children = childMap.get(sourceFolder);
            if (children != null && children.size() > 0) {
                children.forEach(child -> {
                    copy(child, destinationFolder, permissionInheritance, resultMessage, remappings);
                    recurseCopy(child, permissionInheritance, resultMessage, remappings, childMap);
                });
            }
        }
    }

    /**
     * Create a map of folders and their child items.
     */
    private void createChildMap(final List<ExplorerNode> explorerNodes,
                                final Map<ExplorerNode, List<ExplorerNode>> childMap) {
        explorerNodes.forEach(explorerNode -> {
            final List<ExplorerNode> children = explorerNodeService.getChildren(explorerNode.getDocRef());
            if (children != null && children.size() > 0) {
                childMap.put(explorerNode, children);
                createChildMap(children, childMap);
            }
        });
    }

    @Override
    public BulkActionResult move(final List<ExplorerNode> explorerNodes,
                                 final ExplorerNode destinationFolder,
                                 final PermissionInheritance permissionInheritance) {
        final DocRef folderRef = getDestinationFolderRef(destinationFolder);
        final List<ExplorerNode> resultNodes = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final ExplorerNode explorerNode : explorerNodes) {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(explorerNode.getType());

            DocRef result = null;

            try {
                // Check that the user is allowed to create an item of this type in the destination folder.
                checkCreatePermission(getUUID(folderRef), explorerNode.getType());
                // Move the item.
                result = handler.moveDocument(explorerNode.getUuid());
                explorerEventLog.move(explorerNode.getDocRef(), folderRef, permissionInheritance, null);
                resultNodes.add(ExplorerNode.builder()
                        .docRef(result)
                        .rootNodeUuid(explorerNode.getRootNodeUuid())
                        .build());

            } catch (final RuntimeException e) {
                explorerEventLog.move(explorerNode.getDocRef(), folderRef, permissionInheritance, e);
                resultMessage.append("Unable to move '");
                resultMessage.append(explorerNode.getName());
                resultMessage.append("' ");
                resultMessage.append(e.getMessage());
                resultMessage.append("\n");
            }

            // Create the explorer node
            if (result != null) {
                explorerNodeService.moveNode(result, folderRef, permissionInheritance);
            }
        }

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(resultNodes, resultMessage.toString());
    }

    @Override
    public ExplorerNode rename(final ExplorerNode explorerNode, final String docName) {
        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(explorerNode.getType());
        final ExplorerNode result = rename(handler, explorerNode, docName);

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return result;
    }

    private ExplorerNode rename(final ExplorerActionHandler handler,
                                final ExplorerNode explorerNode,
                                final String docName) {
        DocRef result;

        try {
            result = handler.renameDocument(explorerNode.getUuid(), docName);
            explorerEventLog.rename(explorerNode.getDocRef(), docName, null);
        } catch (final RuntimeException e) {
            explorerEventLog.rename(explorerNode.getDocRef(), docName, e);
            throw e;
        }

        // Rename the explorer node.
        explorerNodeService.renameNode(result);

        return ExplorerNode.builder()
                .docRef(result)
                .rootNodeUuid(explorerNode.getRootNodeUuid())
                .build();
    }

    @Override
    public BulkActionResult delete(final List<ExplorerNode> explorerNodes) {
        final List<ExplorerNode> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        final HashSet<ExplorerNode> deleted = new HashSet<>();
        explorerNodes.forEach(explorerNode -> {
            // Check this document hasn't already been deleted.
            if (!deleted.contains(explorerNode)) {
                recursiveDelete(explorerNodes, deleted, resultDocRefs, resultMessage);
            }
        });

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    private void recursiveDelete(final List<ExplorerNode> explorerNodes,
                                 final HashSet<ExplorerNode> deleted,
                                 final List<ExplorerNode> resultDocRefs,
                                 final StringBuilder resultMessage) {
        explorerNodes.forEach(explorerNode -> {
            // Check this document hasn't already been deleted.
            if (!deleted.contains(explorerNode)) {
                // Get any children that might need to be deleted.
                List<ExplorerNode> children = explorerNodeService.getChildren(explorerNode.getDocRef());
                if (children != null && children.size() > 0) {
                    // Recursive delete.
                    recursiveDelete(children, deleted, resultDocRefs, resultMessage);
                }

                // Check to see if we still have children.
                children = explorerNodeService.getChildren(explorerNode.getDocRef());
                if (children != null && children.size() > 0) {
                    final String message = "Unable to delete '" + explorerNode.getName() +
                            "' because the folder is not empty";
                    resultMessage.append(message);
                    resultMessage.append("\n");
                    explorerEventLog.delete(explorerNode.getDocRef(), new RuntimeException(message));

                } else {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(explorerNode.getType());
                    try {
                        handler.deleteDocument(explorerNode.getUuid());
                        explorerEventLog.delete(explorerNode.getDocRef(), null);
                        deleted.add(explorerNode);
                        resultDocRefs.add(explorerNode);

                        // Delete the explorer node.
                        explorerNodeService.deleteNode(explorerNode.getDocRef());

                    } catch (final Exception e) {
                        explorerEventLog.delete(explorerNode.getDocRef(), e);
                        resultMessage.append("Unable to delete '");
                        resultMessage.append(explorerNode.getName());
                        resultMessage.append("' ");
                        resultMessage.append(e.getMessage());
                        resultMessage.append("\n");
                    }
                }
            }
        });
    }

    @Override
    public void rebuildTree() {
        explorerTreeModel.rebuild();
    }

    @Override
    public void clear() {
        explorerTreeModel.clear();
    }

    @Override
    public List<DocumentType> getNonSystemTypes() {
        return explorerActionHandlers.getNonSystemTypes();
    }

    @Override
    public List<DocumentType> getVisibleTypes() {
        // Get the master tree model.
        final TreeModel masterTreeModel = explorerTreeModel.getModel();

        // Filter the model by user permissions.
        final Set<String> requiredPermissions = new HashSet<>();
        requiredPermissions.add(DocumentPermissionNames.READ);

        final Set<String> visibleTypes = new HashSet<>();
        addTypes(null, masterTreeModel, visibleTypes, requiredPermissions);

        return getDocumentTypes(visibleTypes);
    }

    private boolean addTypes(final ExplorerNode parent,
                             final TreeModel treeModel,
                             final Set<String> types,
                             final Set<String> requiredPermissions) {
        boolean added = false;

        final List<ExplorerNode> children = treeModel.getChildren(parent);
        if (children != null) {
            for (final ExplorerNode child : children) {
                // Recurse right down to find out if a descendant is being added and therefore if we need to
                // include this type as it is an ancestor.
                final boolean hasChildren = addTypes(child, treeModel, types, requiredPermissions);
                if (hasChildren) {
                    types.add(child.getType());
                    added = true;
                } else if (checkSecurity(child, requiredPermissions)) {
                    types.add(child.getType());
                    added = true;
                }
            }
        }

        return added;
    }

    private List<DocumentType> getDocumentTypes(final Collection<String> visibleTypes) {
        return getNonSystemTypes().stream()
                .filter(type -> visibleTypes.contains(type.getType()))
                .collect(Collectors.toList());
    }

    private String getUUID(final DocRef docRef) {
        return Optional.ofNullable(docRef)
                .map(DocRef::getUuid)
                .orElse(null);
    }

    private void checkCreatePermission(final String folderUUID, final String type) {
        // Only allow administrators to create documents with no folder.
        if (folderUUID == null) {
            if (!securityContext.isAdmin()) {
                throw new PermissionException(securityContext.getUserId(),
                        "Only administrators can create root level entries");
            }
        } else {
            if (!securityContext.hasDocumentPermission(folderUUID,
                    DocumentPermissionNames.getDocumentCreatePermission(type))) {
                throw new PermissionException(securityContext.getUserId(),
                        "You do not have permission to create (" + type + ") in folder " + folderUUID);
            }
        }
    }
}
