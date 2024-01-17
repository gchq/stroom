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
 */

package stroom.explorer.client.presenter;

import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collection;

public class EntityDropDownPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, HasDataSelectionHandlers<ExplorerNode>, Focus {

    private final ExplorerDropDownTreePresenter explorerDropDownTreePresenter;
    private boolean enabled = true;

    @Inject
    public EntityDropDownPresenter(final EventBus eventBus,
                                   final DropDownView view,
                                   final ExplorerDropDownTreePresenter explorerDropDownTreePresenter) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.explorerDropDownTreePresenter = explorerDropDownTreePresenter;
        changeSelection(null);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(explorerDropDownTreePresenter.addDataSelectionHandler(event ->
                changeSelection(event.getSelectedItem())));
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public void setQuickFilter(final String filterInput) {
        explorerDropDownTreePresenter.setInitialQuickFilter(filterInput);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerDropDownTreePresenter.setIncludedTypes(includedTypes);
    }

    public void setIncludedTypes(final Collection<String> includedTypes) {
        explorerDropDownTreePresenter.setIncludedTypes(GwtNullSafe.stream(includedTypes)
                .toArray(String[]::new));
    }

    public void setTags(final String... tags) {
        explorerDropDownTreePresenter.setTags(tags);
    }

    public void setTags(final Collection<String> tags) {
        explorerDropDownTreePresenter.setTags(GwtNullSafe.stream(tags)
                .toArray(String[]::new));
    }

    public void setTags(final StandardExplorerTags... tags) {
        explorerDropDownTreePresenter.setTags(GwtNullSafe.stream(tags)
                .map(StandardExplorerTags::getTagName)
                .toArray(String[]::new));
    }

    public void setNodeFlags(final NodeFlag... nodeFlags) {
        explorerDropDownTreePresenter.setNodeFlags(nodeFlags);
    }

    public void setNodeFlags(final Collection<NodeFlag> nodeFlags) {
        explorerDropDownTreePresenter.setNodeFlags(GwtNullSafe.stream(nodeFlags)
                .toArray(NodeFlag[]::new));
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

    public void setSelectedEntityReference(final DocRef docRef, final boolean fireEvents) {
        explorerDropDownTreePresenter.setSelectedEntityReference(docRef, fireEvents);
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        explorerDropDownTreePresenter.setAllowFolderSelection(allowFolderSelection);
    }

    @Override
    public void showPopup() {
        if (enabled) {
            explorerDropDownTreePresenter.show();
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<ExplorerNode> handler) {
        return explorerDropDownTreePresenter.addDataSelectionHandler(handler);
    }

    private void changeSelection(final ExplorerNode selection) {
        if (selection == null) {
            getView().setText("None");
        } else {
            getView().setText(selection.getDisplayValue());
        }
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            getView().asWidget().getElement().addClassName("disabled");
        } else {
            getView().asWidget().getElement().removeClassName("disabled");
        }
    }
}
