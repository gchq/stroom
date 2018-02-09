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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentProxy;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentView;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class CreateDocumentPresenter
        extends MyPresenter<CreateDocumentView, CreateDocumentProxy>
        implements ShowCreateDocumentDialogEvent.Handler, PopupUiHandlers {
    private final EntityTreePresenter entityTreePresenter;
    private String docType;
    private String caption;
    private boolean allowNullFolder;

    @Inject
    public CreateDocumentPresenter(final EventBus eventBus, final CreateDocumentView view, final CreateDocumentProxy proxy,
                                   final EntityTreePresenter entityTreePresenter) {
        super(eventBus, view, proxy);
        this.entityTreePresenter = entityTreePresenter;
        view.setUiHandlers(this);

        view.setFolderView(entityTreePresenter.getView());

        entityTreePresenter.setIncludedTypes(DocumentTypes.FOLDER_TYPES);
        entityTreePresenter.setRequiredPermissions(DocumentPermissionNames.USE, DocumentPermissionNames.READ);
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowCreateDocumentDialogEvent event) {
        docType = event.getDocType();

        entityTreePresenter.setSelectedItem(null);

//        if (event.getCurrentParents() != null && event.getCurrentParents().size() > 0) {
//            ExplorerNode folder = null;
//            for (final ExplorerNode parent : event.getCurrentParents()) {
//                if (folder == null && parent != null && parent instanceof EntityData
//                        && Folder.ENTITY_TYPE.equals(parent.getType())) {
//                    folder = parent;
//                }
//            }
//
//            if (folder != null) {
//                entityTreePresenter.getSelectionModel().setSelected(folder, true);
//            }
//
//            entityTreePresenter.reset(new HashSet<>(event.getCurrentParents()), 1);
//        } else {
//            entityTreePresenter.reset(null, 1);
//        }

        entityTreePresenter.setSelectedItem(event.getSelected());
        entityTreePresenter.getModel().reset();
        entityTreePresenter.getModel().setEnsureVisible(event.getSelected());
        entityTreePresenter.getModel().refresh();

        caption = "New " + event.getDocDisplayType();
        allowNullFolder = event.isAllowNullFolder();

        forceReveal();
    }

    @Override
    protected void revealInParent() {
        getView().setName("");
        getView().setPermissionInheritance(PermissionInheritance.DESTINATION);

        final PopupSize popupSize = new PopupSize(350, 400, 350, 350, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
        getView().focus();
    }

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
                    CreateDocumentEvent.fire(this, this, docType, docName, destinationFolderRef, getView().getPermissionInheritance());
                }
            }
        } else {
            HidePopupEvent.fire(this, this, autoClose, ok);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    private DocRef getFolder() {
        final ExplorerNode selected = entityTreePresenter.getSelectedItem();
        if (selected != null) {
            return selected.getDocRef();
        }

        return null;
    }

    public interface CreateDocumentView extends View, HasUiHandlers<PopupUiHandlers> {
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
