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

package stroom.explorer.server;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.HasNodeState;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope(StroomScope.PROTOTYPE)
class ExplorerServiceImpl implements ExplorerService {
    private final ExplorerNodeService explorerNodeService;
    private final ExplorerTreeModel explorerTreeModel;
    private final ExplorerActionHandlersImpl explorerActionHandlers;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;

    @Inject
    ExplorerServiceImpl(final ExplorerNodeService explorerNodeService,
                        final ExplorerTreeModel explorerTreeModel,
                        final ExplorerActionHandlersImpl explorerActionHandlers,
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
        HashSet<ExplorerNode> temporaryOpenItems = null;
        if (filter.isNameFilterChange() && filter.getNameFilter() != null) {
            temporaryOpenItems = new HashSet<>(filteredModel.getChildMap().keySet());
        }

        addRoots(filteredModel, criteria.getOpenItems(), forcedOpenItems, temporaryOpenItems, result);

        result.setTemporaryOpenedItems(temporaryOpenItems);
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

    private void forceMinDepthOpen(final TreeModel masterTreeModel, final Set<ExplorerNode> forcedOpen, final ExplorerNode parent, final int minDepth, final int depth) {
        final List<ExplorerNode> children = masterTreeModel.getChildMap().get(parent);
        for (final ExplorerNode child : children) {
            forcedOpen.add(child);
            if (minDepth > depth) {
                forceMinDepthOpen(masterTreeModel, forcedOpen, child, minDepth, depth + 1);
            }
        }
    }

    private boolean addDescendants(final ExplorerNode parent, final TreeModel treeModelIn, final TreeModel treeModelOut, final ExplorerTreeFilter filter, final boolean ignoreNameFilter, final Set<ExplorerNode> allOpenItemns, final int currentDepth) {
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

    private void addRoots(final TreeModel filteredModel, final Set<ExplorerNode> openItems, final Set<ExplorerNode> forcedOpenItems, final Set<ExplorerNode> temporaryOpenItems, final FetchExplorerNodeResult result) {
        final List<ExplorerNode> children = filteredModel.getChildMap().get(null);
        if (children != null) {
            for (final ExplorerNode child : children) {
                result.getTreeStructure().add(null, child);
                addChildren(child, filteredModel, openItems, forcedOpenItems, temporaryOpenItems, 1, result);
            }
        }
    }

    private void addChildren(final ExplorerNode parent, final TreeModel filteredModel, final Set<ExplorerNode> openItems, final Set<ExplorerNode> forcedOpenItems, final Set<ExplorerNode> temporaryOpenItems, final int currentDepth, final FetchExplorerNodeResult result) {
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
        final List<DocumentType> allTypes = getAllTypes();
        final List<DocumentType> visibleTypes = getVisibleTypes();
        return new DocumentTypes(allTypes, visibleTypes);
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

    private boolean addTypes(final ExplorerNode parent, final TreeModel treeModel, final Set<String> types, final Set<String> requiredPermissions) {
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

    private List<DocumentType> getAllTypes() {
        return explorerActionHandlers.getTypes();
    }

    private List<DocumentType> getDocumentTypes(final Collection<String> visibleTypes) {
        final List<DocumentType> allTypes = getAllTypes();
        return allTypes.stream().filter(type -> visibleTypes.contains(type.getType())).collect(Collectors.toList());
    }

    @Override
    public DocRef create(final String type, final String name, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        DocRef folderRef = destinationFolderRef;
        if (folderRef == null) {
            folderRef = explorerNodeService.getRoot().getDocRef();
        }

        final ExplorerActionHandler handler = explorerActionHandlers.getHandler(type);

        DocRef result;

        // Create the document.
        try {
            result = handler.createDocument(name, getUUID(folderRef));
            explorerEventLog.create(type, name, result.getUuid(), folderRef, permissionInheritance, null);
        } catch (final RuntimeException e) {
            explorerEventLog.create(type, name,null, folderRef, permissionInheritance, e);
            throw e;
        }

        // Create the explorer node.
        explorerNodeService.createNode(result, folderRef, permissionInheritance);

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return result;
    }

    @Override
    public BulkActionResult copy(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        DocRef folderRef = destinationFolderRef;
        if (folderRef == null) {
            folderRef = explorerNodeService.getRoot().getDocRef();
        }

        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());

            DocRef result = null;

            try {
                result = handler.copyDocument(docRef.getUuid(), getUUID(folderRef));
                explorerEventLog.copy(docRef, folderRef, permissionInheritance, null);
                resultDocRefs.add(result);

            } catch (final Exception e) {
                explorerEventLog.copy(docRef, folderRef, permissionInheritance, e);
                resultMessage.append("Unable to copy '");
                resultMessage.append(docRef.getName());
                resultMessage.append("' ");
                resultMessage.append(e.getMessage());
                resultMessage.append("\n");
            }

            // Create the explorer node
            if (result != null) {
                explorerNodeService.copyNode(docRef, result, folderRef, permissionInheritance);
            }
        }

        // Make sure the tree model is rebuilt.
        rebuildTree();

        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    @Override
    public BulkActionResult move(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        DocRef folderRef = destinationFolderRef;
        if (folderRef == null) {
            folderRef = explorerNodeService.getRoot().getDocRef();
        }

        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());

            DocRef result = null;

            try {
                result = handler.moveDocument(docRef.getUuid(), getUUID(folderRef));
                explorerEventLog.move(docRef, folderRef, permissionInheritance, null);
                resultDocRefs.add(result);

            } catch (final Exception e) {
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

        // Make sure the tree model is rebuilt.
        rebuildTree();

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

            } catch (final Exception e) {
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
    public void rebuildTree() {
        explorerTreeModel.setRebuildRequired(true);
    }

    private String getUUID(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        return docRef.getUuid();
    }

//    private void addDocumentPermissions(final DocRef source, final DocRef dest, final boolean owner) {
//        String sourceType = null;
//        String sourceUuid = null;
//        String destType = null;
//        String destUuid = null;
//
//        if (source != null) {
//            sourceType = source.getType();
//            sourceUuid = source.getUuid();
//        }
//
//        if (dest != null) {
//            destType = dest.getType();
//            destUuid = dest.getUuid();
//        }
//
//        securityContext.addDocumentPermissions(sourceType, sourceUuid, destType, destUuid, owner);
//    }
}