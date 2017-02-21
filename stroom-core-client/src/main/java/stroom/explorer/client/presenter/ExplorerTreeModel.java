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

package stroom.explorer.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.SelectionModel;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExplorerTreeModel extends TreeNodeModel<ExplorerData> {
    private final NameFilterTimer timer = new NameFilterTimer();
    private final ExplorerTreeFilterBuilder explorerTreeFilterBuilder = new ExplorerTreeFilterBuilder();
    private final AbstractExporerTree exporerTree;
    private final Widget loading;
    private final ClientDispatchAsync dispatcher;

    private Integer minDepth = 1;
    private Set<ExplorerData> ensureVisible;

    private FindExplorerDataCriteria currentCriteria;
    private FetchExplorerDataResult currentResult;
    private boolean fetching;

    ExplorerTreeModel(final AbstractExporerTree exporerTree, final Widget loading, final ClientDispatchAsync dispatcher) {
        this.exporerTree = exporerTree;
        this.loading = loading;
        this.dispatcher = dispatcher;
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

    public void setEnsureVisible(final ExplorerData... ensureVisible) {
        this.ensureVisible = SetUtil.toSet(ensureVisible);
    }

    public void refresh() {
        final ExplorerTreeFilter explorerTreeFilter = explorerTreeFilterBuilder.build();
        final Set<ExplorerData> openItems = getOpenItems();
        if (explorerTreeFilter != null) {
            // Fetch a list of data items that belong to this parent.
            currentCriteria = new FindExplorerDataCriteria(openItems, explorerTreeFilter, minDepth, ensureVisible);

            if (!fetching) {
                fetching = true;
                loading.setVisible(true);
                Scheduler.get().scheduleDeferred(() -> {
                        final FindExplorerDataCriteria criteria = currentCriteria;
                        final FetchExplorerDataAction action = new FetchExplorerDataAction(criteria);
                        dispatcher.execute(action, new AsyncCallbackAdaptor<FetchExplorerDataResult>() {
                            @Override
                            public void onSuccess(final FetchExplorerDataResult result) {
                                fetching = false;

                                //                    final Set<ExplorerData> currentOpenItems = getOpenItems();
                                //                    final ExplorerTreeFilter currentFilter = explorerTreeFilterBuilder.build();

                                // Check if the filter settings have changed
                                // since we were asked to fetch.
                                if (criteria != currentCriteria) {//!explorerTreeFilter.equals(currentFilter) || !openItems.equals(currentOpenItems)) {
                                    // Some settings have changed so try again with the new settings.
                                    refresh();

                                } else {
                                    onDataChanged(result);

                                    // Build the row list from the tree structure.
                                    final List<ExplorerData> rows = new ArrayList<>();
                                    if (result != null && result.getTreeStructure() != null) {
                                        addToRows(result.getTreeStructure().getRoot(), result.getTreeStructure(), rows);
                                    }

                                    exporerTree.setData(rows);
                                    loading.setVisible(false);

                                    // If we have been asked to ensure something is visible then chances are we are
                                    // expected to select it as well.
                                    // Try and find the item we have asked to make visible first and if we can't find
                                    // that try and select one of the folders that has been forced open in an attempt to
                                    // make the requested item visible.
                                    if (criteria.getEnsureVisible() != null && criteria.getEnsureVisible().size() > 0) {
                                        ExplorerData nextSelection = criteria.getEnsureVisible().iterator().next();
                                        int index = rows.indexOf(nextSelection);
                                        if (index == -1) {
                                            nextSelection = null;

                                            if (result.getOpenedItems() != null) {
                                                for (int i = result.getOpenedItems().size() - 1; i >= 0 && nextSelection == null; i--) {
                                                    final ExplorerData item = result.getOpenedItems().get(i);
                                                    if (rows.contains(item)) {
                                                        nextSelection = item;
                                                    }
                                                }
                                            }
                                        } else {
                                            // Reassign the selection because matches are only by UUID and this will ensure that we get the latest version with any new name it might have.
                                            nextSelection = rows.get(index);
                                        }

                                        if (nextSelection != null) {
                                            exporerTree.setInitialSelectedItem(nextSelection);
                                        }
                                    }

                                    // If we have asked the server to ensure one or more nodes are visible then some
                                    // folders might have been opened to make this happen. The server will tell us which
                                    // folders these were so we can add them to the set of open folders to ensure they
                                    // aren't immediately closed on the next refresh.
                                    if (result.getOpenedItems() != null) {
                                        for (final ExplorerData openedItem : result.getOpenedItems()) {
                                            addOpenItem(openedItem);
                                        }
                                    }

                                    // We do not want to ensure specific items are visible on every refresh as we might
                                    // want to close some folders. To do this we need to forget which nodes we wanted to
                                    // ensure visibility of. We can forget them now as we have a result that should have
                                    // opened required folders to make them visible.
                                    ensureVisible = null;
                                }
                            }
                        });
                });
            }
        }
    }

    protected void onDataChanged(final FetchExplorerDataResult result) {
        currentResult = result;
    }

    private void addToRows(final ExplorerData parent, final TreeStructure treeStructure, final List<ExplorerData> rows) {
        rows.add(parent);
        final List<ExplorerData> children = treeStructure.getChildren(parent);
        if (children != null) {
            for (final ExplorerData child : children) {
                addToRows(child, treeStructure, rows);
            }
        }
    }

//    public void refresh(final Set<ExplorerData> openItems, final Integer depth) {
//        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
//            @Override
//            public void execute() {
//                fetchData();
//            }
//        });
//    }
//
//    public void reset() {
//        reset(null, 1);
//    }

    public void clear() {
        exporerTree.setData(new ArrayList<>());
    }

    public void reset() {
        clearOpenItems();
        minDepth = 1;
        ensureVisible = null;
//        explorerTreeFilterBuilder = new ExplorerTreeFilterBuilder();
    }

//    public void reset() {
//        clearOpenItems();
//        minDepth = 1;
//        refresh();
//    }
//
//
//    public FetchExplorerDataResult getCurrentResult() {
//        return currentResult;
//    }

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