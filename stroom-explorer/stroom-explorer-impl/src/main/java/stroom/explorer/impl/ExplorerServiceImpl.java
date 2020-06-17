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
import stroom.explorer.api.ExplorerActionHandler;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeState;
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
import stroom.util.shared.PermissionException;
import stroom.util.shared.filter.FilterFieldDefinition;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
class ExplorerServiceImpl implements ExplorerService, CollectionService {

    // Bit of a fudge to allow folder searching but you can't use it with name/type as folder is a parent of the other
// items
//            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Folder"), docRef ->
//                    ExplorerConstants.FOLDER.equals(docRef.getType())
//                            ? docRef.getName()
//                            : null),
    private static final FilterFieldMappers<DocRef> FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FilterFieldDefinition.defaultField("Name"), DocRef::getName),
            FilterFieldMapper.of(FilterFieldDefinition.qualifiedField("Type"), DocRef::getType)
    );

    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;
    private final Provider<ExplorerDecorator> explorerDecoratorProvider;

    @Inject
    ExplorerServiceImpl(final ExplorerNodeService explorerNodeService,
                        final ExplorerTreeModel explorerTreeModel,
                        final ExplorerActionHandlers explorerActionHandlers,
                        final SecurityContext securityContext,
                        final ExplorerEventLog explorerEventLog,
                        final Provider<ExplorerDecorator> explorerDecoratorProvider) {
        this.explorerNodeService = explorerNodeService;
        this.explorerTreeModel = explorerTreeModel;
        this.explorerActionHandlers = explorerActionHandlers;
        this.securityContext = securityContext;
        this.explorerEventLog = explorerEventLog;
        this.explorerDecoratorProvider = explorerDecoratorProvider;
    }

    @Override
    public FetchExplorerNodeResult getData(final FindExplorerNodeCriteria criteria) {
        final ExplorerTreeFilter filter = criteria.getFilter();
        final FetchExplorerNodeResult result = new FetchExplorerNodeResult();

        // Get the master tree model.
        final TreeModel masterTreeModel = explorerTreeModel.getModel();

        // See if we need to open any more folders to see nodes we want to ensure are visible.
        final Set<String> forcedOpenItems = getForcedOpenItems(masterTreeModel, criteria);

        final Set<String> allOpenItems = new HashSet<>();
        allOpenItems.addAll(criteria.getOpenItems());
        allOpenItems.addAll(criteria.getTemporaryOpenedItems());
        allOpenItems.addAll(forcedOpenItems);

        final TreeModel filteredModel = new TreeModel();
        // Create the predicate for the current filter value
        final Predicate<DocRef> fuzzyMatchPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                filter.getNameFilter(), FIELD_MAPPERS);

        addDescendants(
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
            final Set<String> temporaryOpenItems;

            if (filter.getNameFilter() == null) {
                temporaryOpenItems = new HashSet<>();
            } else {
                temporaryOpenItems = new HashSet<>(filteredModel.getAllParents());
            }

            addRoots(filteredModel, criteria.getOpenItems(), forcedOpenItems, temporaryOpenItems, result);
            result.setTemporaryOpenedItems(temporaryOpenItems);
        } else {
            addRoots(
                    filteredModel,
                    criteria.getOpenItems(),
                    forcedOpenItems,
                    criteria.getTemporaryOpenedItems(),
                    result);
        }

        if (criteria.getFilter() != null &&
                criteria.getFilter().getTags() != null &&
                criteria.getFilter().getTags().contains(StandardTagNames.DATA_SOURCE)) {
            final ExplorerDecorator explorerDecorator = explorerDecoratorProvider.get();
            if (explorerDecorator != null) {
                final List<DocRef> additionalDocRefs = explorerDecorator.list();
                additionalDocRefs.forEach(docRef -> {
                    final ExplorerNode node = new ExplorerNode(
                            docRef.getType(),
                            docRef.getUuid(),
                            docRef.getName(),
                            StandardTagNames.DATA_SOURCE);
                    node.setNodeState(NodeState.LEAF);
                    node.setDepth(1);
                    node.setIconUrl(DocumentType.DOC_IMAGE_URL + "searchable.svg");
                    result.getRootNodes().get(0).getChildren().add(node);
                });
            }
        }

        return result;
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

    private Set<String> getForcedOpenItems(final TreeModel masterTreeModel,
                                           final FindExplorerNodeCriteria criteria) {
        final Set<String> forcedOpen = new HashSet<>();

        // Add parents of  nodes that we have been requested to ensure are visible.
        if (criteria.getEnsureVisible() != null && criteria.getEnsureVisible().size() > 0) {
            for (final String ensureVisible : criteria.getEnsureVisible()) {
                ExplorerNode parent = masterTreeModel.getParent(ensureVisible);
                while (parent != null) {
                    forcedOpen.add(parent.getUuid());
                    parent = masterTreeModel.getParent(parent);
                }
            }
        }

        // Add nodes that should be forced open because they are deeper than the minimum expansion depth.
        if (criteria.getMinDepth() != null && criteria.getMinDepth() > 0) {
            forceMinDepthOpen(masterTreeModel, forcedOpen, null, criteria.getMinDepth(), 1);
        }

        return forcedOpen;
    }

    private void forceMinDepthOpen(final TreeModel masterTreeModel,
                                   final Set<String> forcedOpen,
                                   final ExplorerNode parent,
                                   final int minDepth,
                                   final int depth) {
        final List<ExplorerNode> children = masterTreeModel.getChildren(parent);
        if (children != null) {
            for (final ExplorerNode child : children) {
                forcedOpen.add(child.getUuid());
                if (minDepth > depth) {
                    forceMinDepthOpen(masterTreeModel, forcedOpen, child, minDepth, depth + 1);
                }
            }
        }
    }

    private boolean addDescendants(final ExplorerNode parent,
                                   final TreeModel treeModelIn,
                                   final TreeModel treeModelOut,
                                   final ExplorerTreeFilter filter,
                                   final Predicate<DocRef> filterPredicate,
                                   final boolean ignoreNameFilter,
                                   final Set<String> allOpenItems,
                                   final int currentDepth) {
        int added = 0;

        final List<ExplorerNode> children = treeModelIn.getChildren(parent);
        if (children != null) {
            // Add all children if the name filter has changed or the parent item is open.
            final boolean addAllChildren = (filter.isNameFilterChange() && filter.getNameFilter() != null)
                    || parent == null || allOpenItems.contains(parent.getUuid());

            // We need to add add least one item to the tree to be able to determine if the parent is a leaf node.
            final Iterator<ExplorerNode> iterator = children.iterator();
            while (iterator.hasNext() && (addAllChildren || added == 0)) {
                final ExplorerNode child = iterator.next();

                // We don't want to filter child items if the parent folder matches the name filter.
                final boolean ignoreChildNameFilter = filterPredicate.test(child.getDocRef());

                // Recurse right down to find out if a descendant is being added and therefore if we need to include this as an ancestor.
                final boolean hasChildren = addDescendants(
                        child,
                        treeModelIn,
                        treeModelOut,
                        filter,
                        filterPredicate,
                        ignoreChildNameFilter,
                        allOpenItems,
                        currentDepth + 1);
                if (hasChildren) {
                    treeModelOut.add(parent, child);
                    added++;

                } else if (checkType(child, filter.getIncludedTypes())
                        && checkTags(child, filter.getTags())
                        && (ignoreNameFilter || filterPredicate.test(child.getDocRef()))
                        && checkSecurity(child, filter.getRequiredPermissions())) {
                    treeModelOut.add(parent, child);
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

    private void addRoots(final TreeModel filteredModel,
                          final Set<String> openItems,
                          final Set<String> forcedOpenItems,
                          final Set<String> temporaryOpenItems,
                          final FetchExplorerNodeResult result) {
        final List<ExplorerNode> children = filteredModel.getChildren(null);
        if (children != null) {
            for (final ExplorerNode child : children) {
                final ExplorerNode copy = child.copy();
                result.getRootNodes().add(copy);
                addChildren(copy, filteredModel, openItems, forcedOpenItems, temporaryOpenItems, 0, result);
            }
        }
    }

    private void addChildren(final ExplorerNode parent,
                             final TreeModel filteredModel,
                             final Set<String> openItems,
                             final Set<String> forcedOpenItems,
                             final Set<String> temporaryOpenItems,
                             final int currentDepth,
                             final FetchExplorerNodeResult result) {
        final String parentUuid = parent.getUuid();
        parent.setDepth(currentDepth);

        // See if we need to force this item open.
        boolean force = false;
        if (forcedOpenItems.contains(parentUuid)) {
            force = true;
            result.getOpenedItems().add(parentUuid);
        } else if (temporaryOpenItems != null && temporaryOpenItems.contains(parentUuid)) {
            force = true;
        }

        final List<ExplorerNode> children = filteredModel.getChildren(parent);
        if (children == null) {
            parent.setNodeState(NodeState.LEAF);

        } else if (force || openItems.contains(parentUuid)) {
            parent.setNodeState(NodeState.OPEN);

            final List<ExplorerNode> newChildren = new ArrayList<>();
            parent.setChildren(newChildren);
            for (final ExplorerNode child : children) {
                final ExplorerNode copy = child.copy();
                newChildren.add(copy);
                addChildren(copy, filteredModel, openItems, forcedOpenItems, temporaryOpenItems, currentDepth + 1, result);
            }

        } else {
            parent.setNodeState(NodeState.CLOSED);
        }
    }

    @Override
    public DocRef create(final String type,
                         final String name,
                         final DocRef destinationFolderRef,
                         final PermissionInheritance permissionInheritance) {
        final DocRef folderRef = Optional.ofNullable(destinationFolderRef)
                .orElse(explorerNodeService.getRoot()
                        .map(ExplorerNode::getDocRef)
                        .orElse(null)
                );

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

        return result;
    }

    @Override
    public BulkActionResult copy(final List<DocRef> docRefs,
                                 final DocRef potentialDestinationFolderRef,
                                 final PermissionInheritance permissionInheritance) {
        final DocRef destinationFolderRef = Optional.ofNullable(potentialDestinationFolderRef)
                .orElse(explorerNodeService.getRoot()
                        .map(ExplorerNode::getDocRef)
                        .orElseThrow(() -> new RuntimeException("Cannot copy into null destination")));

        final StringBuilder resultMessage = new StringBuilder();

        // Create a map to store source to destination document references.
        final Map<DocRef, DocRef> remappings = new HashMap<>();

        // Discover all affected folder children.
        final Map<DocRef, List<DocRef>> childMap = new HashMap<>();
        createChildMap(docRefs, childMap);

        // Perform a copy on the selected items.
        docRefs.forEach(sourceDocRef -> copy(sourceDocRef,
                destinationFolderRef,
                permissionInheritance,
                resultMessage,
                remappings));

        // Recursively copy any selected folders.
        docRefs.forEach(sourceDocRef -> recurseCopy(sourceDocRef,
                permissionInheritance,
                resultMessage,
                remappings,
                childMap));

        // Remap all dependencies for the copied items.
        remappings.values().forEach(newDocRef -> {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(newDocRef.getType());
            if (handler != null) {
                handler.remapDependencies(newDocRef, remappings);
            }
        });

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(new ArrayList<>(remappings.values()), resultMessage.toString());
    }

    /**
     * Copy an item into a destination folder.
     *
     * @param sourceDocRef          The doc ref for the item being copied
     * @param destinationFolderRef  The doc ref for the destination folder
     * @param permissionInheritance The mode of permission inheritance being used for the whole operation
     * @param resultMessage         Allow contribution to result message
     * @param remappings            A map to store source to destination document references
     */
    private void copy(final DocRef sourceDocRef,
                      final DocRef destinationFolderRef,
                      final PermissionInheritance permissionInheritance,
                      final StringBuilder resultMessage,
                      final Map<DocRef, DocRef> remappings) {

        try {
            // Ensure we haven't already copied this item as part of a folder copy.
            if (!remappings.containsKey(sourceDocRef)) {
                // Get a handler to performt he copy.
                final ExplorerActionHandler handler = explorerActionHandlers.getHandler(sourceDocRef.getType());

                // Check that the user is allowed to create an item of this type in the destination folder.
                checkCreatePermission(getUUID(destinationFolderRef), sourceDocRef.getType());

                // Find out names of other items in the destination folder.
                final Set<String> otherDestinationChildrenNames = explorerNodeService.getChildren(destinationFolderRef)
                        .stream()
                        .map(ExplorerNode::getDocRef)
                        .filter(docRef -> docRef.getType().equals(sourceDocRef.getType()))
                        .map(DocRef::getName)
                        .collect(Collectors.toSet());

                // Copy the item to the destination folder.
                final DocRef destinationDocRef = handler.copyDocument(sourceDocRef, otherDestinationChildrenNames);
                explorerEventLog.copy(sourceDocRef, destinationFolderRef, permissionInheritance, null);

                // Create the explorer node
                if (destinationDocRef != null) {
                    // Copy the explorer node.
                    explorerNodeService.copyNode(sourceDocRef, destinationDocRef, destinationFolderRef, permissionInheritance);

                    // Record where the document got copied from -> to.
                    remappings.put(sourceDocRef, destinationDocRef);
                }
            }

        } catch (final RuntimeException e) {
            explorerEventLog.copy(sourceDocRef, destinationFolderRef, permissionInheritance, e);
            resultMessage.append("Unable to copy '");
            resultMessage.append(sourceDocRef.getName());
            resultMessage.append("' ");
            resultMessage.append(e.getMessage());
            resultMessage.append("\n");
        }
    }

    /**
     * Copy the contents of a folder recursively
     *
     * @param sourceFolderRef       The doc ref for the folder being copied
     * @param permissionInheritance The mode of permission inheritance being used for the whole operation
     * @param resultMessage         Allow contribution to result message
     * @param remappings            A map to store source to destination document references
     * @param childMap              A map of folders and their child items.
     */
    private void recurseCopy(final DocRef sourceFolderRef,
                             final PermissionInheritance permissionInheritance,
                             final StringBuilder resultMessage,
                             final Map<DocRef, DocRef> remappings,
                             final Map<DocRef, List<DocRef>> childMap) {
        final DocRef destinationFolderRef = remappings.get(sourceFolderRef);
        if (destinationFolderRef != null) {
            final List<DocRef> children = childMap.get(sourceFolderRef);
            if (children != null && children.size() > 0) {
                children.forEach(child -> {
                    copy(child, destinationFolderRef, permissionInheritance, resultMessage, remappings);
                    recurseCopy(child, permissionInheritance, resultMessage, remappings, childMap);
                });
            }
        }
    }

    /**
     * Create a map of folders and their child items.
     */
    private void createChildMap(final List<DocRef> docRefs,
                                final Map<DocRef, List<DocRef>> childMap) {
        docRefs.forEach(docRef -> {
            final List<ExplorerNode> children = explorerNodeService.getChildren(docRef);
            if (children != null && children.size() > 0) {
                final List<DocRef> childDocRefs = children
                        .stream()
                        .map(ExplorerNode::getDocRef)
                        .collect(Collectors.toList());
                childMap.put(docRef, childDocRefs);
                createChildMap(childDocRefs, childMap);
            }
        });
    }

    @Override
    public BulkActionResult move(final List<DocRef> docRefs,
                                 final DocRef destinationFolderRef,
                                 final PermissionInheritance permissionInheritance) {
        final DocRef folderRef = Optional.ofNullable(destinationFolderRef)
                .orElse(explorerNodeService.getRoot()
                        .map(ExplorerNode::getDocRef)
                        .orElse(null));

        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());

            DocRef result = null;

            try {
                // Check that the user is allowed to create an item of this type in the destination folder.
                checkCreatePermission(getUUID(folderRef), docRef.getType());
                // Move the item.
                result = handler.moveDocument(docRef.getUuid());
                explorerEventLog.move(docRef, folderRef, permissionInheritance, null);
                resultDocRefs.add(result);

            } catch (final RuntimeException e) {
                explorerEventLog.move(docRef, folderRef, permissionInheritance, e);
                resultMessage.append("Unable to move '");
                resultMessage.append(docRef.getName());
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

        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    @Override
    public DocRef rename(final DocRef docRef, final String docName) {
        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());

        final DocRef result = rename(handler, docRef, docName);

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return result;
    }

    private DocRef rename(final ExplorerActionHandler handler,
                          final DocRef docRef,
                          final String docName) {
        DocRef result;

        try {
            result = handler.renameDocument(docRef.getUuid(), docName);
            explorerEventLog.rename(docRef, docName, null);
        } catch (final RuntimeException e) {
            explorerEventLog.rename(docRef, docName, e);
            throw e;
        }

        // Rename the explorer node.
        explorerNodeService.renameNode(result);

        return result;
    }

    @Override
    public BulkActionResult delete(final List<DocRef> docRefs) {
        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        final HashSet<DocRef> deleted = new HashSet<>();
        docRefs.forEach(docRef -> {
            // Check this document hasn't already been deleted.
            if (!deleted.contains(docRef)) {
                recursiveDelete(docRefs, deleted, resultDocRefs, resultMessage);
            }
        });

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    private void recursiveDelete(final List<DocRef> docRefs, final HashSet<DocRef> deleted, final List<DocRef> resultDocRefs, final StringBuilder resultMessage) {
        docRefs.forEach(docRef -> {
            // Check this document hasn't already been deleted.
            if (!deleted.contains(docRef)) {
                // Get any children that might need to be deleted.
                List<ExplorerNode> children = explorerNodeService.getChildren(docRef);
                if (children != null && children.size() > 0) {
                    // Recursive delete.
                    final List<DocRef> childDocRefs = children.stream().map(ExplorerNode::getDocRef).collect(Collectors.toList());
                    recursiveDelete(childDocRefs, deleted, resultDocRefs, resultMessage);
                }

                // Check to see if we still have children.
                children = explorerNodeService.getChildren(docRef);
                if (children != null && children.size() > 0) {
                    final String message = "Unable to delete '" + docRef.getName() + "' because the folder is not empty";
                    resultMessage.append(message);
                    resultMessage.append("\n");
                    explorerEventLog.delete(docRef, new RuntimeException(message));

                } else {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());
                    try {
                        handler.deleteDocument(docRef.getUuid());
                        explorerEventLog.delete(docRef, null);
                        deleted.add(docRef);
                        resultDocRefs.add(docRef);

                        // Delete the explorer node.
                        explorerNodeService.deleteNode(docRef);

                    } catch (final Exception e) {
                        explorerEventLog.delete(docRef, e);
                        resultMessage.append("Unable to delete '");
                        resultMessage.append(docRef.getName());
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
                // Recurse right down to find out if a descendant is being added and therefore if we need to include this type as it is an ancestor.
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
                throw new PermissionException(securityContext.getUserId(), "Only administrators can create root level entries");
            }
        } else {
            if (!securityContext.hasDocumentPermission(folderUUID, DocumentPermissionNames.getDocumentCreatePermission(type))) {
                throw new PermissionException(securityContext.getUserId(), "You do not have permission to create (" + type + ") in folder " + folderUUID);
            }
        }
    }
}