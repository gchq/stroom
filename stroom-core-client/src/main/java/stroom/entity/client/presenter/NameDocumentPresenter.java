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
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.entity.client.presenter.NameDocumentPresenter.NameDocumentProxy;
import stroom.explorer.shared.ExplorerNode;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.List;

public class NameDocumentPresenter
        extends MyPresenter<NameDocumentView, NameDocumentProxy>
        implements ShowRenameDocumentDialogEvent.Handler,
        HidePopupRequestEvent.Handler,
        HidePopupEvent.Handler {

    private List<ExplorerNode> explorerNodeList;
    private ExplorerNode entity;

    @Inject
    public NameDocumentPresenter(final EventBus eventBus, final NameDocumentView view, final NameDocumentProxy proxy) {
        super(eventBus, view, proxy);
        view.setUiHandlers(new DefaultHideRequestUiHandlers(this));
    }

    @ProxyEvent
    @Override
    public void onRename(final ShowRenameDocumentDialogEvent event) {
        explorerNodeList = event.getExplorerNodeList();
        renameNextEntity();
    }

    private void renameNextEntity() {
        entity = getNextEntity();
        if (entity != null) {
            forceReveal();
        }
    }

    private ExplorerNode getNextEntity() {
        if (explorerNodeList.size() > 0) {
            return explorerNodeList.remove(0);
        }
        return null;
    }

    @Override
    protected void revealInParent() {
        final String caption = "Rename " + entity.getDisplayValue();
        getView().setName(entity.getDisplayValue());
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .onHide(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            String entityName = getView().getName();
            if (entityName != null) {
                entityName = entityName.trim();
            }

            if (entityName == null || entityName.length() == 0) {
                AlertEvent.fireWarn(NameDocumentPresenter.this,
                        "You must provide a new name for " + entity.getDisplayValue(), null);
            } else {
                RenameDocumentEvent.fire(NameDocumentPresenter.this,
                        NameDocumentPresenter.this,
                        entity,
                        entityName);
            }
        } else {
            e.hide();
        }
    }

    @Override
    public void onHide(final HidePopupEvent e) {
        // If there are any more entities that are to be renamed then go through the whole process again.
        renameNextEntity();
    }

    @ProxyCodeSplit
    public interface NameDocumentProxy extends Proxy<NameDocumentPresenter> {

    }
}
