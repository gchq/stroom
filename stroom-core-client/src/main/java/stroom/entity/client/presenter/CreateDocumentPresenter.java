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

package stroom.entity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.docref.DocRef;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentProxy;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentView;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.HideUiHandlers;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.function.Consumer;

public class CreateDocumentPresenter
        extends MyPresenter<CreateDocumentView, CreateDocumentProxy>
        implements ShowCreateDocumentDialogEvent.Handler {

    private final PopupUiHandlers popupUiHandlers;
    private final EntityTreePresenter entityTreePresenter;
    private String docType;
    private String caption;
    private String name = "";
    private boolean allowNullFolder;
    private Consumer<DocRef> newDocConsumer;

    @Inject
    public CreateDocumentPresenter(final EventBus eventBus,
                                   final CreateDocumentView view,
                                   final CreateDocumentProxy proxy,
                                   final EntityTreePresenter entityTreePresenter) {
        super(eventBus, view, proxy);
        popupUiHandlers = new DefaultPopupUiHandlers(this) {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DocRef destinationFolderRef = getFolder();
                    if (!allowNullFolder && destinationFolderRef == null) {
                        AlertEvent.fireWarn(CreateDocumentPresenter.this, "No parent folder has been selected", null);
                    } else {
                        String docName = getView().getName();
                        if (docName != null) {
                            docName = docName.trim();
                        }

                        if (docName == null || docName.length() == 0) {
                            AlertEvent.fireWarn(CreateDocumentPresenter.this,
                                    "You must provide a name for the new " + docType.toLowerCase(), null);
                        } else {
                            CreateDocumentEvent.fire(CreateDocumentPresenter.this,
                                    CreateDocumentPresenter.this,
                                    docType,
                                    docName,
                                    destinationFolderRef,
                                    getView().getPermissionInheritance(),
                                    newDocConsumer);
                        }
                    }
                } else {
                    hide(autoClose, ok);
                }
            }
        };
        this.entityTreePresenter = entityTreePresenter;
        view.setUiHandlers(popupUiHandlers);
        view.setFolderView(entityTreePresenter.getView());

        entityTreePresenter.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
        entityTreePresenter.setRequiredPermissions(DocumentPermissionNames.USE, DocumentPermissionNames.READ);
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowCreateDocumentDialogEvent event) {
        docType = event.getDocType();
        newDocConsumer = event.getNewDocConsumer();

        entityTreePresenter.setSelectedItem(null);
        entityTreePresenter.setSelectedItem(event.getSelected());
        entityTreePresenter.getModel().reset();
        entityTreePresenter.getModel().setEnsureVisible(event.getSelected());
        entityTreePresenter.getModel().refresh();

        caption = event.getDialogCaption();
        if (event.getInitialDocName() != null) {
            name = event.getInitialDocName();
        }
        allowNullFolder = event.isAllowNullFolder();

        forceReveal();
    }

    @Override
    protected void revealInParent() {
        getView().setName(name);
        getView().setPermissionInheritance(PermissionInheritance.DESTINATION);

        final PopupSize popupSize = PopupSize.resizable(400, 550);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, popupUiHandlers);
        getView().focus();
    }

    private DocRef getFolder() {
        final ExplorerNode selected = entityTreePresenter.getSelectedItem();
        if (selected != null) {
            return selected.getDocRef();
        }

        return null;
    }

    public interface CreateDocumentView extends View, HasUiHandlers<HideUiHandlers> {

        String getName();

        void setName(String name);

        void setFolderView(View view);

        void setFoldersVisible(final boolean visible);

        void focus();

        PermissionInheritance getPermissionInheritance();

        void setPermissionInheritance(PermissionInheritance permissionInheritance);
    }

    @ProxyCodeSplit
    public interface CreateDocumentProxy extends Proxy<CreateDocumentPresenter> {

    }
}
