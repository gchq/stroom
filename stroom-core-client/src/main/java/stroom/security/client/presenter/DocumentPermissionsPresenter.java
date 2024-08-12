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

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionPopup;
import stroom.security.client.presenter.DocumentPermissionsPresenter.DocumentPermissionsView;
import stroom.security.shared.DocPermissionResource;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.Size;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentPermissionsPresenter
        extends MyPresenterWidget<DocumentPermissionsView>
        implements DocumentPermissionsUiHandlers {

    private final RestFactory restFactory;
    private final Provider<DocumentPermissionsEditPresenter> documentPermissionsEditPresenterProvider;
    private final Provider<SelectUserPresenter> selectUserPresenterProvider;
    private final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter;
    private final Provider<DocSelectionPopup> docSelectionPopupProvider;

    private DocRef docRef;

    @Inject
    public DocumentPermissionsPresenter(
            final EventBus eventBus,
            final DocumentPermissionsView view,
            final RestFactory restFactory,
            final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter,
            final Provider<DocumentPermissionsEditPresenter> documentPermissionsEditPresenterProvider,
            final Provider<SelectUserPresenter> selectUserPresenterProvider,
            final Provider<DocSelectionPopup> docSelectionPopupProvider) {

        super(eventBus, view);
        this.restFactory = restFactory;
        this.documentPermissionsEditPresenterProvider = documentPermissionsEditPresenterProvider;
        this.selectUserPresenterProvider = selectUserPresenterProvider;
        this.documentUserPermissionsListPresenter = documentUserPermissionsListPresenter;
        this.docSelectionPopupProvider = docSelectionPopupProvider;
        view.setPermissionsView(documentUserPermissionsListPresenter.getView());
        view.setUiHandlers(this);
    }

    @Override
    public void editPermissions() {
        final DocumentPermissionsEditPresenter documentPermissionsEditPresenter =
                documentPermissionsEditPresenterProvider.get();
        documentPermissionsEditPresenter.show(docRef, () -> {
            documentUserPermissionsListPresenter.refresh();
        });
    }

    //    private void enableButtons() {
//        removeButton.setEnabled(documentUserPermissionsListPresenter.getSelectionModel().getSelected() != null);
//    }
//
//    private void edit() {
//        final DocumentUserPermissions documentUserPermissions = documentUserPermissionsListPresenter
//                .getSelectionModel()
//                .getSelected();
//        if (documentUserPermissions != null) {
//            final Consumer<User> consumer = user -> {
//                final DocumentUserPermissionEditPresenter editPresenter =
//                        documentUserPermissionEditPresenterProvider.get();
//                editPresenter.show(docRef, documentUserPermissions, updated -> {
//                    documentUserPermissionsListPresenter.refresh();
//                });
//            };
//            selectUserPresenterProvider.get().show(consumer);
//        }
//    }
//
//    private void add() {
//        final Consumer<User> consumer = user -> {
//            final DocumentUserPermissionEditPresenter editPresenter =
//            documentUserPermissionEditPresenterProvider.get();
//            final DocumentUserPermissions documentUserPermissions = new DocumentUserPermissions(
//                    user.asRef(),
//                    DocumentPermission.USE,
//                    Collections.emptySet());
//            editPresenter.show(docRef, documentUserPermissions, updated -> {
//                documentUserPermissionsListPresenter.refresh();
//                documentUserPermissionsListPresenter.getSelectionModel().setSelected(updated);
//            });
//        };
//        selectUserPresenterProvider.get().show(consumer);
//    }
//
//    private void remove() {
//        final DocumentUserPermissions documentUserPermissions =
//                documentUserPermissionsListPresenter.getSelectionModel().getSelected();
//        if (documentUserPermissions != null) {
//            ConfirmEvent.fire(this, "Are you sure you want to remove all permissions for '" +
//                    documentUserPermissions.getUserRef().toDisplayString() + "'?", ok -> {
//                if (ok) {
//                    documentUserPermissionsListPresenter.getSelectionModel().clear();
//                    doRemove(documentUserPermissions);
//                }
//            });
//        }
//    }
//
//    private void doRemove(final DocumentUserPermissions documentUserPermissions) {
//        final DocumentUserPermissions newPermissions = new DocumentUserPermissions(
//                documentUserPermissions.getUserRef(),
//                null,
//                null);
//        final SetDocumentUserPermissionsRequest request =
//                new SetDocumentUserPermissionsRequest(docRef, newPermissions);
//        restFactory
//                .create(DOC_PERMISSION_RESOURCE)
//                .method(res -> res.setDocumentUserPermissions(request))
//                .onSuccess(result -> {
//                    documentUserPermissionsListPresenter.refresh();
//
////                        GWT.log("After copyFromParent:"
////                                + "\ninitialPermissions:\n"
////                                + DocumentPermissions.permsMapToStr(initialPermissions)
////                                + "\nparentDocPermissions:\n"
////                                + DocumentPermissions.permsMapToStr(parentDocPermissions.getPermissions())
////                                + "\nADDs:\n"
////                                + DocumentPermissions.permsMapToStr(permissionsToAdd)
////                                + "\nREMOVEs:\n"
////                                + DocumentPermissions.permsMapToStr(permissionsToRemove));
//                })
//                .taskListener(documentUserPermissionsListPresenter.getPagerView())
//                .exec();
//    }
//
//    private void copy() {
//        final DocSelectionPopup popup = docSelectionPopupProvider.get();
//        popup.setCaption("Select Document To Copy Permissions From");
//        popup.setRequiredPermissions(DocumentPermission.VIEW);
//        popup.show(source -> {
//            if (source != null) {
//                final CopyDocumentPermissionsRequest request =
//                        new CopyDocumentPermissionsRequest(source, docRef);
//                restFactory
//                        .create(DOC_PERMISSION_RESOURCE)
//                        .method(res -> res.copyDocumentPermissions(request))
//                        .onSuccess(result -> {
//                            documentUserPermissionsListPresenter.refresh();
//                        })
//                        .taskListener(documentUserPermissionsListPresenter.getPagerView())
//                        .exec();
//            }
//        });
//    }
//
//    private void clear() {
//        ConfirmEvent.fire(this, "Are you sure you want to remove all permissions?", ok -> {
//            if (ok) {
//                documentUserPermissionsListPresenter.getSelectionModel().clear();
//                doClear();
//            }
//        });
//    }
//
//    private void doClear() {
//        final ClearDocumentPermissionsRequest request =
//                new ClearDocumentPermissionsRequest(docRef);
//        restFactory
//                .create(DOC_PERMISSION_RESOURCE)
//                .method(res -> res.clearDocumentPermissions(request))
//                .onSuccess(result -> {
//                    documentUserPermissionsListPresenter.refresh();
//                })
//                .taskListener(documentUserPermissionsListPresenter.getPagerView())
//                .exec();
//    }

    public void show(final DocRef docRef) {
        this.docRef = docRef;
        documentUserPermissionsListPresenter.setDocRef(docRef);

        final PopupSize popupSize = PopupSize.builder()
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

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Permissions For '" + docRef.getDisplayValue() + "'")
                .modal()
                .fire();
    }

//    @Override
//    public void onAddPermissionsFromParentFolder(final TaskListener taskListener) {
//        if (docRefs.size() > 1) {
//            ConfirmEvent.fire(this, "Are you sure you want to add permissions from parent folders?",
//                    ok -> {
//                        if (ok) {
//                            restFactory
//                                    .create(DOC_PERMISSION_RESOURCE)
//                                    .method(res -> res.addPermissionsFromParentFolder(docRefs))
//                                    .onSuccess(result -> {
//                                        documentUserPermissionsListPresenter.refresh();
//
////                        GWT.log("After copyFromParent:"
////                                + "\ninitialPermissions:\n"
////                                + DocumentPermissions.permsMapToStr(initialPermissions)
////                                + "\nparentDocPermissions:\n"
////                                + DocumentPermissions.permsMapToStr(parentDocPermissions.getPermissions())
////                                + "\nADDs:\n"
////                                + DocumentPermissions.permsMapToStr(permissionsToAdd)
////                                + "\nREMOVEs:\n"
////                                + DocumentPermissions.permsMapToStr(permissionsToRemove));
//                                    })
//                                    .taskListener(taskListener)
//                                    .exec();
//                        }
//                    });
//        }
//
//
//    }

    public interface DocumentPermissionsView extends View, HasUiHandlers<DocumentPermissionsUiHandlers> {

        void setPermissionsView(View view);

        void setEditVisible(boolean visible);
    }
}
