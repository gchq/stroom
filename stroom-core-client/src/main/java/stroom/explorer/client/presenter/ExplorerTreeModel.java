/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.NodeFlag.NodeFlagGroups;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExplorerTreeModel {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final ExplorerNode NULL_SELECTION = ExplorerNode.builder()
            .docRef(DocRef.builder()
                    .uuid("")
                    .name("None")
                    .type("")
                    .build())
            .build();

    private final OpenItems<ExplorerNodeKey> openItems = new OpenItems<>();
    private final NameFilterTimer nameFilterTimer = new NameFilterTimer();
    private final ExplorerTreeFilter.Builder explorerTreeFilterBuilder = ExplorerTreeFilter.builder();
    private final AbstractExplorerTree explorerTree;
    private final RestFactory restFactory;
    private final TaskMonitorFactory taskMonitorFactory;

    private Integer minDepth = 1;
    private Set<ExplorerNodeKey> ensureVisible;
    private ExplorerNode forceSelection;
    private boolean showAlerts = false;

    private FetchExplorerNodesRequest currentCriteria;
    private boolean fetching;

    private boolean includeNullSelection;

    private List<ExplorerNode> currentRootNodes;
    private boolean selectParentIfNotFound = false;

    ExplorerTreeModel(final AbstractExplorerTree explorerTree,
                      final RestFactory restFactory,
                      final TaskMonitorFactory taskMonitorFactory) {
        this.explorerTree = explorerTree;
        this.restFactory = restFactory;
        this.taskMonitorFactory = taskMonitorFactory;
    }

    /**
     * For use when the filter input is being typed by a user.
     */
    public void changeNameFilter(final String name) {
        nameFilterTimer.setName(name);
        nameFilterTimer.cancel();
        nameFilterTimer.schedule(400);
    }

    /**
     * For use when setting an initial value for the filter.
     */
    public void setInitialNameFilter(final String name) {
        explorerTreeFilterBuilder.setNameFilter(name, true);
    }

    public void setIncludedTypeSet(final Set<String> types) {
        explorerTreeFilterBuilder.includedTypeSet(types);
    }

    public void setIncludedTypes(final String... types) {
        explorerTreeFilterBuilder.includedTypes(types);
    }

    public void setIncludedRootTypes(final String... types) {
        explorerTreeFilterBuilder.includedRootTypes(types);
    }

    public void setTags(final String... tags) {
        explorerTreeFilterBuilder.tags(tags);
    }

    public void setNodeFlags(final NodeFlag... nodeFlags) {
        explorerTreeFilterBuilder.nodeFlags(NullSafe.asSet(nodeFlags));
    }

    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        explorerTreeFilterBuilder.requiredPermissions(requiredPermissions);
    }

    public void setForceSelection(final ExplorerNode forceSelection) {
        this.forceSelection = forceSelection;
    }

    public void setEnsureVisible(final Set<ExplorerNode> ensureVisible) {
        this.ensureVisible = null;
        if (NullSafe.hasItems(ensureVisible)) {
            this.ensureVisible = new HashSet<>();
            for (final ExplorerNode node : ensureVisible) {
                if (node != null && node.getUniqueKey() != null) {
                    this.ensureVisible.add(node.getUniqueKey());
//                    GWT.log("setEnsureVisible: " + node.getName());
                }
            }
        }
    }

    public void setEnsureVisible(final ExplorerNode... ensureVisible) {
        this.ensureVisible = null;
        if (NullSafe.hasItems(ensureVisible)) {
            this.ensureVisible = new HashSet<>();
            for (final ExplorerNode node : ensureVisible) {
                if (node != null && node.getUniqueKey() != null) {
                    this.ensureVisible.add(node.getUniqueKey());
//                    GWT.log("setEnsureVisible: " + node.getName());
                }
            }
        }
    }

    public void setShowAlerts(final boolean showAlerts) {
        this.showAlerts = showAlerts;
    }

    public void setSelectParentIfNotFound(final boolean selectParentIfNotFound) {
        this.selectParentIfNotFound = selectParentIfNotFound;
    }

    public void refresh() {
        // Fetch data from the server to update the tree.
        fetchData();
    }

    private void refresh(final boolean fetch) {
        if (fetch) {
            // Fetch data from the server to update the tree.
            fetchData();
        } else {
            // Just quickly update the display using previously cached data.
            update();
        }
    }

    private void fetchData() {
        final ExplorerTreeFilter explorerTreeFilter = explorerTreeFilterBuilder.build();
        if (explorerTreeFilter != null) {
            // Fetch a list of data items that belong to this parent.
            currentCriteria = new FetchExplorerNodesRequest(
                    openItems.getOpenItems(),
                    openItems.getTemporaryOpenItems(),
                    explorerTreeFilter,
                    minDepth,
                    ensureVisible,
                    showAlerts);

            if (!fetching) {
                fetching = true;
                Scheduler.get().scheduleDeferred(() -> {
                    final FetchExplorerNodesRequest criteria = currentCriteria;
//                    GWT.log("fetchData - filter: " + explorerTreeFilter.getNameFilter()
//                            + " openItems: " + openItems.getOpenItems().size()
//                            + " minDepth: " + minDepth
//                            + " ensureVisible: " + ensureVisible);
                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.fetchExplorerNodes(criteria))
                            .onSuccess(result -> {
                                handleFetchResult(criteria, result);
                            })
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                });
            }
        }
    }

    private void handleFetchResult(final FetchExplorerNodesRequest criteria,
                                   final FetchExplorerNodeResult result) {
//        GWT.log("handleFetchResult - filter: " + result.getQualifiedFilterInput()
//                + " openItems: " + NullSafe.size(result.getOpenedItems())
//                + " tempOpenedItems: " + NullSafe.size(result.getTemporaryOpenedItems()));

        fetching = false;
        // Check if the filter settings have changed
        // since we were asked to fetch.
        if (criteria != currentCriteria) {
            // Some settings have changed so try again with the new settings.
            refresh();
        } else {
            onDataChanged(result);
            // If we have asked the server to ensure one or more nodes are visible then some
            // folders might have been opened to make this happen. The server will tell us which
            // folders these were, so we can add them to the set of open folders to ensure they
            // aren't immediately closed on the next refresh.
            if (result.getOpenedItems() != null) {
                for (final ExplorerNodeKey openedItem : result.getOpenedItems()) {
                    openItems.open(openedItem);
                }
            }
            if (result.getTemporaryOpenedItems() != null) {
                openItems.setTemporaryOpenItems(result.getTemporaryOpenedItems());
            }

            // Remember the new tree structure.
            this.currentRootNodes = result.getRootNodes();
            // Update the tree.
            final List<ExplorerNode> rows = update();

            // If we have been asked to ensure something is visible then chances are we are
            // expected to select it as well.
            // Try and find the item we have asked to make visible first and if we can't find
            // that try and select one of the folders that has been forced open in an attempt to
            // make the requested item visible.
            ExplorerNode nextSelection = forceSelection;
            if (nextSelection == null && NullSafe.hasItems(criteria.getEnsureVisible())) {
                final ExplorerNodeKey ensureVisibleUniqueKey = criteria.getEnsureVisible().iterator().next();
                nextSelection = NullSafe.get(ensureVisibleUniqueKey, ExplorerNode::fromExplorerNodeKey);

                // If we are allowing null selection then select the NULL node if we have been
                // asked to ensure NULL is selected after refresh.
                if (nextSelection == null && includeNullSelection) {
                    nextSelection = NULL_SELECTION;
                }
                final int index = rows.indexOf(nextSelection);
                if (index == -1) {
                    nextSelection = null;

                    // In some use cases, like copy/move, the selected item will not be found as it is not
                    // a folder, so we want to select the parent of the 'selected item' so we can copy/move
                    // into that folder as a default.
                    if (selectParentIfNotFound) {
                        if (result.getOpenedItems() != null) {
                            final int openedItemsCnt = result.getOpenedItems().size();
                            for (int i = openedItemsCnt - 1; i >= 0 && nextSelection == null; i--) {
                                final ExplorerNodeKey item = result.getOpenedItems().get(i);
                                final ExplorerNode explorerNode = ExplorerNode.builder()
                                        .type(item.getType())
                                        .uuid(item.getUuid())
                                        .rootNodeUuid(item.getRootNodeUuid())
                                        .build();
                                if (rows.contains(explorerNode)) {
                                    nextSelection = explorerNode;
                                }
                            }
                        }
                    }
                } else {
                    // Reassign the selection because matches are only by UUID and this will ensure
                    // that we get the latest version with any new name it might have.
                    nextSelection = rows.get(index);
                }
            }
            if (nextSelection != null) {
//                GWT.log("nextSelection: " + nextSelection);
                explorerTree.setInitialSelectedItem(nextSelection);
            }

            // We do not want the root to always be forced open.
            minDepth = 0;

            // We do not want to ensure specific items are visible on every refresh as we might
            // want to close some folders. To do this we need to forget which nodes we wanted to
            // ensure visibility of. We can forget them now as we have a result that should have
            // opened required folders to make them visible.
            if (nextSelection != null && !NULL_SELECTION.equals(nextSelection)) {
//                GWT.log("Clearing ensureVisible & forceSelection");
                ensureVisible = null;
                forceSelection = null;
            }
        }
    }

    private List<ExplorerNode> update() {
        // Build the row list from the tree structure.
        final List<ExplorerNode> rows = new ArrayList<>();
        if (currentRootNodes != null) {
            addToRows(currentRootNodes, rows, openItems.getAllOpenItems());
        }

        // If we are allowing null selection then insert a node at the root to make it
        // possible.
        if (includeNullSelection) {
            if (rows.isEmpty() || rows.get(0) != NULL_SELECTION) {
                // If there is any quick filter then NULL_SELECTION is a non-match
                final boolean isFilterMatch = NullSafe.isBlankString(NullSafe.get(
                        currentCriteria,
                        FetchExplorerNodesRequest::getFilter,
                        ExplorerTreeFilter::getNameFilter));

                rows.add(0, NULL_SELECTION.copy()
                        .setGroupedNodeFlag(NodeFlagGroups.FILTER_MATCH_PAIR, isFilterMatch)
                        .build());
            }
        }

        explorerTree.setData(rows);

        return rows;
    }

    private void addToRows(final List<ExplorerNode> in,
                           final List<ExplorerNode> rows,
                           final Set<ExplorerNodeKey> openItems) {
        for (final ExplorerNode parent : in) {
            if (openItems.contains(parent.getUniqueKey())) {
                final ExplorerNode.Builder builder = parent.copy();
                if (!parent.hasNodeFlag(NodeFlag.LEAF)) {
                    builder.setGroupedNodeFlag(NodeFlagGroups.EXPANDER_GROUP, NodeFlag.OPEN);
                }
                rows.add(builder.build());

                final List<ExplorerNode> children = parent.getChildren();
                if (children != null) {
                    addToRows(children, rows, openItems);
                }

            } else {
                final ExplorerNode.Builder builder = parent.copy();
                if (!parent.hasNodeFlag(NodeFlag.LEAF)) {
                    builder.setGroupedNodeFlag(NodeFlagGroups.EXPANDER_GROUP, NodeFlag.CLOSED);
                }
                rows.add(builder.build());
            }
        }
    }

    protected void onDataChanged(final FetchExplorerNodeResult result) {
    }

    public void reset() {
        reset(null);
    }

    public void reset(final String initialNameFilter) {
//        GWT.log("reset with initialQuickFilter: " + initialNameFilter);
        explorerTreeFilterBuilder.setNameFilter(initialNameFilter);
        explorerTree.setData(new ArrayList<>());
        openItems.clear();
        minDepth = 1;
        ensureVisible = null;
    }

    public void expandAll() {
//        explorerTreeFilterBuilder.setNameFilter(null);
        openItems.clear();
        minDepth = 1000;
        ensureVisible = null;
        refresh();
    }

    public void collapseAll() {
//        explorerTreeFilterBuilder.setNameFilter(null);
        openItems.clear();
        minDepth = 1;
        ensureVisible = null;
        refresh();
    }

    public boolean isIncludeNullSelection() {
        return includeNullSelection;
    }

    public void setIncludeNullSelection(final boolean includeNullSelection) {
        this.includeNullSelection = includeNullSelection;
    }

    public void setItemOpen(final ExplorerNode item, final boolean open) {
        if (item != null) {
            if (open != openItems.isOpen(item.getUniqueKey())) {
                refresh(openItems.toggleOpenState(item.getUniqueKey()));
            }
        }
    }

    public void toggleOpenState(final ExplorerNode item) {
        if (item != null) {
            refresh(openItems.toggleOpenState(item.getUniqueKey()));
        }
    }

    // --------------------------------------------------------------------------------


    private class NameFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            if (explorerTreeFilterBuilder.setNameFilter(name)) {
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
