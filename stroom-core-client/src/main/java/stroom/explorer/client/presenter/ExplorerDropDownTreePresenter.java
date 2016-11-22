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
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.Folder;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.widget.dropdowntree.client.presenter.DropDownTreePresenter;
import stroom.widget.popup.client.event.HidePopupEvent;

public class ExplorerDropDownTreePresenter extends DropDownTreePresenter
        implements HasDataSelectionHandlers<ExplorerData> {
    private final ExtendedExplorerTree explorerTree;
    private boolean allowFolderSelection;
    @Inject
    public ExplorerDropDownTreePresenter(final EventBus eventBus, final DropDownTreeView view,
                                         final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        setUnselectedText("None");

        explorerTree = new ExtendedExplorerTree(this, dispatcher);

        // Add views.
        view.setCellTree(explorerTree);
    }

    private void setSelectedTreeItem(final ExplorerData selectedItem, final boolean fireEvents,
                                     final boolean doubleClick) {
        if (doubleClick) {
            if (selectedItem == null) {
                DataSelectionEvent.fire(this, null, true);
                HidePopupEvent.fire(this, this);
            } else {
                // Is the selection type valid?
                if (isSelectionAllowed(selectedItem)) {
                    DataSelectionEvent.fire(this, selectedItem, true);
                    HidePopupEvent.fire(this, this);
                }
            }
        } else {
            if (selectedItem == null) {
                // Has the selected item changed to null.
                if (fireEvents) {
                    DataSelectionEvent.fire(this, null, false);
                }
            } else {
                // Has the selected item changed.
                if (isSelectionAllowed(selectedItem)) {
                    if (fireEvents) {
                        DataSelectionEvent.fire(this, selectedItem, false);
                    }
                }
            }
        }
    }

    private boolean isSelectionAllowed(final ExplorerData selected) {
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
        explorerTree.unselect();
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
        setSelectedEntityReference(docRef, true);
    }

    public void setSelectedEntityReference(final DocRef docRef, final boolean fireEvents) {
        if (docRef != null) {
            final EntityData entityData = EntityData.create(docRef);
            setSelectedEntityData(entityData, fireEvents);
        } else {
            setSelectedEntityData(null, fireEvents);
        }
    }

    private void setSelectedEntityData(final EntityData entityData, final boolean fireEvents) {
        if (entityData != null) {
            explorerTree.getSelectionModel().setSelected(entityData, true);
            explorerTree.getTreeModel().reset();
            explorerTree.getTreeModel().setEnsureVisible(entityData);
            explorerTree.getTreeModel().refresh();

            if (fireEvents) {
                explorerTree.select(entityData, true);
            }
        } else {
            explorerTree.getTreeModel().reset();
            explorerTree.getSelectionModel().clear();
            explorerTree.getTreeModel().refresh();
        }
    }

    private EntityData getSelectedEntityData() {
        if (explorerTree.getSelectedItem() == null) {
            return null;
        }

        if (explorerTree.getSelectedItem() instanceof EntityData) {
            return (EntityData) explorerTree.getSelectedItem();
        }

        return null;
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
            DataSelectionEvent.fire(this, explorerTree.getSelectedItem(), false);
        }
        super.onHideRequest(autoClose, ok);
    }

    private static class ExtendedExplorerTree extends ExplorerTree {
        private final ExplorerDropDownTreePresenter explorerDropDownTreePresenter;

        public ExtendedExplorerTree(final ExplorerDropDownTreePresenter explorerDropDownTreePresenter, final ClientDispatchAsync dispatcher) {
            super(dispatcher);
            this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;
        }

        @Override
        protected void select(ExplorerData selection, boolean doubleClick) {
            super.select(selection, doubleClick);
            explorerDropDownTreePresenter.setSelectedTreeItem(selection, false, doubleClick);
        }

        public void unselect() {
            selectedItem = null;
            getSelectionModel().clear();
            select(null);
        }
    }
}
