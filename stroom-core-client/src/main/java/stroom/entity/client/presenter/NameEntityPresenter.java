/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.entity.client.event.RenameEntityEvent;
import stroom.entity.client.event.ShowRenameEntityDialogEvent;
import stroom.explorer.shared.EntityData;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class NameEntityPresenter extends MyPresenter<NameEntityView, NameEntityPresenter.RenameEntityProxy>
        implements ShowRenameEntityDialogEvent.Handler, PopupUiHandlers {
    private EntityData entity;

    @Inject
    public NameEntityPresenter(final EventBus eventBus, final NameEntityView view, final RenameEntityProxy proxy) {
        super(eventBus, view, proxy);
        view.setUiHandlers(this);
    }

    @ProxyEvent
    @Override
    public void onRename(final ShowRenameEntityDialogEvent event) {
        entity = event.getEntityItem();
        forceReveal();
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
                AlertEvent.fireWarn(NameEntityPresenter.this,
                        "You must provide a new name for " + entity.getDisplayValue(), null);
            } else {
                RenameEntityEvent.fire(this, this, entity.getDocRef(), entityName);
            }
        } else {
            HidePopupEvent.fire(this, this, autoClose, ok);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }

    @ProxyCodeSplit
    public interface RenameEntityProxy extends Proxy<NameEntityPresenter> {
    }
}
