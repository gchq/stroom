/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.explorer.shared.ExplorerData;
import stroom.query.api.v1.DocRef;
import stroom.widget.dropdowntree.client.presenter.DropDownPresenter;

public class EntityDropDownPresenter extends DropDownPresenter implements HasDataSelectionHandlers<ExplorerData> {
    private final ExplorerDropDownTreePresenter explorerDropDownTreePresenter;

    @Inject
    public EntityDropDownPresenter(final EventBus eventBus, final DropDrownView view,
                                   final ExplorerDropDownTreePresenter explorerDropDownTreePresenter) {
        super(eventBus, view);
        this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;
        changeSelection(null);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(explorerDropDownTreePresenter.addDataSelectionHandler(event -> changeSelection(event.getSelectedItem())));
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerDropDownTreePresenter.setIncludedTypes(includedTypes);
    }

    public void setTags(final String... tags) {
        explorerDropDownTreePresenter.setTags(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        explorerDropDownTreePresenter.setRequiredPermissions(requiredPermissions);
    }

    public DocRef getSelectedEntityReference() {
        return explorerDropDownTreePresenter.getSelectedEntityReference();
    }

    public void setSelectedEntityReference(final DocRef docRef) {
        explorerDropDownTreePresenter.setSelectedEntityReference(docRef);
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        explorerDropDownTreePresenter.setAllowFolderSelection(allowFolderSelection);
    }

    @Override
    public void showPopup() {
        explorerDropDownTreePresenter.show();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<ExplorerData> handler) {
        return explorerDropDownTreePresenter.addDataSelectionHandler(handler);
    }

    private void changeSelection(final ExplorerData selection) {
        if (selection == null) {
            getView().setText("None");
        } else {
            getView().setText(selection.getDisplayValue());
        }
    }
}
