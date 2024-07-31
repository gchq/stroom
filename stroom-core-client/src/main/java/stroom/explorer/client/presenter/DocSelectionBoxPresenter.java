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

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collection;
import java.util.Objects;

public class DocSelectionBoxPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, HasDataSelectionHandlers<DocRef>, Focus {

    private final ExplorerPopupPresenter explorerPopupPresenter;
    private boolean enabled = true;

    @Inject
    public DocSelectionBoxPresenter(final EventBus eventBus,
                                    final DropDownView view,
                                    final ExplorerPopupPresenter explorerPopupPresenter) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.explorerPopupPresenter = explorerPopupPresenter;
        explorerPopupPresenter.setSelectionChangeConsumer(this::changeSelection);
        changeSelection(null);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public void setQuickFilter(final String filterInput) {
        explorerPopupPresenter.setInitialQuickFilter(filterInput);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerPopupPresenter.setIncludedTypes(includedTypes);
    }

    public void setIncludedTypes(final Collection<String> includedTypes) {
        explorerPopupPresenter.setIncludedTypes(GwtNullSafe.stream(includedTypes)
                .toArray(String[]::new));
    }

    public void setTags(final String... tags) {
        explorerPopupPresenter.setTags(tags);
    }

    public void setTags(final Collection<String> tags) {
        explorerPopupPresenter.setTags(GwtNullSafe.stream(tags)
                .toArray(String[]::new));
    }

    public void setTags(final StandardExplorerTags... tags) {
        explorerPopupPresenter.setTags(GwtNullSafe.stream(tags)
                .map(StandardExplorerTags::getTagName)
                .toArray(String[]::new));
    }

    public void setNodeFlags(final NodeFlag... nodeFlags) {
        explorerPopupPresenter.setNodeFlags(nodeFlags);
    }

    public void setNodeFlags(final Collection<NodeFlag> nodeFlags) {
        explorerPopupPresenter.setNodeFlags(GwtNullSafe.stream(nodeFlags)
                .toArray(NodeFlag[]::new));
    }

    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        explorerPopupPresenter.setRequiredPermissions(requiredPermissions);
    }

    public DocRef getSelectedEntityReference() {
        return explorerPopupPresenter.getSelectedEntityReference();
    }

    public void setSelectedEntityReference(final DocRef docRef) {
        explorerPopupPresenter.setSelectedEntityReference(docRef);
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        explorerPopupPresenter.setAllowFolderSelection(allowFolderSelection);
    }

    @Override
    public void showPopup() {
        if (enabled) {
            final ExplorerNode initialSelection = explorerPopupPresenter.getCurrentSelection();
            explorerPopupPresenter.show(docRef -> {
                final ExplorerNode currentSelection = explorerPopupPresenter.getCurrentSelection();
                if (!Objects.equals(initialSelection, currentSelection)) {
                    DataSelectionEvent.fire(DocSelectionBoxPresenter.this, docRef, true);
                }
            });
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<DocRef> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
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
