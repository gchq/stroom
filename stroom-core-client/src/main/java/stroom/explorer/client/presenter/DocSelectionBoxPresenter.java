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

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.security.shared.DocumentPermission;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

/// A document picker as a presenter, for the screens that still take presenters: a
/// [DocSelectionController] attached to the view's picker, which it owns. A screen split into a
/// component and a controller declares the picker (`DropDownBox`) in its template and attaches a
/// [DocSelectionController] itself.
public class DocSelectionBoxPresenter extends MyPresenterWidget<DropDownBox> implements Focus {

    /// Shown when no document is chosen.
    public static final String NONE_DISPLAY_VALUE = DocSelectionController.NONE_DISPLAY_VALUE;

    private final DocSelectionController controller;

    @Inject
    public DocSelectionBoxPresenter(final EventBus eventBus,
                                    final DropDownBox view,
                                    final ExplorerPopupPresenter explorerPopupPresenter,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        controller = own(new DocSelectionController(eventBus, explorerPopupPresenter, restFactory));
        // This presenter's own task monitor, if it is given one, else its owner's
        controller.setTaskMonitorFactory(this);
        controller.attach(view);
    }

    @Override
    public void focus() {
        controller.focus();
    }

    /// @see DocSelectionController#setItemType(String)
    public void setItemType(final String itemType) {
        controller.setItemType(itemType);
    }

    /// @see DocSelectionController#setCaption(String)
    public void setCaption(final String caption) {
        controller.setCaption(caption);
    }

    /// @see DocSelectionController#setQuickFilter(String)
    public void setQuickFilter(final String filterInput) {
        controller.setQuickFilter(filterInput);
    }

    /// @see DocSelectionController#setIncludedTypes(String...)
    public void setIncludedTypes(final String... includedTypes) {
        controller.setIncludedTypes(includedTypes);
    }

    /// @see DocSelectionController#setIncludedTypes(Collection)
    public void setIncludedTypes(final Collection<String> includedTypes) {
        controller.setIncludedTypes(includedTypes);
    }

    /// @see DocSelectionController#setTags(String...)
    public void setTags(final String... tags) {
        controller.setTags(tags);
    }

    /// @see DocSelectionController#setTags(Collection)
    public void setTags(final Collection<String> tags) {
        controller.setTags(tags);
    }

    /// @see DocSelectionController#setTags(StandardExplorerTags...)
    public void setTags(final StandardExplorerTags... tags) {
        controller.setTags(tags);
    }

    /// @see DocSelectionController#setNodeFlags(NodeFlag...)
    public void setNodeFlags(final NodeFlag... nodeFlags) {
        controller.setNodeFlags(nodeFlags);
    }

    /// @see DocSelectionController#setNodeFlags(Collection)
    public void setNodeFlags(final Collection<NodeFlag> nodeFlags) {
        controller.setNodeFlags(nodeFlags);
    }

    /// @see DocSelectionController#setRequiredPermissions(DocumentPermission...)
    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        controller.setRequiredPermissions(requiredPermissions);
    }

    /// @see DocSelectionController#getRequiredPermissions()
    public Set<DocumentPermission> getRequiredPermissions() {
        return controller.getRequiredPermissions();
    }

    /// @see DocSelectionController#getSelectedEntityReference()
    public DocRef getSelectedEntityReference() {
        return controller.getSelectedEntityReference();
    }

    /// @see DocSelectionController#setSelectedEntityReference(DocRef, boolean)
    public void setSelectedEntityReference(final DocRef docRef, final boolean decorateDocRef) {
        controller.setSelectedEntityReference(docRef, decorateDocRef);
    }

    /// @see DocSelectionController#setSelectedEntityReference(DocRef, boolean, Consumer)
    public void setSelectedEntityReference(final DocRef docRef,
                                           final boolean decorateDocRef,
                                           final Consumer<DocRef> onDocRefDecoration) {
        controller.setSelectedEntityReference(docRef, decorateDocRef, onDocRefDecoration);
    }

    /// @see DocSelectionController#setSelectedEntityReference(DocRef, boolean, Consumer, Runnable)
    public void setSelectedEntityReference(final DocRef docRef,
                                           final boolean decorateDocRef,
                                           final Consumer<DocRef> onDocRefDecoration,
                                           final Runnable onDocRefNotFound) {
        controller.setSelectedEntityReference(docRef, decorateDocRef, onDocRefDecoration, onDocRefNotFound);
    }

    /// @see DocSelectionController#buildNotFoundMessage(DocRef)
    public String buildNotFoundMessage(final DocRef docRef) {
        return controller.buildNotFoundMessage(docRef);
    }

    /// @see DocSelectionController#setAllowFolderSelection(boolean)
    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        controller.setAllowFolderSelection(allowFolderSelection);
    }

    /// @see DocSelectionController#addSelectionListener(Consumer)
    public HandlerRegistration addSelectionListener(final Consumer<DocRef> listener) {
        return controller.addSelectionListener(listener);
    }

    /// @see DocSelectionController#setReadOnly(boolean)
    public void setReadOnly(final boolean readOnly) {
        controller.setReadOnly(readOnly);
    }

    /// @see DocSelectionController#setEnabled(boolean)
    public void setEnabled(final boolean enabled) {
        controller.setEnabled(enabled);
    }
}
