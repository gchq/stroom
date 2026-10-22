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
import stroom.explorer.shared.DecorateRequest;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Listeners;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class DocSelectionBoxPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, Focus {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final String NONE_DISPLAY_VALUE = "None";

    private final ExplorerPopupPresenter explorerPopupPresenter;
    private final RestFactory restFactory;
    private boolean enabled = true;
    private boolean readOnly;
    private DocRef value = null;
    private String errorMsg = null;
    private String itemType = null;
    // The perms required on any selected docRef or nodes in the picker
    private Set<DocumentPermission> requiredPermissions = null;

    private final Listeners<DocRef> selectionListeners = new Listeners<>();

    @Inject
    public DocSelectionBoxPresenter(final EventBus eventBus,
                                    final DropDownView view,
                                    final ExplorerPopupPresenter explorerPopupPresenter,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        own(explorerPopupPresenter);
        this.restFactory = restFactory;
        this.explorerPopupPresenter = explorerPopupPresenter;
        view.setUiHandlers(this);
//        explorerPopupPresenter.setSelectionChangeConsumer(this::changeSelection);
        changeSelection(null);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    /**
     * Call this if the selection box contains more than one {@link DocRef} type,
     * e.g. all data source types. Value should be singular and in
     * sentence case, e.g. 'Data Source'.
     *
     * @param itemType
     */
    public void setItemType(final String itemType) {
        this.itemType = itemType;
    }

    /**
     * Sets the caption of the chooser popup this box opens.
     *
     * <p>Defaults to {@code "Choose item"}, which says nothing about what is being chosen —
     * unhelpful on a dialog with more than one box. Convention elsewhere is
     * {@code "Choose <Thing>"} in title case.</p>
     *
     * @param caption the popup caption; a null leaves the default in place
     */
    public void setCaption(final String caption) {
        if (caption != null) {
            explorerPopupPresenter.setCaption(caption);
        }
    }

    public void setQuickFilter(final String filterInput) {
        explorerPopupPresenter.setInitialQuickFilter(filterInput);
    }

    public void setIncludedTypes(final String... includedTypes) {
        explorerPopupPresenter.setIncludedTypes(includedTypes);
    }

    public void setIncludedTypes(final Collection<String> includedTypes) {
        explorerPopupPresenter.setIncludedTypes(NullSafe.stream(includedTypes)
                .toArray(String[]::new));
    }

    public void setTags(final String... tags) {
        explorerPopupPresenter.setTags(tags);
    }

    public void setTags(final Collection<String> tags) {
        explorerPopupPresenter.setTags(NullSafe.stream(tags)
                .toArray(String[]::new));
    }

    public void setTags(final StandardExplorerTags... tags) {
        explorerPopupPresenter.setTags(NullSafe.stream(tags)
                .map(StandardExplorerTags::getTagName)
                .toArray(String[]::new));
    }

    public void setNodeFlags(final NodeFlag... nodeFlags) {
        explorerPopupPresenter.setNodeFlags(nodeFlags);
    }

    public void setNodeFlags(final Collection<NodeFlag> nodeFlags) {
        explorerPopupPresenter.setNodeFlags(NullSafe.stream(nodeFlags)
                .toArray(NodeFlag[]::new));
    }

    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        this.requiredPermissions = NullSafe.asSet(requiredPermissions);
        explorerPopupPresenter.setRequiredPermissions(requiredPermissions);

        // In case this is called after setSelectedEntityReference, call that again to
        // do the perm check now that we know what perms are needed.
        if (value != null) {
            setSelectedEntityReference(value, true, null, null);
        }
    }

    public Set<DocumentPermission> getRequiredPermissions() {
        return requiredPermissions;
    }

    public DocRef getSelectedEntityReference() {
        return value;
    }

    /**
     * @param docRef         The {@link DocRef} to set as the selected value of the dropdown.
     *                       If docRef does not exist or is not visible to the user, a warning
     *                       will be displayed but the value will be as passed.
     * @param decorateDocRef If true, force decorate docRef to ensure it has the correct name.
     *                       Set to false if you know docRef has an up-to-date name, is a constant,
     *                       is definitely null, or you don't care.
     */
    public void setSelectedEntityReference(final DocRef docRef, final boolean decorateDocRef) {
        setSelectedEntityReference(docRef, decorateDocRef, null, null);
    }

    /**
     * @see DocSelectionBoxPresenter#setSelectedEntityReference(DocRef, boolean, Consumer, Runnable)
     */
    public void setSelectedEntityReference(final DocRef docRef,
                                           final boolean decorateDocRef,
                                           final Consumer<DocRef> onDocRefDecoration) {
        setSelectedEntityReference(docRef, decorateDocRef, onDocRefDecoration, null);
    }

    /**
     * @param docRef             The {@link DocRef} to set as the selected value of the dropdown.
     *                           If docRef does not exist or is not visible to the user, a warning
     *                           will be displayed but the value will be as passed.
     * @param decorateDocRef     If true, force decorate docRef to ensure it has the correct name.
     *                           Set to false if you know docRef has an up-to-date name, is a constant,
     *                           is definitely null, or you don't care.
     * @param onDocRefDecoration Called when docRef is found to be different to a
     *                           decorated version of it, e.g. it's name has changed.
     * @param onDocRefNotFound   Called docRef does not exist, e.g. it has been deleted
     *                           or its permissions have changed.
     */
    public void setSelectedEntityReference(final DocRef docRef,
                                           final boolean decorateDocRef,
                                           final Consumer<DocRef> onDocRefDecoration,
                                           final Runnable onDocRefNotFound) {
        if (docRef != null && decorateDocRef) {
            // Selected as given at once, so that it is the value until decorating it is done, and if
            // that fails (rather than the value before, or none, which whatever reads the value,
            // e.g. a query expression's term editor, would save in its place)
            explorerPopupPresenter.setSelectedEntityReference(docRef);
            setFieldValue(docRef, null);
            // The docRef may have come from the data within a Doc so the name may be out of date,
            // so decorate it to ensure we have the right name
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(resource ->
                            resource.decorate(DecorateRequest.createWithPermCheck(
                                    docRef, getRequiredPermissions())))
                    .onSuccess(decoratedDocRef -> {
                        if (!Objects.equals(value, docRef)) {
                            // Another has been selected since, e.g. by a term editor moving on to
                            // another term: this one's reply is too late
                            return;
                        }
                        if (decoratedDocRef != null) {
                            explorerPopupPresenter.setSelectedEntityReference(decoratedDocRef);
                            setFieldValue(decoratedDocRef, null);
                            if (!Objects.equals(docRef, decoratedDocRef)) {
                                // The decorated one is different so we need to update
                                if (onDocRefDecoration != null) {
                                    onDocRefDecoration.accept(decoratedDocRef);
                                }
                            }
                        } else {
                            handleDecorateError(docRef, onDocRefNotFound);
                        }
                    })
                    .taskMonitorFactory(this)
                    .exec();
        } else {
            // No decoration
            explorerPopupPresenter.setSelectedEntityReference(docRef);
            setFieldValue(docRef, null);
        }
    }

    private void handleDecorateError(final DocRef docRef,
                                     final Runnable onDocRefNotFound) {
//        GWT.log(error.getException().getClass().getSimpleName() + " - " + error.getMessage());
        // likely doesn't exist or user can't see it, so leave it as is
        // but show a warning, so they know there is a problem

        // Even thought it doesn't exist, set the value as requested but display a
        value = docRef;
        errorMsg = buildNotFoundMessage(docRef);
        setFieldValue(docRef, errorMsg);

        NullSafe.run(onDocRefNotFound);
    }

    // The not-found problem in brief, as shown under the picker; the full message (with the id and
    // what to do) is its tooltip and what screen readers read
    private String buildShortNotFoundMessage(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        final String name = NullSafe.getOrElse(docRef.getName(), n -> "'" + n + "'", "It");
        return name + " can't be found, or you don't have permission to see it.";
    }

    public String buildNotFoundMessage(final DocRef docRef) {
        if (docRef != null) {
            final String type = Objects.requireNonNullElseGet(this.itemType, () ->
                    Objects.requireNonNullElse(docRef.getType(), "Document"));
            final String uuid = docRef.getUuid();
            final String displayName = NullSafe.getOrElse(
                    docRef.getName(),
                    name -> "'" + name + "' (" + uuid + ")",
                    uuid);

            return type +
                   " " +
                   displayName +
                   " doesn't exist or you do not have permission to view it.\n" +
                   "Select a different " + type.toLowerCase() + " or speak to your administrator.";
        } else {
            return null;
        }
    }

    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        explorerPopupPresenter.setAllowFolderSelection(allowFolderSelection);
    }

    @Override
    public void showPopup() {
        if (enabled && !readOnly) {
            final DocRef oldDocRef = getSelectedEntityReference();
            explorerPopupPresenter.show(selectedDocRef -> {
//                GWT.log("currentSelectionRef: " + selectedDocRef);
                if (!Objects.equals(oldDocRef, selectedDocRef)) {
                    setFieldValue(selectedDocRef);
                    selectionListeners.fire(selectedDocRef);
                }
            });
        }
    }

    /// Listens for the user choosing a document, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<DocRef> listener) {
        return selectionListeners.add(listener);
    }

    private void changeSelection(final ExplorerNode selection) {
        setFieldValue(NullSafe.get(selection, ExplorerNode::getDocRef));
    }

    private void setFieldValue(final DocRef value) {
        setFieldValue(value, null);
    }

    private void setFieldValue(final DocRef value, final String errorMsg) {
        this.value = value;
        this.errorMsg = errorMsg;
        getView().setText(NullSafe.getOrElse(value, DocRef::getDisplayValue, NONE_DISPLAY_VALUE));
        // A document that can't be found is shown as a problem with the field, under it
        if (NullSafe.isNonBlankString(errorMsg)) {
            getView().setProblem(buildShortNotFoundMessage(value), errorMsg);
        } else {
            getView().setProblem(null, null);
        }
    }

    /// Makes the selection read only: it can be read and stays in the tab order, but the
    /// picker doesn't open. It looks like the normal field with its value greyed.
    ///
    /// @param readOnly Whether the selection is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        getView().setReadOnly(readOnly);
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        getView().setEnabled(enabled);
    }
}
