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

package stroom.explorer;

import stroom.entity.shared.PermissionException;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.feed.shared.FeedDoc;
import stroom.docref.DocRef;
import stroom.query.api.v2.DocRefInfo;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.HasNodeState;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ExplorerServiceImpl implements ExplorerService {
    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlers explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;

    @Inject
    ExplorerServiceImpl(final ExplorerNodeService explorerNodeService,
                        final ExplorerTreeModel explorerTreeModel,
                        final ExplorerActionHandlers explorerActionHandlers,
                        final SecurityContext securityContext,
                        final ExplorerEventLog explorerEventLog) {
        this.explorerNodeService = explorerNodeService;
        this.explorerTreeModel = explorerTreeModel;
        this.explorerActionHandlers = explorerActionHandlers;
        this.securityContext = securityContext;
        this.explorerEventLog = explorerEventLog;
    }

    @Override
    public FetchExplorerNodeResult getData(final FindExplorerNodeCriteria criteria) {
        final ExplorerTreeFilter filter = criteria.getFilter();
        final FetchExplorerNodeResult result = new FetchExplorerNodeResult();

        // Get the master tree model.
        final TreeModel masterTreeModel = explorerTreeModel.getModel();

        // See if we need to open any more folders to see nodes we want to ensure are visible.
        final Set<ExplorerNode> forcedOpenItems = getForcedOpenItems(masterTreeModel, criteria);

        final Set<ExplorerNode> allOpenItems = new HashSet<>();
        allOpenItems.addAll(criteria.getOpenItems());
        allOpenItems.addAll(forcedOpenItems);

        final TreeModel filteredModel = new TreeModelImpl();
        addDescendants(null, masterTreeModel, filteredModel, filter, false, allOpenItems, 0);

        // If the name filter has changed then we want to temporarily expand all nodes.
        if (filter.isNameFilterChange()) {
            final Set<ExplorerNode> temporaryOpenItems;

            if (filter.getNameFilter() == null) {
                temporaryOpenItems = new HashSet<>();
            } else {
                temporaryOpenItems = new HashSet<>(filteredModel.getChildMap().keySet());
            }

            addRoots(filteredModel, criteria.getOpenItems(), forcedOpenItems, temporaryOpenItems, result);
            result.setTemporaryOpenedItems(temporaryOpenItems);
        } else {
            addRoots(filteredModel, criteria.getOpenItems(), forcedOpenItems, criteria.getTemporaryOpenedItems(), result);
        }

        return result;
    }

    private Set<ExplorerNode> getForcedOpenItems(final TreeModel masterTreeModel,
                                                 final FindExplorerNodeCriteria criteria) {
        final Set<ExplorerNode> forcedOpen = new HashSet<>();

        // Add parents of  nodes that we have been requested to ensure are visible.
        if (criteria.getEnsureVisible() != null && criteria.getEnsureVisible().size() > 0) {
            for (final ExplorerNode ensureVisible : criteria.getEnsureVisible()) {

                ExplorerNode parent = masterTreeModel.getParentMap().get(ensureVisible);
                while (parent != null) {
                    forcedOpen.add(parent);
                    parent = masterTreeModel.getParentMap().get(parent);
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
                                   final Set<ExplorerNode> forcedOpen,
                                   final ExplorerNode parent,
                                   final int minDepth,
                                   final int depth) {
        final List<ExplorerNode> children = masterTreeModel.getChildMap().get(parent);
        if (children != null) {
            for (final ExplorerNode child : children) {
                forcedOpen.add(child);
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
                                   final boolean ignoreNameFilter,
                                   final Set<ExplorerNode> allOpenItemns,
                                   final int currentDepth) {
        int added = 0;

        final List<ExplorerNode> children = treeModelIn.getChildMap().get(parent);
        if (children != null) {
            // Add all children if the name filter has changed or the parent item is open.
            final boolean addAllChildren = (filter.isNameFilterChange() && filter.getNameFilter() != null) || allOpenItemns.contains(parent);

            // We need to add add least one item to the tree to be able to determine if the parent is a leaf node.
            final Iterator<ExplorerNode> iterator = children.iterator();
            while (iterator.hasNext() && (addAllChildren || added == 0)) {
                final ExplorerNode child = iterator.next();

                // We don't want to filter child items if the parent folder matches the name filter.
                final boolean ignoreChildNameFilter = checkName(child, filter.getNameFilter());

                // Recurse right down to find out if a descendant is being added and therefore if we need to include this as an ancestor.
                final boolean hasChildren = addDescendants(child, treeModelIn, treeModelOut, filter, ignoreChildNameFilter, allOpenItemns, currentDepth + 1);
                if (hasChildren) {
                    treeModelOut.add(parent, child);
                    added++;

                } else if (checkType(child, filter.getIncludedTypes())
                        && checkTags(child, filter.getTags())
                        && (ignoreNameFilter || checkName(child, filter.getNameFilter()))
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

        final String type = explorerNode.getType();
        final String uuid = explorerNode.getDocRef().getUuid();
        for (final String permission : requiredPermissions) {
            if (!securityContext.hasDocumentPermission(type, uuid, permission)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkType(final ExplorerNode explorerNode, final Set<String> types) {
        return types == null || types.contains(explorerNode.getType());
    }

    private boolean checkTags(final ExplorerNode explorerNode, final Set<String> tags) {
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

    private boolean checkName(final ExplorerNode explorerNode, final String nameFilter) {
        return nameFilter == null || explorerNode.getDisplayValue().toLowerCase().contains(nameFilter.toLowerCase());
    }

    private void addRoots(final TreeModel filteredModel,
                          final Set<ExplorerNode> openItems,
                          final Set<ExplorerNode> forcedOpenItems,
                          final Set<ExplorerNode> temporaryOpenItems,
                          final FetchExplorerNodeResult result) {
        final List<ExplorerNode> children = filteredModel.getChildMap().get(null);
        if (children != null) {
            for (final ExplorerNode child : children) {
                result.getTreeStructure().add(null, child);
                addChildren(child, filteredModel, openItems, forcedOpenItems, temporaryOpenItems, 0, result);
            }
        }
    }

    private void addChildren(final ExplorerNode parent,
                             final TreeModel filteredModel,
                             final Set<ExplorerNode> openItems,
                             final Set<ExplorerNode> forcedOpenItems,
                             final Set<ExplorerNode> temporaryOpenItems,
                             final int currentDepth,
                             final FetchExplorerNodeResult result) {
        parent.setDepth(currentDepth);

        // See if we need to force this item open.
        boolean force = false;
        if (forcedOpenItems.contains(parent)) {
            force = true;
            result.getOpenedItems().add(parent);
        } else if (temporaryOpenItems != null && temporaryOpenItems.contains(parent)) {
            force = true;
        }

        final List<ExplorerNode> children = filteredModel.getChildMap().get(parent);
        if (children == null) {
            parent.setNodeState(HasNodeState.NodeState.LEAF);

        } else if (force || openItems.contains(parent)) {
            parent.setNodeState(HasNodeState.NodeState.OPEN);
            for (final ExplorerNode child : children) {
                result.getTreeStructure().add(parent, child);
                addChildren(child, filteredModel, openItems, forcedOpenItems, temporaryOpenItems, currentDepth + 1, result);
            }

        } else {
            parent.setNodeState(HasNodeState.NodeState.CLOSED);
        }
    }

    @Override
    public DocumentTypes getDocumentTypes() {
        final List<DocumentType> nonSystemTypes = getNonSystemTypes();
        final List<DocumentType> visibleTypes = getVisibleTypes();
        return new DocumentTypes(nonSystemTypes, visibleTypes);
    }

    private List<DocumentType> getVisibleTypes() {
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

        final List<ExplorerNode> children = treeModel.getChildMap().get(parent);
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

    private List<DocumentType> getNonSystemTypes() {
        return explorerActionHandlers.getNonSystemTypes();
    }

    private List<DocumentType> getDocumentTypes(final Collection<String> visibleTypes) {
        return getNonSystemTypes().stream()
                .filter(type -> visibleTypes.contains(type.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public DocRef create(final String type, final String name, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
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

        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        final Map<DocRef, List<ExplorerNode>> childNodesByParent = new HashMap<>();
        recurseGetNodes(docRefs.stream(), childNodesByParent::put);

        // Create the UUID's of the copies up front
        final Map<String, String> copiesByOriginalUuid = childNodesByParent.keySet().stream()
                .filter(d -> !d.getType().equals(FeedDoc.DOCUMENT_TYPE)) // we don't copy feeds
                .collect(Collectors.toMap(DocRef::getUuid, (d) -> UUID.randomUUID().toString()));

        docRefs.forEach(sourceDocRef ->
                explorerNodeService.getParent(sourceDocRef)
                        .map(ExplorerNode::getDocRef)
                        .ifPresent(sourceParent -> recurseCopy(sourceParent,
                                sourceDocRef,
                                destinationFolderRef,
                                permissionInheritance,
                                resultDocRefs,
                                resultMessage,
                                copiesByOriginalUuid,
                                childNodesByParent)
                        )
        );

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    /**
     * This traverses the explorer tree and creates a cache of child Explorer Nodes by their parent Doc Ref.
     * This will be used to pre-choose UUID's for all the copies to be made, and then used as a cache for executing
     * the copy.
     *
     * @param docRefs          The source doc refs being copied
     * @param childNodesByUuid The map of children by parent being built
     */
    private void recurseGetNodes(final Stream<DocRef> docRefs,
                                 final BiConsumer<DocRef, List<ExplorerNode>> childNodesByUuid) {
        docRefs.forEach(sourceDocRef -> {
            final List<ExplorerNode> sourceDescendants = explorerNodeService.getChildren(sourceDocRef);
            childNodesByUuid.accept(sourceDocRef, sourceDescendants);

            sourceDescendants.stream()
                    .map(ExplorerNode::getDocRef)
                    .map(Stream::of)
                    .forEach(l -> recurseGetNodes(l, childNodesByUuid));
        });
    }

    /**
     * Copy the contents of a folder recursively
     *
     * @param sourceDirectoryFolderRef The doc ref of the folder that the source belongs to
     * @param sourceDocRef             The doc ref for the folder being copied
     * @param destinationFolderRef     The doc ref for the destination folder
     * @param permissionInheritance    The mode of permission inheritance being used for the whole operation
     * @param resultDocRefs            Allow contribution to result doc refs
     * @param resultMessage            Allow contribution to result message
     * @param copiesByOriginalUuid     UUID's of the intended copies by their original UUID
     * @param childNodesByParent       A cached version of the explorer node tree
     */
    private void recurseCopy(final DocRef sourceDirectoryFolderRef,
                             final DocRef sourceDocRef,
                             final DocRef destinationFolderRef,
                             final PermissionInheritance permissionInheritance,
                             final List<DocRef> resultDocRefs,
                             final StringBuilder resultMessage,
                             final Map<String, String> copiesByOriginalUuid,
                             final Map<DocRef, List<ExplorerNode>> childNodesByParent) {
        final String destinationUuid = copiesByOriginalUuid.get(sourceDocRef.getUuid());
        if (null == destinationUuid) return;

        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(sourceDocRef.getType());

        try {
            // Check that the user is allowed to create an item of this type in the destination folder.
            checkCreatePermission(getUUID(destinationFolderRef), sourceDocRef.getType());
            // Copy the item to the destination folder.
            DocRef destinationDocRef = handler.copyDocument(sourceDocRef.getUuid(),
                    destinationUuid,
                    copiesByOriginalUuid);
            explorerEventLog.copy(sourceDocRef, destinationFolderRef, permissionInheritance, null);

            // Create the explorer node
            if (destinationDocRef != null) {
                explorerNodeService.copyNode(sourceDocRef, destinationDocRef, destinationFolderRef, permissionInheritance);

                // If the source directory and destination directory are the same, rename it with 'copy of'
                if (sourceDirectoryFolderRef.getUuid().equals(destinationFolderRef.getUuid())) {
                    final String copyName = getCopyName(destinationFolderRef, destinationDocRef);
                    destinationDocRef = rename(handler, destinationDocRef, copyName);
                }
            }
            resultDocRefs.add(destinationDocRef);

            // Handle recursion
            final DocRef finalDestination = destinationDocRef;
            childNodesByParent.get(sourceDocRef)
                    .forEach(sourceDescendant ->
                            recurseCopy(sourceDocRef,
                                    sourceDescendant.getDocRef(),
                                    finalDestination,
                                    permissionInheritance,
                                    resultDocRefs,
                                    resultMessage,
                                    copiesByOriginalUuid,
                                    childNodesByParent
                            )
                    );
        } catch (final RuntimeException e) {
            explorerEventLog.copy(sourceDocRef, destinationFolderRef, permissionInheritance, e);
            resultMessage.append("Unable to copy '");
            resultMessage.append(sourceDocRef.getName());
            resultMessage.append("' ");
            resultMessage.append(e.getMessage());
            resultMessage.append("\n");
        }
    }

    private String getCopyName(final DocRef destinationFolderDocRef,
                               final DocRef destinationDocRef) {

        final List<String> otherDestinationChildrenNames = explorerNodeService.getChildren(destinationFolderDocRef)
                .stream()
                .map(ExplorerNode::getDocRef)
                .map(DocRef::getName)
                .collect(Collectors.toList());

        int copyIndex = 0;
        String copyName = String.format("%s - Copy", destinationDocRef.getName());

        while (otherDestinationChildrenNames.contains(copyName)) {
            copyIndex++;
            copyName = String.format("%s - Copy %d", destinationDocRef.getName(), copyIndex);
        }


        return copyName;
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

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());
            try {
                handler.deleteDocument(docRef.getUuid());
                explorerEventLog.delete(docRef, null);
                resultDocRefs.add(docRef);

            } catch (final RuntimeException e) {
                explorerEventLog.delete(docRef, e);
                resultMessage.append("Unable to delete '");
                resultMessage.append(docRef.getName());
                resultMessage.append("' ");
                resultMessage.append(e.getMessage());
                resultMessage.append("\n");
            }

            // Delete the explorer node.
            explorerNodeService.deleteNode(docRef);
        }

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());

        return handler.info(docRef.getUuid());
    }

    @Override
    public void rebuildTree() {
        explorerTreeModel.rebuild();
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
            if (!securityContext.hasDocumentPermission(ExplorerConstants.FOLDER, folderUUID, DocumentPermissionNames.getDocumentCreatePermission(type))) {
                throw new PermissionException(securityContext.getUserId(), "You do not have permission to create (" + type + ") in folder " + folderUUID);
            }
        }
    }
}