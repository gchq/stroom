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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import stroom.alert.client.event.AlertEvent;
import stroom.entity.client.EntityTabData;
import stroom.entity.client.event.SaveAsEntityEvent;
import stroom.entity.client.event.ShowSaveAsEntityDialogEvent;
import stroom.entity.shared.NamedEntity;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class SaveAsEntityPresenter
        extends MyPresenter<SaveAsEntityPresenter.SaveAsEntityView, SaveAsEntityPresenter.SaveAsEntityProxy>
        implements ShowSaveAsEntityDialogEvent.Handler, PopupUiHandlers {
    public interface SaveAsEntityView extends View, HasUiHandlers<PopupUiHandlers> {
        String getName();

        void setName(String name);
    }

    @ProxyCodeSplit
    public interface SaveAsEntityProxy extends Proxy<SaveAsEntityPresenter> {
    }

    private EntityTabData tabData;
    private NamedEntity entity;

    @Inject
    public SaveAsEntityPresenter(final EventBus eventBus, final SaveAsEntityView view, final SaveAsEntityProxy proxy) {
        super(eventBus, view, proxy);
        view.setUiHandlers(this);
    }

    @ProxyEvent
    @Override
    public void onSaveAs(final ShowSaveAsEntityDialogEvent event) {
        tabData = event.getTabData();
        entity = getEntity(tabData);
        getView().setName(entity.getName());
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        final String caption = "Save " + entity.getDisplayValue() + " As";
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, caption, this);
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            String entityName = getView().getName();
            if (entityName != null) {
                entityName = entityName.trim();
            }

            if (entityName == null || entityName.length() == 0 || entityName.equals(entity.getName())) {
                AlertEvent.fireWarn(SaveAsEntityPresenter.this, "You must provide a new name for " + entity.getName(),
                        null);
            } else {
                SaveAsEntityEvent.fire(this, this, tabData, entityName);
            }
        } else {
            HidePopupEvent.fire(this, this, autoClose, ok);
        }
    }

    private NamedEntity getEntity(final EntityTabData tabData) {
        return ((EntityEditPresenter<?, ?>) tabData).getEntity();
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
        // Do nothing.
    }
}
