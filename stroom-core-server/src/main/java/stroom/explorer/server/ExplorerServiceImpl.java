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
import stroom.entity.server.EntityServiceBeanRegistry;
import stroom.entity.server.FolderService;
import stroom.entity.server.NameValidationUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.PermissionInheritance;
import stroom.entity.shared.ProvidesNamePattern;
import stroom.explorer.shared.BulkActionResult;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerData;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerDataResult;
import stroom.explorer.shared.FindExplorerDataCriteria;
import stroom.folder.server.FolderRootExplorerDataProvider;
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

@Component
@Scope(StroomScope.PROTOTYPE)
class ExplorerServiceImpl implements ExplorerService {
    private final ExplorerTreeModel explorerTreeModel;
    private final EntityServiceBeanRegistry beanRegistry;
    private final SecurityContext securityContext;
    private final ExplorerEventLog explorerEventLog;

    @Inject
    ExplorerServiceImpl(final ExplorerTreeModel explorerTreeModel,
                        final EntityServiceBeanRegistry beanRegistry,
                        final SecurityContext securityContext,
                        final ExplorerEventLog explorerEventLog) {
        this.explorerTreeModel = explorerTreeModel;
        this.beanRegistry = beanRegistry;
        this.securityContext = securityContext;
        this.explorerEventLog = explorerEventLog;
    }

    @Override
    public FetchExplorerDataResult getData(final FindExplorerDataCriteria criteria) {
        final ExplorerTreeFilter filter = criteria.getFilter();
        final FetchExplorerDataResult result = new FetchExplorerDataResult();

        // Get the master tree model.
        final TreeModel masterTreeModel = explorerTreeModel.getModel();

        // See if we need to open any more folders to see nodes we want to ensure are visible.
        final Set<ExplorerData> forcedOpenItems = getForcedOpenItems(masterTreeModel, criteria);

        final Set<ExplorerData> allOpenItems = new HashSet<>();
        allOpenItems.addAll(criteria.getOpenItems());
        allOpenItems.addAll(forcedOpenItems);

        final TreeModel filteredModel = new TreeModelImpl();
        addDescendants(FolderRootExplorerDataProvider.ROOT, masterTreeModel, filteredModel, filter, false, allOpenItems, 0);

        // If the name filter has changed then we want to temporarily expand all nodes.
        HashSet<ExplorerData> temporaryOpenItems = null;
        if (filter.isNameFilterChange() && filter.getNameFilter() != null) {
            temporaryOpenItems = new HashSet<>(filteredModel.getChildMap().keySet());
        }

        // Add root node.
        result.getTreeStructure().add(null, FolderRootExplorerDataProvider.ROOT);
        addChildren(FolderRootExplorerDataProvider.ROOT, filteredModel, criteria.getOpenItems(), forcedOpenItems, temporaryOpenItems, 0, result);

        result.setTemporaryOpenedItems(temporaryOpenItems);
        return result;
    }

    private Set<ExplorerData> getForcedOpenItems(final TreeModel masterTreeModel, final FindExplorerDataCriteria criteria) {
        final Set<ExplorerData> forcedOpen = new HashSet<>();

        // Add parents of  nodes that we have been requested to ensure are visible.
        if (criteria.getEnsureVisible() != null && criteria.getEnsureVisible().size() > 0) {
            for (final ExplorerData ensureVisible : criteria.getEnsureVisible()) {

                ExplorerData parent = masterTreeModel.getParentMap().get(ensureVisible);
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

    private void forceMinDepthOpen(final TreeModel masterTreeModel, final Set<ExplorerData> forcedOpen, final ExplorerData parent, final int minDepth, final int depth) {
        final List<ExplorerData> children = masterTreeModel.getChildMap().get(parent);
        for (final ExplorerData child : children) {
            forcedOpen.add(child);
            if (minDepth > depth) {
                forceMinDepthOpen(masterTreeModel, forcedOpen, child, minDepth, depth + 1);
            }
        }
    }

    private boolean addDescendants(final ExplorerData parent, final TreeModel treeModelIn, final TreeModel treeModelOut, final ExplorerTreeFilter filter, final boolean ignoreNameFilter, final Set<ExplorerData> allOpenItemns, final int currentDepth) {
        int added = 0;

        final List<ExplorerData> children = treeModelIn.getChildMap().get(parent);
        if (children != null) {
            // Add all children if the name filter has changed or the parent item is open.
            final boolean addAllChildren = (filter.isNameFilterChange() && filter.getNameFilter() != null) || allOpenItemns.contains(parent);

            // We need to add add least one item to the tree to be able to determine if the parent is a leaf node.
            final Iterator<ExplorerData> iterator = children.iterator();
            while (iterator.hasNext() && (addAllChildren || added == 0)) {
                final ExplorerData child = iterator.next();

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

    private boolean checkSecurity(final ExplorerData explorerData, final Set<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.size() == 0) {
            return false;
        }

        final String type = explorerData.getType();
        final String uuid = explorerData.getDocRef().getUuid();
        for (final String permission : requiredPermissions) {
            if (!securityContext.hasDocumentPermission(type, uuid, permission)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkType(final ExplorerData explorerData, final Set<String> types) {
        return types == null || types.contains(explorerData.getType()) || FolderService.SYSTEM.equals(explorerData.getType());
    }

    private boolean checkTags(final ExplorerData explorerData, final Set<String> tags) {
        if (tags == null) {
            return true;
        } else if (explorerData.getTags() != null && explorerData.getTags().size() > 0 && tags.size() > 0) {
            for (final String tag : tags) {
                if (explorerData.getTags().contains(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean checkName(final ExplorerData explorerData, final String nameFilter) {
        return nameFilter == null || explorerData.getDisplayValue().toLowerCase().contains(nameFilter.toLowerCase());
    }

    private void addChildren(final ExplorerData parent, final TreeModel filteredModel, final Set<ExplorerData> openItems, final Set<ExplorerData> forcedOpenItems, final Set<ExplorerData> temporaryOpenItems, final int currentDepth, final FetchExplorerDataResult result) {
        parent.setDepth(currentDepth);

        // See if we need to force this item open.
        boolean force = false;
        if (forcedOpenItems.contains(parent)) {
            force = true;
            result.getOpenedItems().add(parent);
        } else if (temporaryOpenItems != null && temporaryOpenItems.contains(parent)) {
            force = true;
        }

        final List<ExplorerData> children = filteredModel.getChildMap().get(parent);
        if (children == null) {
            parent.setNodeState(HasNodeState.NodeState.LEAF);

        } else if (force || openItems.contains(parent)) {
            parent.setNodeState(HasNodeState.NodeState.OPEN);
            for (final ExplorerData child : children) {
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
        addTypes(FolderRootExplorerDataProvider.ROOT, masterTreeModel, visibleTypes, requiredPermissions);

        return getDocumentTypes(visibleTypes);
    }

    private boolean addTypes(final ExplorerData parent, final TreeModel treeModel, final Set<String> types, final Set<String> requiredPermissions) {
        boolean added = false;

        final List<ExplorerData> children = treeModel.getChildMap().get(parent);
        if (children != null) {
            for (final ExplorerData child : children) {
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
        final Collection<String> types = explorerTreeModel.getTypes();
        return getDocumentTypes(types);
    }

    private List<DocumentType> getDocumentTypes(final Collection<String> types) {
        final List<DocumentType> documentTypes = new ArrayList<>(types.size());
        for (final String type : types) {
            final ExplorerDataProvider provider = explorerTreeModel.getProvider(type);
            if (!(provider instanceof FolderRootExplorerDataProvider)) {
                final String displayType = provider.getDisplayType();
                final int priority = provider.getPriority();
                final String iconUrl = provider.getIconUrl();
                documentTypes.add(new DocumentType(priority, type, displayType, iconUrl));
            }
        }

        // Sort types by priority.
        documentTypes.sort((o1, o2) -> {
            final int comparison = Integer.compare(o1.getPriority(), o2.getPriority());
            if (comparison != 0) {
                return comparison;
            }

            return o1.getType().compareTo(o2.getType());
        });

        return documentTypes;
    }

    @Override
    public DocRef create(final String type, final String name, final DocRef folder, final PermissionInheritance permissionInheritance) {
        final ExplorerActionHandler documentStore = getDocumentStore(type);

        DocRef result;

        try {
            // Validate the entity name.
            validateName(documentStore, name);

            result = documentStore.create(folder.getUuid(), name);

            // Create the initial user permissions for this new document.
            switch (permissionInheritance) {
                case NONE:
                    addDocumentPermissions(null, result, true);
                    break;
                case COMBINED:
                    addDocumentPermissions(folder, result, true);
                    break;
                case INHERIT:
                    addDocumentPermissions(folder, result, true);
                    break;
            }

            explorerTreeModel.setRebuildRequired(true);

            explorerEventLog.create(type, name, folder, permissionInheritance, null);
        } catch (final RuntimeException e) {
            explorerEventLog.create(type, name, folder, permissionInheritance, e);
            throw e;
        }

        return result;
    }

    @Override
    public BulkActionResult copy(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler documentStore = getDocumentStore(docRef.getType());

            DocRef result;

            try {
                result = documentStore.copy(docRef.getUuid(), destinationFolderRef.getUuid());

                if (permissionInheritance != null) {
                    switch (permissionInheritance) {
                        case NONE:
                            addDocumentPermissions(docRef, result, true);
                            break;
                        case COMBINED:
                            addDocumentPermissions(docRef, result, true);
                            addDocumentPermissions(destinationFolderRef, result, true);
                            break;
                        case INHERIT:
                            addDocumentPermissions(destinationFolderRef, result, true);
                            break;
                    }
                }

                explorerEventLog.copy(docRef, destinationFolderRef, permissionInheritance, null);
                resultDocRefs.add(result);

            } catch (final Exception e) {
                explorerEventLog.copy(docRef, destinationFolderRef, permissionInheritance, e);
                resultMessage.append("Unable to copy '" + docRef.getName() + "' " + e.getMessage() + "\n");
            }
        }

        explorerTreeModel.setRebuildRequired(true);
        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    @Override
    public BulkActionResult move(final List<DocRef> docRefs, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler documentStore = getDocumentStore(docRef.getType());

            DocRef result;

            try {
                result = documentStore.move(docRef.getUuid(), destinationFolderRef.getUuid());

                if (permissionInheritance != null) {
                    switch (permissionInheritance) {
                        case NONE:
                            addDocumentPermissions(docRef, result, true);
                            break;
                        case COMBINED:
                            addDocumentPermissions(docRef, result, true);
                            addDocumentPermissions(destinationFolderRef, result, true);
                            break;
                        case INHERIT:
                            addDocumentPermissions(destinationFolderRef, result, true);
                            break;
                    }
                }

                explorerEventLog.move(docRef, destinationFolderRef, permissionInheritance, null);
                resultDocRefs.add(result);

            } catch (final Exception e) {
                explorerEventLog.move(docRef, destinationFolderRef, permissionInheritance, e);
                resultMessage.append("Unable to move '" + docRef.getName() + "' " + e.getMessage() + "\n");
            }
        }

        explorerTreeModel.setRebuildRequired(true);
        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    @Override
    public DocRef rename(final DocRef docRef, final String docName) {
        final ExplorerActionHandler documentStore = getDocumentStore(docRef.getType());

        DocRef result;

        try {
            // Validate the entity name.
            validateName(documentStore, docName);
            result = documentStore.rename(docRef.getUuid(), docName);

            explorerTreeModel.setRebuildRequired(true);

            explorerEventLog.rename(docRef, docName, null);
        } catch (final RuntimeException e) {
            explorerEventLog.rename(docRef, docName, e);
            throw e;
        }

        return result;
    }

    @Override
    public BulkActionResult delete(final List<DocRef> docRefs) {
        final List<DocRef> resultDocRefs = new ArrayList<>();
        final StringBuilder resultMessage = new StringBuilder();

        for (final DocRef docRef : docRefs) {
            final ExplorerActionHandler documentStore = getDocumentStore(docRef.getType());
            try {
                documentStore.delete(docRef.getUuid());
                explorerEventLog.delete(docRef, null);
                resultDocRefs.add(docRef);

            } catch (final Exception e) {
                explorerEventLog.delete(docRef, e);
                resultMessage.append("Unable to delete '" + docRef.getName() + "' " + e.getMessage() + "\n");
            }
        }

        explorerTreeModel.setRebuildRequired(true);
        return new BulkActionResult(resultDocRefs, resultMessage.toString());
    }

    private void validateName(final ExplorerActionHandler documentStore, final String name) {
        if (documentStore instanceof ProvidesNamePattern) {
            final ProvidesNamePattern providesNamePattern = (ProvidesNamePattern) documentStore;
            NameValidationUtil.validate(providesNamePattern, name);
        }
    }

    private ExplorerActionHandler getDocumentStore(final String type) {
        final Object bean = beanRegistry.getEntityService(type);
        if (bean == null) {
            throw new EntityServiceException("No document store can be found for type '" + type + "'");
        }
        if (!(bean instanceof ExplorerActionHandler)) {
            throw new EntityServiceException("Bean is not a document store");
        }
        return (ExplorerActionHandler) bean;
    }

    private void addDocumentPermissions(final DocRef source, final DocRef dest, final boolean owner) {
        String sourceType = null;
        String sourceUuid = null;
        String destType = null;
        String destUuid = null;

        if (source != null) {
            sourceType = source.getType();
            sourceUuid = source.getUuid();
        }

        if (dest != null) {
            destType = dest.getType();
            destUuid = dest.getUuid();
        }

        securityContext.addDocumentPermissions(sourceType, sourceUuid, destType, destUuid, owner);
    }
}
