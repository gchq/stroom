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

package stroom.explorer.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNode.NodeState;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FindExplorerNodeCriteria;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExplorerTreeModel {
    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final ExplorerNode NULL_SELECTION = ExplorerNode.create(new DocRef.Builder().uuid("").name("None").type("").build());

    private final OpenItems<String> openItems = new OpenItems<>();
    private final NameFilterTimer timer = new NameFilterTimer();
    private final ExplorerTreeFilterBuilder explorerTreeFilterBuilder = new ExplorerTreeFilterBuilder();
    private final AbstractExplorerTree explorerTree;
    private final Widget loading;
    private final RestFactory restFactory;

    private Integer minDepth = 1;
    private Set<String> ensureVisible;

    private FindExplorerNodeCriteria currentCriteria;
    private boolean fetching;

    private boolean includeNullSelection;

    private List<ExplorerNode> currentRootNodes;

    ExplorerTreeModel(final AbstractExplorerTree explorerTree,
                      final Widget loading,
                      final RestFactory restFactory) {
        this.explorerTree = explorerTree;
        this.loading = loading;
        this.restFactory = restFactory;
    }

    public void changeNameFilter(final String name) {
        timer.setName(name);
        timer.cancel();
        timer.schedule(300);
    }

    public void setIncludedTypeSet(final Set<String> types) {
        explorerTreeFilterBuilder.setIncludedTypeSet(types);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerTreeFilterBuilder.setIncludedTypes(includedTypes);
    }

    public void setTags(final String... tags) {
        explorerTreeFilterBuilder.setTags(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        explorerTreeFilterBuilder.setRequiredPermissions(requiredPermissions);
    }

    public void setEnsureVisible(final ExplorerNode... ensureVisible) {
        this.ensureVisible = null;
        if (ensureVisible != null) {
            this.ensureVisible = new HashSet<>();
            for (ExplorerNode node : ensureVisible) {
                if (node != null && node.getUuid() != null) {
                    this.ensureVisible.add(node.getUuid());
                }
            }
        }
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
            currentCriteria = new FindExplorerNodeCriteria(openItems.getOpenItems(),
                    openItems.getTemporaryOpenItems(),
                    explorerTreeFilter,
                    minDepth,
                    ensureVisible);

            if (!fetching) {
                fetching = true;
                loading.setVisible(true);
                Scheduler.get().scheduleDeferred(() -> {
                    final FindExplorerNodeCriteria criteria = currentCriteria;
                    final Rest<FetchExplorerNodeResult> rest = restFactory.create();
                    rest.onSuccess(result -> {
                        fetching = false;

                        //                    final Set<ExplorerNode> currentOpenItems = getOpenItems();
                        //                    final ExplorerTreeFilter currentFilter = explorerTreeFilterBuilder.build();

                        // Check if the filter settings have changed
                        // since we were asked to fetch.
                        if (criteria != currentCriteria) {//!explorerTreeFilter.equals(currentFilter) || !openItems.equals(currentOpenItems)) {
                            // Some settings have changed so try again with the new settings.
                            refresh();

                        } else {
                            onDataChanged(result);

                            // If we have asked the server to ensure one or more nodes are visible then some
                            // folders might have been opened to make this happen. The server will tell us which
                            // folders these were so we can add them to the set of open folders to ensure they
                            // aren't immediately closed on the next refresh.
                            if (result.getOpenedItems() != null) {
                                for (final String openedItem : result.getOpenedItems()) {
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
                            if (criteria.getEnsureVisible() != null && criteria.getEnsureVisible().size() > 0) {
                                String uuid = criteria.getEnsureVisible().iterator().next();
                                ExplorerNode nextSelection = null;
                                if (uuid != null) {
                                    nextSelection = ExplorerNode.create(new DocRef(null, uuid));
                                }

                                // If we are allowing null selection then select the NULL node if we have been
                                // asked to ensure NULL is selected after refresh.
                                if (nextSelection == null && includeNullSelection) {
                                    nextSelection = NULL_SELECTION;
                                }
                                int index = rows.indexOf(nextSelection);
                                if (index == -1) {
                                    nextSelection = null;

                                    if (result.getOpenedItems() != null) {
                                        for (int i = result.getOpenedItems().size() - 1; i >= 0 && nextSelection == null; i--) {
                                            final String item = result.getOpenedItems().get(i);
                                            final ExplorerNode explorerNode = ExplorerNode.create(new DocRef(null, item));
                                            if (rows.contains(explorerNode)) {
                                                nextSelection = explorerNode;
                                            }
                                        }
                                    }
                                } else {
                                    // Reassign the selection because matches are only by UUID and this will ensure that we get the latest version with any new name it might have.
                                    nextSelection = rows.get(index);
                                }

                                if (nextSelection != null) {
                                    explorerTree.setInitialSelectedItem(nextSelection);
                                }
                            }

                            // We do not want the root to always be forced open.
                            minDepth = 0;

                            // We do not want to ensure specific items are visible on every refresh as we might
                            // want to close some folders. To do this we need to forget which nodes we wanted to
                            // ensure visibility of. We can forget them now as we have a result that should have
                            // opened required folders to make them visible.
                            ensureVisible = null;

                            // We aren't loading any more.
                            loading.setVisible(false);
                        }
                    })
                            .call(EXPLORER_RESOURCE)
                            .fetch(criteria);
                });
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
            if (rows.size() == 0 || rows.get(0) != NULL_SELECTION) {
                rows.add(0, NULL_SELECTION);
            }
        }

        explorerTree.setData(rows);

        return rows;
    }

    private void addToRows(final List<ExplorerNode> in, final List<ExplorerNode> rows, final Set<String> openItems) {
        for (ExplorerNode parent : in) {
            rows.add(parent);
            if (openItems.contains(parent.getUuid())) {
                final List<ExplorerNode> children = parent.getChildren();
                if (children != null) {
                    addToRows(children, rows, openItems);
                }

                if (!NodeState.LEAF.equals(parent.getNodeState())) {
                    parent.setNodeState(NodeState.OPEN);
                }
            } else {
                if (!NodeState.LEAF.equals(parent.getNodeState())) {
                    parent.setNodeState(NodeState.CLOSED);
                }
            }
        }
    }

    protected void onDataChanged(final FetchExplorerNodeResult result) {
    }

    public void reset() {
        explorerTree.setData(new ArrayList<>());
        openItems.clear();
        minDepth = 1;
        ensureVisible = null;
    }

    public boolean isIncludeNullSelection() {
        return includeNullSelection;
    }

    public void setIncludeNullSelection(final boolean includeNullSelection) {
        this.includeNullSelection = includeNullSelection;
    }

    public void setItemOpen(final ExplorerNode item, final boolean open) {
        if (item != null) {
            if (open != openItems.isOpen(item.getUuid())) {
                refresh(openItems.toggleOpenState(item.getUuid()));
            }
        }
    }

    public void toggleOpenState(final ExplorerNode item) {
        if (item != null) {
            refresh(openItems.toggleOpenState(item.getUuid()));
        }
    }

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