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
import stroom.document.client.event.CreateDocumentEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentProxy;
import stroom.entity.client.presenter.CreateDocumentPresenter.CreateDocumentView;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.PermissionInheritance;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.user.client.ui.Focus;
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
        implements ShowCreateDocumentDialogEvent.Handler,
        HidePopupRequestEvent.Handler {

    private final EntityTreePresenter entityTreePresenter;
    private String docType;
    private String caption;
    private String name = "";
    private boolean allowNullFolder;
    private Consumer<ExplorerNode> newDocConsumer;

    @Inject
    public CreateDocumentPresenter(final EventBus eventBus,
                                   final CreateDocumentView view,
                                   final CreateDocumentProxy proxy,
                                   final EntityTreePresenter entityTreePresenter) {
        super(eventBus, view, proxy);

        this.entityTreePresenter = entityTreePresenter;
        view.setUiHandlers(new DefaultHideRequestUiHandlers(this));
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
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final ExplorerNode destinationFolder = getFolder();
            if (!allowNullFolder && destinationFolder == null) {
                AlertEvent.fireWarn(CreateDocumentPresenter.this,
                        "No parent folder has been selected",
                        null);
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
                            destinationFolder,
                            getView().getPermissionInheritance(),
                            newDocConsumer);
                }
            }
        } else {
            e.hide();
        }
    }

    private ExplorerNode getFolder() {
        return entityTreePresenter.getSelectedItem();
    }

    public interface CreateDocumentView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        String getName();

        void setName(String name);

        void setFolderView(View view);

        void setFoldersVisible(final boolean visible);

        PermissionInheritance getPermissionInheritance();

        void setPermissionInheritance(PermissionInheritance permissionInheritance);
    }

    @ProxyCodeSplit
    public interface CreateDocumentProxy extends Proxy<CreateDocumentPresenter> {

    }
}
