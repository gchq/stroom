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

package stroom.security.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.DocumentPermissionsPresenter.DocumentPermissionsView;
import stroom.security.shared.ChangeDocumentPermissionsRequest;
import stroom.security.shared.ChangeDocumentPermissionsRequest.Cascade;
import stroom.security.shared.Changes;
import stroom.security.shared.CopyPermissionsFromParentRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.FetchAllDocumentPermissionsRequest;
import stroom.security.shared.PermissionChangeImpactSummary;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;
import stroom.widget.tab.client.presenter.LinkTabsPresenter;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentPermissionsPresenter
        extends MyPresenterWidget<DocumentPermissionsView> {

    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private static final Map<String, List<String>> ALL_PERMISSIONS_CACHE = new HashMap<>();

    private final LinkTabsPresenter tabPresenter;
    private final RestFactory restFactory;
    private final Provider<DocumentPermissionsTabPresenter> documentPermissionsListPresenterProvider;
    private final Provider<FolderPermissionsTabPresenter> folderPermissionsListPresenterProvider;

    private Changes changes = new Changes(new HashMap<>(), new HashMap<>());

    // This is the working model of permissions that gets modified as changes are made
    private DocumentPermissions documentPermissions = null;
    // The permissions initially received from the server before any client side changes are made.
    // This is map will be set on show then will not change
    private Map<String, Set<String>> initialPermissions = null;

    @Inject
    public DocumentPermissionsPresenter(
            final EventBus eventBus,
            final DocumentPermissionsView view,
            final LinkTabsPresenter tabPresenter,
            final RestFactory restFactory,
            final Provider<DocumentPermissionsTabPresenter> documentPermissionsListPresenterProvider,
            final Provider<FolderPermissionsTabPresenter> folderPermissionsListPresenterProvider) {

        super(eventBus, view);
        this.tabPresenter = tabPresenter;
        this.restFactory = restFactory;
        this.documentPermissionsListPresenterProvider = documentPermissionsListPresenterProvider;
        this.folderPermissionsListPresenterProvider = folderPermissionsListPresenterProvider;

        view.setTabsView(tabPresenter.getView());
    }

    private void getAllPermissions(final String docType, final Consumer<List<String>> consumer) {
        final List<String> cached = ALL_PERMISSIONS_CACHE.get(docType);
        if (cached != null) {
            consumer.accept(cached);
        } else {
            final Rest<List<String>> rest = restFactory.create();
            rest
                    .onSuccess(permissions -> {
                        ALL_PERMISSIONS_CACHE.put(docType, permissions);
                        consumer.accept(permissions);
                    })
                    .call(DOC_PERMISSION_RESOURCE)
                    .getPermissionForDocType(docType);
        }
    }

    public void show(final ExplorerNode explorerNode) {
        getAllPermissions(explorerNode.getType(), allPermissions -> {
            getView().setCascadeVisible(DocumentTypes.isFolder(explorerNode.getType()));
            final DocumentPermissionsTabPresenter usersPresenter = getTabPresenter(explorerNode);
            final DocumentPermissionsTabPresenter groupsPresenter = getTabPresenter(explorerNode);

            final TabData groups = tabPresenter.addTab("Groups", groupsPresenter);
            tabPresenter.addTab("Users", usersPresenter);
            tabPresenter.changeSelectedTab(groups);

            getView().getCopyPermissionsFromParentButton()
                    .addClickHandler(buildCopyPermissionsFromParentClickHandler(
                            explorerNode,
                            allPermissions,
                            usersPresenter,
                            groupsPresenter));
            // If we're looking at the root node then we can't copy from the parent because there isn't one.
            if (DocumentTypes.isSystem(explorerNode.getType())) {
                getView().getCopyPermissionsFromParentButton().setEnabled(false);
            }

            final DocRef docRef = explorerNode.getDocRef();
            final Rest<DocumentPermissions> rest = restFactory.create();
            rest
                    .onSuccess(documentPermissions -> {
                        this.documentPermissions = documentPermissions;
                        // Take a deep copy of documentPermissions before the user mutates it with client side
                        // changes
                        this.initialPermissions = Collections.unmodifiableMap(
                                DocumentPermissions.copyPermsMap(documentPermissions.getPermissions()));
                        usersPresenter.setDocumentPermissions(
                                allPermissions,
                                documentPermissions,
                                false,
                                changes);
                        groupsPresenter.setDocumentPermissions(
                                allPermissions,
                                documentPermissions,
                                true,
                                changes);

                        final PopupSize popupSize = DocumentTypes.isFolder(explorerNode.getType())
                                ? getFolderPopupSize()
                                : getDocumentPopupSize();

                        ShowPopupEvent.builder(this)
                                .popupType(PopupType.OK_CANCEL_DIALOG)
                                .popupSize(popupSize)
                                .caption("Set " + explorerNode.getType() + " Permissions")
                                .onShow(e -> groupsPresenter.focus())
                                .onHideRequest(e -> onHideRequest(e, docRef))
                                .fire();
                    })
                    .call(DOC_PERMISSION_RESOURCE)
                    .fetchAllDocumentPermissions(new FetchAllDocumentPermissionsRequest(explorerNode.getDocRef()));
        });
    }

    private ClickHandler buildCopyPermissionsFromParentClickHandler(
            final ExplorerNode explorerNode,
            final List<String> allPermissions,
            final DocumentPermissionsTabPresenter usersPresenter,
            final DocumentPermissionsTabPresenter groupsPresenter) {

        return event -> {
            final Rest<DocumentPermissions> rest = restFactory.create();
            rest
                    .onSuccess(parentDocPermissions -> {
                        // We want to wipe existing permissions on the server, which means creating REMOVES
                        // for all the perms that we started with except those that are also on the parent.
                        final Map<String, Set<String>> permissionsToRemove = DocumentPermissions.excludePermissions(
                                initialPermissions,
                                parentDocPermissions.getPermissions());
                        final Map<String, Set<String>> permissionsToAdd = DocumentPermissions.excludePermissions(
                                parentDocPermissions.getPermissions(),
                                initialPermissions);
                        // Now create the ADDs and REMOVEs for the effective changes.
                        changes = new Changes(permissionsToAdd, permissionsToRemove);

                        // We need to set the document permissions so that what's been changed is visible.
                        usersPresenter.setDocumentPermissions(
                                allPermissions,
                                parentDocPermissions,
                                false,
                                changes);
                        groupsPresenter.setDocumentPermissions(
                                allPermissions,
                                parentDocPermissions,
                                true,
                                changes);

//                        GWT.log("After copyFromParent:"
//                                + "\ninitialPermissions:\n"
//                                + DocumentPermissions.permsMapToStr(initialPermissions)
//                                + "\nparentDocPermissions:\n"
//                                + DocumentPermissions.permsMapToStr(parentDocPermissions.getPermissions())
//                                + "\nADDs:\n"
//                                + DocumentPermissions.permsMapToStr(permissionsToAdd)
//                                + "\nREMOVEs:\n"
//                                + DocumentPermissions.permsMapToStr(permissionsToRemove));
                    })
                    .call(DOC_PERMISSION_RESOURCE)
                    .copyPermissionFromParent(new CopyPermissionsFromParentRequest(explorerNode.getDocRef()));
        };
    }


    private static PopupSize getDocumentPopupSize() {
        PopupSize popupSize;
        popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(1000)
                        .min(1000)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(700)
                        .min(700)
                        .resizable(true)
                        .build())
                .build();
        return popupSize;
    }

    private static PopupSize getFolderPopupSize() {
        PopupSize popupSize;
        popupSize = PopupSize.builder()
                .width(Size
                        .builder()
                        .initial(1000)
                        .min(1000)
                        .resizable(true)
                        .build())
                .height(Size
                        .builder()
                        .initial(800)
                        .min(800)
                        .resizable(true)
                        .build())
                .build();
        return popupSize;
    }

    private void onHideRequest(final HidePopupRequestEvent e, final DocRef docRef) {
        if (e.isOk()) {
            final Cascade cascade = getView().getCascade().getValue();
            // If user is cascading then we need to show them a confirm dialog first showing the impact
            // of what they are about to do as it may impact 00s or 000s of documents.
            if (Cascade.isCascading(cascade)) {
                final Rest<PermissionChangeImpactSummary> rest = restFactory.create();
                rest
                        .onSuccess(impactSummary -> {
                            if (GwtNullSafe.isBlankString(impactSummary.getImpactSummary())) {
                                doPermissionChange(e, docRef);
                            } else {
                                ConfirmEvent.fire(
                                        this,
                                        SafeHtmlUtil.toParagraphs(impactSummary.getImpactSummary()),
                                        GwtNullSafe.get(impactSummary.getImpactDetail(), SafeHtmlUtil::toParagraphs),
                                        ok -> {
                                            if (ok) {
                                                doPermissionChange(e, docRef);
                                            }
                                        });
                            }
                        })
                        .call(DOC_PERMISSION_RESOURCE)
                        .fetchPermissionChangeImpact(new ChangeDocumentPermissionsRequest(
                                docRef,
                                changes,
                                getView().getCascade().getValue()));
            } else {
                doPermissionChange(e, docRef);
            }
        } else {
            e.hide();
        }
    }

    private void doPermissionChange(final HidePopupRequestEvent e, final DocRef docRef) {
        final Rest<Boolean> rest = restFactory.create();
        rest
                .onSuccess(result -> e.hide())
                .call(DOC_PERMISSION_RESOURCE)
                .changeDocumentPermissions(new ChangeDocumentPermissionsRequest(
                        docRef,
                        changes,
                        getView().getCascade().getValue()));
    }

    private DocumentPermissionsTabPresenter getTabPresenter(final ExplorerNode entity) {
        if (DocumentTypes.isFolder(entity.getType())) {
            return folderPermissionsListPresenterProvider.get();
        }

        return documentPermissionsListPresenterProvider.get();
    }


    // --------------------------------------------------------------------------------


    public interface DocumentPermissionsView extends View {

        void setTabsView(View view);

        SelectionBox<ChangeDocumentPermissionsRequest.Cascade> getCascade();

        void setCascadeVisible(boolean visible);

        Button getCopyPermissionsFromParentButton();
    }
}
