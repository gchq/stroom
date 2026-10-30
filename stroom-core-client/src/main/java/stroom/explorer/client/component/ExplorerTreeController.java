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

package stroom.explorer.client.component;

import stroom.dispatch.client.RestFactory;
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
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// Fills an [ExplorerTree] with the explorer's nodes, fetched from the server, and keeps which are
/// open: the tree's filters (name, types, tags, flags, permissions), what it is to show and choose
/// once fetched, and whether it shows alerts. Its owner configures it; the tree asks it to open,
/// close and fetch again.
///
/// ```java
/// final ExplorerTree tree = new ExplorerTree();
/// treeController = make(explorerTreeControllerProvider).attach(tree);
/// treeController.setIncludedTypes(...);
/// treeController.refresh();
/// ```
@SuppressWarnings("SequencedCollectionMethodCanBeUsed")
public class ExplorerTreeController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final OpenItems<ExplorerNodeKey> openItems = new OpenItems<>();
    private final NameFilterTimer nameFilterTimer = new NameFilterTimer();
    private final ExplorerTreeFilter.Builder explorerTreeFilterBuilder = ExplorerTreeFilter.builder();
    private final RestFactory restFactory;
    private ExplorerTree explorerTree;
    private TaskMonitorFactory taskMonitorFactory;

    private Integer minDepth = 1;
    private Set<ExplorerNodeKey> ensureVisible;
    private ExplorerNode forceSelection;
    private boolean showAlerts = false;

    private FetchExplorerNodesRequest currentCriteria;
    private boolean fetching;

    private boolean includeNullSelection;

    private List<ExplorerNode> currentRootNodes;
    private boolean selectParentIfNotFound = false;
    private Consumer<String> onFilterError;

    /// Made by GIN; [#attach(ExplorerTree)] gives it its tree.
    ///
    /// @param restFactory Fetches the nodes.
    @Inject
    public ExplorerTreeController(final RestFactory restFactory) {
        this.restFactory = restFactory;
        onDispose(nameFilterTimer::cancel);
    }

    /// Attaches this to the tree it fills, which tells it what is opened and closed.
    ///
    /// @param explorerTree The tree.
    /// @return This, for its owner to keep.
    public ExplorerTreeController attach(final ExplorerTree explorerTree) {
        this.explorerTree = Objects.requireNonNull(explorerTree, "explorerTree");
        explorerTree.setOnOpenChange(this::setItemOpen);
        explorerTree.setOnToggleOpen(this::toggleOpenState);
        explorerTree.setOnReload(this::refresh);
        explorerTree.setIncludeNullSelection(includeNullSelection);
        explorerTree.setShowAlerts(showAlerts);
        return this;
    }

    /// Says what shows the progress of fetching the nodes, in place of what its owners provide
    /// ([stroom.widget.component.client.Contexts#TASK_MONITOR]), e.g. the explorer's own spinner.
    ///
    /// @param taskMonitorFactory What shows it, or null for what its owners provide.
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    /// Sets what is told why the server rejected the name filter, or null when it applied it, each
    /// time the nodes are fetched, e.g. the quick filter driving the tree.
    ///
    /// @param onFilterError Given the reason, or null.
    public void setOnFilterError(final Consumer<String> onFilterError) {
        this.onFilterError = onFilterError;
    }

    /// Shows (or not) the alert icons of items with problems, which the next fetch asks for.
    ///
    /// @param showAlerts Whether to show them.
    public void setShowAlerts(final boolean showAlerts) {
        this.showAlerts = showAlerts;
        if (explorerTree != null) {
            explorerTree.setShowAlerts(showAlerts);
        }
    }

    /// Filters the tree by name as the user types, fetching once they pause.
    ///
    /// @param name The filter.
    public void changeNameFilter(final String name) {
        nameFilterTimer.setName(name);
        nameFilterTimer.cancel();
        nameFilterTimer.schedule(400);
    }

    /// Sets the name filter the tree starts with, for the next fetch.
    ///
    /// @param name The filter.
    public void setInitialNameFilter(final String name) {
        explorerTreeFilterBuilder.setNameFilter(name, true);
    }

    /// Shows only documents of some types (and the folders holding them), from the next fetch.
    ///
    /// @param types The types, or null for all.
    public void setIncludedTypeSet(final Set<String> types) {
        explorerTreeFilterBuilder.includedTypeSet(types);
    }

    /// Shows only documents of some types (and the folders holding them), from the next fetch.
    ///
    /// @param types The types.
    public void setIncludedTypes(final String... types) {
        explorerTreeFilterBuilder.includedTypes(types);
    }

    /// Shows only the roots of some types, from the next fetch.
    ///
    /// @param types The types.
    public void setIncludedRootTypes(final String... types) {
        explorerTreeFilterBuilder.includedRootTypes(types);
    }

    /// Shows only documents with all of some tags, from the next fetch.
    ///
    /// @param tags The tags.
    public void setTags(final String... tags) {
        explorerTreeFilterBuilder.tags(tags);
    }

    /// Shows only nodes with some flags, from the next fetch.
    ///
    /// @param nodeFlags The flags.
    public void setNodeFlags(final NodeFlag... nodeFlags) {
        explorerTreeFilterBuilder.nodeFlags(NullSafe.asSet(nodeFlags));
    }

    /// Shows only documents the user has some permissions on, from the next fetch.
    ///
    /// @param requiredPermissions The permissions.
    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        explorerTreeFilterBuilder.requiredPermissions(requiredPermissions);
    }

    /// Chooses an item once the next fetch has shown it.
    ///
    /// @param forceSelection The item.
    public void setForceSelection(final ExplorerNode forceSelection) {
        this.forceSelection = forceSelection;
    }

    /// Opens the folders holding some items in the next fetch, and chooses the first.
    ///
    /// @param ensureVisible The items, or null for none.
    public void setEnsureVisible(final Set<ExplorerNode> ensureVisible) {
        this.ensureVisible = null;
        if (NullSafe.hasItems(ensureVisible)) {
            this.ensureVisible = new HashSet<>();
            for (final ExplorerNode node : ensureVisible) {
                if (node != null && node.getUniqueKey() != null) {
                    this.ensureVisible.add(node.getUniqueKey());
                }
            }
        }
    }

    /// Opens the folders holding some items in the next fetch, and chooses the first.
    ///
    /// @param ensureVisible The items.
    public void setEnsureVisible(final ExplorerNode... ensureVisible) {
        this.ensureVisible = null;
        if (NullSafe.hasItems(ensureVisible)) {
            this.ensureVisible = new HashSet<>();
            for (final ExplorerNode node : ensureVisible) {
                if (node != null && node.getUniqueKey() != null) {
                    this.ensureVisible.add(node.getUniqueKey());
                }
            }
        }
    }

    /// Sets whether an item to show that isn't found (e.g. a document, where only folders are
    /// shown) has its folder chosen instead, as when copying or moving it.
    ///
    /// @param selectParentIfNotFound Whether to choose its folder.
    public void setSelectParentIfNotFound(final boolean selectParentIfNotFound) {
        this.selectParentIfNotFound = selectParentIfNotFound;
    }

    /// Fetches the nodes again and shows them.
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
                    restFactory
                            .create(EXPLORER_RESOURCE)
                            .method(res -> res.fetchExplorerNodes(criteria))
                            .onSuccess(result -> {
                                handleFetchResult(criteria, result);
                            })
                            .owner(this)
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                });
            }
        }
    }

    private void handleFetchResult(final FetchExplorerNodesRequest criteria,
                                   final FetchExplorerNodeResult result) {
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
            // update() replaces all rows, which resets the tree's scroll position to the top. If the
            // currently selected item is about to disappear (e.g. it was just deleted) there will be
            // nothing to scroll back to, so the view would be left jumped to the top. Remember the
            // selected item and the row above it now, so we can keep that row in view below.
            final ExplorerNode previousSelection = NullSafe.get(
                    explorerTree.getSelectionModel(), MultiSelectionModelImpl::getSelected);
            final ExplorerNode nodeAboveSelection = explorerTree.getNodeAbove(previousSelection);
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
                    nextSelection = ExplorerTree.NULL_SELECTION;
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
                explorerTree.setInitialSelectedItem(nextSelection);
            } else if (previousSelection != null && !rows.contains(previousSelection)) {
                // The previously selected item has gone (e.g. it was just deleted). Select the row that
                // was above it and reveal that, so the view stays where it was rather than jumping to the
                // top. This reuses the same reveal path as selection follow, which scrolls reliably.
                final int aboveIndex = rows.indexOf(nodeAboveSelection);
                if (aboveIndex >= 0) {
                    explorerTree.setInitialSelectedItem(rows.get(aboveIndex));
                    // Also move the table's keyboard row here, otherwise the table re-focuses its
                    // (reset) keyboard row after the data change and scrolls the view back to the top.
                    explorerTree.setKeyboardSelectedRow(aboveIndex);
                }
            }

            // We do not want the root to always be forced open.
            minDepth = 0;

            // We do not want to ensure specific items are visible on every refresh as we might
            // want to close some folders. To do this we need to forget which nodes we wanted to
            // ensure visibility of. We can forget them now as we have a result that should have
            // opened required folders to make them visible.
            if (nextSelection != null && !ExplorerTree.NULL_SELECTION.equals(nextSelection)) {
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
            if (rows.isEmpty() || rows.get(0) != ExplorerTree.NULL_SELECTION) {
                // If there is any quick filter then ExplorerTree.NULL_SELECTION is a non-match
                final boolean isFilterMatch = NullSafe.isBlankString(NullSafe.get(
                        currentCriteria,
                        FetchExplorerNodesRequest::getFilter,
                        ExplorerTreeFilter::getNameFilter));

                rows.add(0, ExplorerTree.NULL_SELECTION.copy()
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

    private void onDataChanged(final FetchExplorerNodeResult result) {
        explorerTree.setRootNodes(result.getRootNodes());
        // A rejected filter comes back as an empty tree like any other, so surface the reason on
        // whichever quick filter is driving this tree. Every explorer surface - the explorer, document
        // tree, the explorer popup, export config - shares this one callback. See
        // FetchExplorerNodeResult.filterError.
        if (onFilterError != null) {
            onFilterError.accept(NullSafe.get(result.getFilterError(), TokenError::getText));
        }
    }

    /// Forgets the open folders, what to show and the name filter, and empties the tree.
    public void reset() {
        reset(null);
    }

    /// Forgets the open folders and what to show, and empties the tree, with a name filter.
    ///
    /// @param initialNameFilter The name filter, or null for none.
    public void reset(final String initialNameFilter) {
        explorerTreeFilterBuilder.setNameFilter(initialNameFilter);
        explorerTree.setData(new ArrayList<>());
        openItems.clear();
        minDepth = 1;
        ensureVisible = null;
    }

    /// Opens every folder, and fetches them.
    public void expandAll() {
        openItems.clear();
        minDepth = 1000;
        ensureVisible = null;
        refresh();
    }

    /// Closes every folder but the roots, and fetches again.
    public void collapseAll() {
        openItems.clear();
        minDepth = 1;
        ensureVisible = null;
        refresh();
    }

    /// Sets whether the tree's first item is [ExplorerTree#NULL_SELECTION], which a picker
    /// chooses for nothing.
    ///
    /// @param includeNullSelection Whether it is.
    public void setIncludeNullSelection(final boolean includeNullSelection) {
        this.includeNullSelection = includeNullSelection;
        if (explorerTree != null) {
            explorerTree.setIncludeNullSelection(includeNullSelection);
        }
    }

    /// Opens or closes a folder, fetching it if it hasn't been.
    ///
    /// @param item The folder.
    /// @param open Whether it is open.
    public void setItemOpen(final ExplorerNode item, final boolean open) {
        if (item != null) {
            if (open != openItems.isOpen(item.getUniqueKey())) {
                refresh(openItems.toggleOpenState(item.getUniqueKey()));
            }
        }
    }

    /// Opens a closed folder, or closes an open one.
    ///
    /// @param item The folder.
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
