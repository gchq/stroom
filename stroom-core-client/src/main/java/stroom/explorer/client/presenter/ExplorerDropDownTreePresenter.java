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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRef;
import stroom.widget.dropdowntree.client.presenter.DropDownTreePresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.util.client.SelectionType;

class ExplorerDropDownTreePresenter extends DropDownTreePresenter
        implements HasDataSelectionHandlers<ExplorerNode> {
    private final ExtendedExplorerTree explorerTree;
    private boolean allowFolderSelection;
    private ExplorerNode selectedExplorerNode;

    @Inject
    ExplorerDropDownTreePresenter(final EventBus eventBus, final DropDownTreeView view,
                                  final ClientDispatchAsync dispatcher) {
        super(eventBus, view);

        explorerTree = new ExtendedExplorerTree(this, dispatcher);
        setIncludeNullSelection(true);

        // Add views.
        view.setCellTree(explorerTree);
    }

    protected void setIncludeNullSelection(final boolean includeNullSelection) {
        explorerTree.getTreeModel().setIncludeNullSelection(includeNullSelection);
    }

    protected void setSelectedTreeItem(final ExplorerNode selectedItem,
                                       final SelectionType selectionType, final boolean initial) {
        // Is the selection type valid?
        if (isSelectionAllowed(selectedItem)) {
            // Drop down presenters need to know what the initial selection was so that they can update the name of their selected item properly.
            if (initial) {
                DataSelectionEvent.fire(this, selectedItem, false);
            } else if (selectionType.isDoubleSelect()) {
                DataSelectionEvent.fire(this, selectedItem, true);
                this.selectedExplorerNode = selectedItem;
                HidePopupEvent.fire(this, this, true, true);
            }
        }
    }

    private boolean isSelectionAllowed(final ExplorerNode selected) {
        if (selected == null) {
            return true;
        }
        if (allowFolderSelection) {
            return true;
        }

        return !DocumentTypes.isFolder(selected.getType());
    }

    @Override
    public void nameFilterChanged(final String text) {
        explorerTree.changeNameFilter(text);
    }

    @Override
    public void refresh() {
        explorerTree.setSelectedItem(selectedExplorerNode);
        explorerTree.getTreeModel().reset();
        explorerTree.getTreeModel().setEnsureVisible(selectedExplorerNode);
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public void focus() {
        explorerTree.setFocus(true);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerTree.getTreeModel().setIncludedTypes(includedTypes);
    }

    public void setTags(final String... tags) {
        explorerTree.getTreeModel().setTags(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        explorerTree.getTreeModel().setRequiredPermissions(requiredPermissions);
    }

    public DocRef getSelectedEntityReference() {
        final ExplorerNode explorerNode = getSelectedEntityData();
        if (explorerNode == null) {
            return null;
        }
        return explorerNode.getDocRef();
    }

    public void setSelectedEntityReference(final DocRef docRef) {
        setSelectedEntityData(ExplorerNode.create(docRef));
    }

    private ExplorerNode getSelectedEntityData() {
        return explorerTree.getSelectionModel().getSelected();
    }

    private void setSelectedEntityData(final ExplorerNode explorerNode) {
        this.selectedExplorerNode = explorerNode;
        refresh();
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        this.allowFolderSelection = allowFolderSelection;
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<ExplorerNode> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final ExplorerNode selected = getSelectedEntityData();
            if (isSelectionAllowed(selected)) {
                DataSelectionEvent.fire(this, selected, false);
                this.selectedExplorerNode = selected;
                super.onHideRequest(autoClose, ok);
            } else {
                AlertEvent.fireError(ExplorerDropDownTreePresenter.this,
                        "You must choose a valid item.", null);
            }
        } else {
            super.onHideRequest(autoClose, ok);
        }
    }

    private static class ExtendedExplorerTree extends ExplorerTree {
        private final ExplorerDropDownTreePresenter explorerDropDownTreePresenter;

        public ExtendedExplorerTree(final ExplorerDropDownTreePresenter explorerDropDownTreePresenter, final ClientDispatchAsync dispatcher) {
            super(dispatcher, false);
            this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;
        }

        private static ExplorerNode resolve(final ExplorerNode selection) {
            if (selection == ExplorerTreeModel.NULL_SELECTION) {
                return null;
            }

            return selection;
        }

        @Override
        protected void setInitialSelectedItem(final ExplorerNode selection) {
            super.setInitialSelectedItem(selection);
            explorerDropDownTreePresenter.setSelectedTreeItem(resolve(selection), new SelectionType(false, false), true);
        }

        @Override
        protected void doSelect(final ExplorerNode selection, final SelectionType selectionType) {
            super.doSelect(selection, selectionType);
            explorerDropDownTreePresenter.setSelectedTreeItem(resolve(selection), selectionType, false);
        }
    }
}
