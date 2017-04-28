/*
 * Copyright 2016 Crown Copyright
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
 */

package stroom.explorer.server;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.entity.shared.FolderService;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerDataResult;
import stroom.explorer.shared.FindExplorerDataCriteria;
import stroom.folder.server.FolderRootExplorerDataProvider;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.HasNodeState;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@Component
@Scope(StroomScope.THREAD)
class ExplorerServiceImpl implements ExplorerService {
    private final SecurityContext securityContext;
    private final ExplorerTreeModel explorerTreeModel;

    @Inject
    ExplorerServiceImpl(SecurityContext securityContext, ExplorerTreeModel explorerTreeModel) {
        this.securityContext = securityContext;
        this.explorerTreeModel = explorerTreeModel;
    }

    @Override
    public FetchExplorerDataResult getData(final FindExplorerDataCriteria criteria) {
        final FetchExplorerDataResult result = new FetchExplorerDataResult();

        // Get the master tree model.
        final TreeModel masterTreeModel = explorerTreeModel.getModel();

        // See if we need to open any more folders to see nodes we want to ensure are visible.
        final Set<ExplorerData> forcedOpen = getForcedOpenItems(masterTreeModel, criteria);

        final Set<ExplorerData> allOpen = new HashSet<>();
        allOpen.addAll(criteria.getOpenItems());
        allOpen.addAll(forcedOpen);

        final TreeModel filteredModel = new TreeModelImpl();
        addDescendants(FolderRootExplorerDataProvider.ROOT, masterTreeModel, filteredModel, criteria.getFilter(), allOpen, 0);

        // Add root node.
        result.getTreeStructure().add(null, FolderRootExplorerDataProvider.ROOT);
        addChildren(FolderRootExplorerDataProvider.ROOT, filteredModel, criteria.getOpenItems(), forcedOpen, 0, result);

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

    private boolean addDescendants(final ExplorerData parent, final TreeModel treeModelIn, final TreeModel treeModelOut, final ExplorerTreeFilter filter, final Set<ExplorerData> openItems, final int currentDepth) {
        boolean added = false;

        final List<ExplorerData> children = treeModelIn.getChildMap().get(parent);
        if (children != null) {
            // Once we have reached the minimum depth or have a parent that isn't open, we still need to try and add a
            // single item at this level so that we know that the parent has children.
            boolean addAllChildren;

            if (openItems.contains(parent)) {
                addAllChildren = true;
            } else {
                addAllChildren = false;
            }

            // We need to add add least one item to the tree to be able to determine if the parent is a leaf node.
            final Iterator<ExplorerData> iterator = children.iterator();
            while (iterator.hasNext() && (addAllChildren || !added)) {
                final ExplorerData child = iterator.next();

                // Recurse right down to find out if a descendant is being added and therefore if we need to include this as an ancestor.
                final boolean hasChildren = addDescendants(child, treeModelIn, treeModelOut, filter, openItems, currentDepth + 1);
                if (hasChildren) {
                    treeModelOut.add(parent, child);
                    added = true;

                } else if (checkType(child, filter.getIncludedTypes())
                        && checkTags(child, filter.getTags())
                        && checkName(child, filter.getNameFilter())
                        && checkSecurity(child, filter.getRequiredPermissions())) {
                    treeModelOut.add(parent, child);
                    added = true;
                }
            }
        }

        return added;
    }

    private boolean checkSecurity(final ExplorerData explorerData, final Set<String> requiredPermissions) {
        if (!(explorerData instanceof EntityData)) {
            return true;
        }

        if (requiredPermissions == null || requiredPermissions.size() == 0) {
            return false;
        }

        final String type = explorerData.getType();
        final String uuid = ((EntityData) explorerData).getDocRef().getUuid();
        for (final String permission : requiredPermissions) {
            if (!securityContext.hasDocumentPermission(type, uuid, permission)) {
                return false;
            }
        }

        return true;
    }

    private boolean checkType(final ExplorerData explorerData, final Set<String> types) {
        return types == null || types.contains(explorerData.getType()) || FolderService.ROOT.equals(explorerData.getType());
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

    private void addChildren(final ExplorerData parent, final TreeModel filteredModel, final Set<ExplorerData> openItems, final Set<ExplorerData> forcedOpen, final int currentDepth, final FetchExplorerDataResult result) {
        parent.setDepth(currentDepth);

        // See if we need to force this item open.
        boolean force = false;
        if (forcedOpen.contains(parent)) {
            force = true;
            result.getOpenedItems().add(parent);
        }

        final List<ExplorerData> children = filteredModel.getChildMap().get(parent);
        if (children == null) {
            parent.setNodeState(HasNodeState.NodeState.LEAF);

        } else if (force || openItems.contains(parent)) {
            parent.setNodeState(HasNodeState.NodeState.OPEN);
            for (final ExplorerData child : children) {
                result.getTreeStructure().add(parent, child);
                addChildren(child, filteredModel, openItems, forcedOpen, currentDepth + 1, result);
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
        Collections.sort(documentTypes, (o1, o2) -> {
            final int comparison = Integer.compare(o1.getPriority(), o2.getPriority());
            if (comparison != 0) {
                return comparison;
            }

            return o1.getType().compareTo(o2.getType());
        });

        return documentTypes;
    }
}
