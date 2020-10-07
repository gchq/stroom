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

import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.item.client.ItemListBox;
import stroom.security.client.presenter.DocumentPermissionsPresenter.DocumentPermissionsView;
import stroom.security.shared.ChangeDocumentPermissionsRequest;
import stroom.security.shared.Changes;
import stroom.security.shared.CopyPermissionsFromParentRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.FetchAllDocumentPermissionsRequest;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView;
import stroom.widget.tab.client.presenter.LinkTabsPresenter;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Button;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class DocumentPermissionsPresenter
        extends MyPresenterWidget<DocumentPermissionsView> {
    private static final DocPermissionResource DOC_PERMISSION_RESOURCE = GWT.create(DocPermissionResource.class);

    private static final Map<String, List<String>> ALL_PERMISSIONS_CACHE = new HashMap<>();

    private final LinkTabsPresenter tabPresenter;
    private final RestFactory restFactory;
    private final Provider<DocumentPermissionsTabPresenter> documentPermissionsListPresenterProvider;
    private final Provider<FolderPermissionsTabPresenter> folderPermissionsListPresenterProvider;

    private Changes changes = new Changes(new HashMap<>(), new HashMap<>());

    @Inject
    public DocumentPermissionsPresenter(final EventBus eventBus,
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
            final TabData users = tabPresenter.addTab("Users", usersPresenter);

            tabPresenter.changeSelectedTab(groups);

            getView().getCopyPermissionsFromParentButton().addClickHandler(event -> {
                final Rest<DocumentPermissions> rest = restFactory.create();
                rest
                        .onSuccess(documentPermissions -> {
                            // We want to wipe existing permissions, which means updating the removeSet on the changeSet.
                            final Map<String, Set<String>> permissionsToRemove = new HashMap<>();
                            permissionsToRemove.putAll(usersPresenter.getDocumentPermissions().getPermissions());
                            permissionsToRemove.putAll(groupsPresenter.getDocumentPermissions().getPermissions());

                            // We need to update the changeSet with all the new permissions.
                            changes = new Changes(documentPermissions.getPermissions(), permissionsToRemove);

                            // We need to set the document permissions so that what's been changed is visible.
                            usersPresenter.setDocumentPermissions(allPermissions, documentPermissions, false, changes);
                            groupsPresenter.setDocumentPermissions(allPermissions, documentPermissions, true, changes);
                        })
                        .call(DOC_PERMISSION_RESOURCE)
                        .copyPermissionFromParent(new CopyPermissionsFromParentRequest(explorerNode.getDocRef()));
            });
            // If we're looking at the root node then we can't copy from the parent because there isn't one.
            if (DocumentTypes.isSystem(explorerNode.getType())) {
                getView().getCopyPermissionsFromParentButton().setEnabled(false);
            }

            final DocRef docRef = explorerNode.getDocRef();
            final Rest<DocumentPermissions> rest = restFactory.create();
            rest
                    .onSuccess(documentPermissions -> {
                        usersPresenter.setDocumentPermissions(allPermissions, documentPermissions, false, changes);
                        groupsPresenter.setDocumentPermissions(allPermissions, documentPermissions, true, changes);

                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                if (ok) {
                                    final Rest<Boolean> rest = restFactory.create();
                                    rest
                                            .onSuccess(result -> hide(autoClose, ok))
                                            .call(DOC_PERMISSION_RESOURCE)
                                            .changeDocumentPermissions(new ChangeDocumentPermissionsRequest(
                                                    docRef,
                                                    changes,
                                                    getView().getCascade().getSelectedItem()));
                                } else {
                                    hide(autoClose, ok);
                                }
                            }

                            @Override
                            public void onHide(boolean autoClose, boolean ok) {
                            }
                        };

                        PopupSize popupSize;
                        if (DocumentTypes.isFolder(explorerNode.getType())) {
                            popupSize = new PopupSize(384, 664, 384, 664, true);
                        } else {
                            popupSize = new PopupSize(384, 500, 384, 500, true);
                        }

                        ShowPopupEvent.fire(
                                DocumentPermissionsPresenter.this,
                                DocumentPermissionsPresenter.this,
                                PopupView.PopupType.OK_CANCEL_DIALOG,
                                popupSize,
                                "Set " + explorerNode.getType() + " Permissions",
                                popupUiHandlers);
                    })
                    .call(DOC_PERMISSION_RESOURCE)
                    .fetchAllDocumentPermissions(new FetchAllDocumentPermissionsRequest(explorerNode.getDocRef()));
        });
    }

    private DocumentPermissionsTabPresenter getTabPresenter(final ExplorerNode entity) {
        if (DocumentTypes.isFolder(entity.getType())) {
            return folderPermissionsListPresenterProvider.get();
        }

        return documentPermissionsListPresenterProvider.get();
    }

    private void hide(boolean autoClose, boolean ok) {
        HidePopupEvent.fire(this, this, autoClose, ok);
    }

    public interface DocumentPermissionsView extends View {
        void setTabsView(View view);

        ItemListBox<ChangeDocumentPermissionsRequest.Cascade> getCascade();

        void setCascadeVisible(boolean visible);

        Button getCopyPermissionsFromParentButton();
    }
}