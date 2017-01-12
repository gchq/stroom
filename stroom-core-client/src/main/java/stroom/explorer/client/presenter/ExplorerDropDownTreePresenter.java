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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.Folder;
import stroom.explorer.client.event.SelectionType;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.widget.dropdowntree.client.presenter.DropDownTreePresenter;
import stroom.widget.popup.client.event.HidePopupEvent;

class ExplorerDropDownTreePresenter extends DropDownTreePresenter
        implements HasDataSelectionHandlers<ExplorerData> {
    private final ExtendedExplorerTree explorerTree;
    private boolean allowFolderSelection;

    @Inject
    ExplorerDropDownTreePresenter(final EventBus eventBus, final DropDownTreeView view,
                                         final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        setUnselectedText("None");

        explorerTree = new ExtendedExplorerTree(this, dispatcher);

        // Add views.
        view.setCellTree(explorerTree);
    }

    protected void setSelectedTreeItem(final ExplorerData selectedItem,
                                     final SelectionType selectionType, final boolean initial) {
        // Is the selection type valid?
        if (isSelectionAllowed(selectedItem)) {
            // Drop down presenters need to know what the initial selection was so that they can update the name of their selected item properly.
            if (initial) {
                DataSelectionEvent.fire(this, selectedItem, false);
            } else if (selectionType.isDoubleSelect()) {
                DataSelectionEvent.fire(this, selectedItem, true);
                HidePopupEvent.fire(this, this, true, true);
            }
        }
    }

    private boolean isSelectionAllowed(final ExplorerData selected) {
        if (selected == null) {
            return true;
        }

        if (allowFolderSelection) {
            return true;
        }

        if (selected instanceof EntityData) {
            return !Folder.ENTITY_TYPE.equals(selected.getType());
        }

        return false;
    }

    @Override
    public void nameFilterChanged(final String text) {
        explorerTree.changeNameFilter(text);
    }

    @Override
    public void unselect() {
        explorerTree.setSelectedItem(null);
    }

    public void reset() {
        explorerTree.getTreeModel().reset();
    }

    @Override
    public void refresh() {
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
        final EntityData entityData = getSelectedEntityData();
        if (entityData == null) {
            return null;
        }
        return entityData.getDocRef();
    }

    public void setSelectedEntityReference(final DocRef docRef) {
        if (docRef != null) {
            final EntityData entityData = EntityData.create(docRef);
            setSelectedEntityData(entityData);
        } else {
            setSelectedEntityData(null);
        }
    }

    private EntityData getSelectedEntityData() {
        final ExplorerData selected = explorerTree.getSelectionModel().getSelected();
        if (selected != null && selected instanceof EntityData) {
            return (EntityData) selected;
        }
        return null;
    }

    private void setSelectedEntityData(final EntityData entityData) {
        if (entityData != null) {
            explorerTree.setSelectedItem(entityData);
            explorerTree.getTreeModel().reset();
            explorerTree.getTreeModel().setEnsureVisible(entityData);
            explorerTree.getTreeModel().refresh();
        } else {
            explorerTree.getTreeModel().reset();
            explorerTree.setSelectedItem(null);
            explorerTree.getTreeModel().refresh();
        }
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        this.allowFolderSelection = allowFolderSelection;
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<ExplorerData> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final EntityData selected = getSelectedEntityData();
            if (isSelectionAllowed(selected)) {
                DataSelectionEvent.fire(this, selected, false);
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

        @Override
        protected void setInitialSelectedItem(final ExplorerData selection) {
            super.setInitialSelectedItem(selection);
            explorerDropDownTreePresenter.setSelectedTreeItem(selection, new SelectionType(false, false), true);
        }

        @Override
        protected void doSelect(final ExplorerData selection, final SelectionType selectionType) {
            super.doSelect(selection, selectionType);
            explorerDropDownTreePresenter.setSelectedTreeItem(selection, selectionType, false);
        }
    }
}
