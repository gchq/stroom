/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DecorateRequest;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.DropDownUiHandlers;
import stroom.widget.dropdowntree.client.view.DropDownView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class DocSelectionBoxPresenter extends MyPresenterWidget<DropDownView>
        implements DropDownUiHandlers, HasDataSelectionHandlers<DocRef>, Focus {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);
    public static final String NONE_DISPLAY_VALUE = "None";

    private final ExplorerPopupPresenter explorerPopupPresenter;
    private final RestFactory restFactory;
    private boolean enabled = true;
    private DocRef value = null;
    private String errorMsg = null;
    private String itemType = null;
    // The perms required on any selected docRef or nodes in the picker
    private Set<DocumentPermission> requiredPermissions = null;

    @Inject
    public DocSelectionBoxPresenter(final EventBus eventBus,
                                    final DropDownView view,
                                    final ExplorerPopupPresenter explorerPopupPresenter,
                                    final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.explorerPopupPresenter = explorerPopupPresenter;
        view.setUiHandlers(this);
//        explorerPopupPresenter.setSelectionChangeConsumer(this::changeSelection);
        changeSelection(null);
    }

    @Override
    protected void onBind() {
        registerHandler(getView().addWarningClickHandler(event -> {
            event.getNativeEvent().stopPropagation();
            AlertEvent.fireWarn(DocSelectionBoxPresenter.this, getErrorMsg(), null);
        }));
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
            // The docRef may have come from the data within a Doc so the name may be out of date,
            // so decorate it to ensure we have the right name
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(resource ->
                            resource.decorate(DecorateRequest.createWithPermCheck(
                                    docRef, getRequiredPermissions())))
                    .onSuccess(decoratedDocRef -> {
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

    public String buildNotFoundMessage(final DocRef docRef) {
        if (docRef != null) {
            final String type = NullSafe.requireNonNullElseGet(this.itemType, () ->
                    NullSafe.requireNonNullElse(docRef.getType(), "Document"));
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
        if (enabled) {
            final DocRef oldDocRef = getSelectedEntityReference();
            explorerPopupPresenter.show(selectedDocRef -> {
//                GWT.log("currentSelectionRef: " + selectedDocRef);
                if (!Objects.equals(oldDocRef, selectedDocRef)) {
                    setFieldValue(selectedDocRef);
                    DataSelectionEvent.fire(DocSelectionBoxPresenter.this, selectedDocRef, true);
                }
            });
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<DocRef> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
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
        getView().setText(
                NullSafe.getOrElse(value, DocRef::getDisplayValue, NONE_DISPLAY_VALUE),
                NullSafe.isNonBlankString(errorMsg));
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            getView().asWidget().getElement().addClassName("disabled");
        } else {
            getView().asWidget().getElement().removeClassName("disabled");
        }
    }

    private String getErrorMsg() {
        return errorMsg;
    }
}
