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
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.RenameDocumentEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.entity.client.presenter.NameDocumentPresenter.NameDocumentProxy;
import stroom.explorer.shared.ExplorerData;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;

public class NameDocumentPresenter extends MyPresenter<NameDocumentView, NameDocumentProxy>
        implements ShowRenameDocumentDialogEvent.Handler, PopupUiHandlers {
    private List<ExplorerData> explorerDataList;
    private ExplorerData entity;

    @Inject
    public NameDocumentPresenter(final EventBus eventBus, final NameDocumentView view, final NameDocumentProxy proxy) {
        super(eventBus, view, proxy);
        view.setUiHandlers(this);
    }

    @ProxyEvent
    @Override
    public void onRename(final ShowRenameDocumentDialogEvent event) {
        explorerDataList = event.getExplorerDataList();
        renameNextEntity();
    }

    private void renameNextEntity() {
        entity = getNextEntity();
        if (entity != null) {
            forceReveal();
        }
    }

    private ExplorerData getNextEntity() {
        if (explorerDataList.size() > 0) {
            return explorerDataList.remove(0);
        }
        return null;
    }

    @Override
    protected void revealInParent() {
        final String caption = "Rename " + entity.getDisplayValue();
        getView().setName(entity.getDisplayValue());
        final PopupSize popupSize = new PopupSize(350, 78, 300, 78, 1024, 78, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, this);
        getView().focus();
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            String entityName = getView().getName();
            if (entityName != null) {
                entityName = entityName.trim();
            }

            if (entityName == null || entityName.length() == 0) {
                AlertEvent.fireWarn(NameDocumentPresenter.this,
                        "You must provide a new name for " + entity.getDisplayValue(), null);
            } else {
                RenameDocumentEvent.fire(this, this, entity.getDocRef(), entityName);
            }
        } else {
            HidePopupEvent.fire(this, this, autoClose, ok);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // If there are any more entities that are to be renamed then go through the whole process again.
        renameNextEntity();
    }

    @ProxyCodeSplit
    public interface NameDocumentProxy extends Proxy<NameDocumentPresenter> {
    }
}
