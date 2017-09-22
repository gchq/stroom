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
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.ForkDocumentEvent;
import stroom.document.client.event.ShowForkDocumentDialogEvent;
import stroom.entity.client.presenter.ForkDocumentPresenter.ForkDocumentProxy;
import stroom.entity.client.presenter.ForkDocumentPresenter.ForkDocumentView;
import stroom.entity.shared.PermissionInheritance;
import stroom.explorer.client.presenter.EntityTreePresenter;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ForkDocumentPresenter
        extends MyPresenter<ForkDocumentView, ForkDocumentProxy>
        implements ShowForkDocumentDialogEvent.Handler, PopupUiHandlers {
    private final EntityTreePresenter entityTreePresenter;
    private DocumentTabData tabData;

    @Inject
    public ForkDocumentPresenter(final EventBus eventBus,
                                 final ForkDocumentView view,
                                 final ForkDocumentProxy proxy,
                                 final EntityTreePresenter entityTreePresenter) {
        super(eventBus, view, proxy);
        this.entityTreePresenter = entityTreePresenter;
        view.setUiHandlers(this);

        view.setFolderView(entityTreePresenter.getView());

        entityTreePresenter.setIncludedTypes(ExplorerConstants.FOLDER);
        entityTreePresenter.setRequiredPermissions(DocumentPermissionNames.USE, DocumentPermissionNames.READ);
    }

    @ProxyEvent
    @Override
    public void onSaveAs(final ShowForkDocumentDialogEvent event) {
        getView().setPermissionInheritance(PermissionInheritance.INHERIT);
        tabData = event.getTabData();

        getView().setName(tabData.getDocRef().getName());

        final ExplorerNode entityData = ExplorerNode.create(event.getTabData().getDocRef());

        entityTreePresenter.setSelectedItem(entityData);
        entityTreePresenter.getModel().reset();
        entityTreePresenter.getModel().setEnsureVisible(entityData);
        entityTreePresenter.getModel().refresh();

        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final String caption = "Save " + tabData.getDocRef().getName() + " As";
        getView().setName(tabData.getDocRef().getName());
        final PopupSize popupSize = new PopupSize(350, 400, 350, 350, 2000, 2000, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
        getView().focus();
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final DocRef destinationFolderRef = getFolder();
            // if (!allowNullFolder && folder == null) {
            // AlertEvent.fireWarn(CopyEntityPresenter.this,
            // "No parent group has been selected", null);
            // } else {
            String docName = getView().getName();
            if (docName != null) {
                docName = docName.trim();
            }

            if (docName == null || docName.length() == 0) {
                AlertEvent.fireWarn(ForkDocumentPresenter.this,
                        "You must provide a name for the new " + tabData.getType().toLowerCase(), null);
            } else {
                ForkDocumentEvent.fire(ForkDocumentPresenter.this, ForkDocumentPresenter.this, tabData,
                        docName, destinationFolderRef, getView().getPermissionInheritance());
            }
            // }
        } else {
            HidePopupEvent.fire(ForkDocumentPresenter.this, ForkDocumentPresenter.this, autoClose, ok);
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

    public interface ForkDocumentView extends View, HasUiHandlers<PopupUiHandlers> {
        String getName();

        void setName(String name);

        void setFolderView(View view);

        void focus();

        PermissionInheritance getPermissionInheritance();

        void setPermissionInheritance(PermissionInheritance permissionInheritance);
    }

    @ProxyCodeSplit
    public interface ForkDocumentProxy extends Proxy<ForkDocumentPresenter> {
    }
}
