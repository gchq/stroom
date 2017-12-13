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

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.entity.shared.SharedDocRefInfo;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class InfoDocumentPresenter
        extends MyPresenter<InfoDocumentPresenter.InfoDocumentView, InfoDocumentPresenter.InfoDocumentProxy>
        implements ShowInfoDocumentDialogEvent.Handler, PopupUiHandlers {
    private final ClientDispatchAsync dispatcher;

    @Inject
    public InfoDocumentPresenter(final EventBus eventBus,
                                 final InfoDocumentView view,
                                 final InfoDocumentProxy proxy,
                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, view, proxy);
        this.dispatcher = dispatcher;
    }

    @Override
    protected void revealInParent() {
        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, "Info");
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowInfoDocumentDialogEvent event) {
        final SharedDocRefInfo info = event.getInfo();

        getView().getDocUuid().setText(info.getUuid());
        getView().getDocType().setText("Type - " + info.getType());
        getView().getDocName().setText("Name - " + info.getName());

        final String created = "Created by " + info.getCreateUser() + " at";
        getView().getCreatedUser().setText(created);
        getView().getCreatedTime().setText(ClientDateUtil.toISOString(info.getCreateTime()));
        final String updated = "Updated by " + info.getUpdateUser() + " at";
        getView().getUpdatedUser().setText(updated);
        getView().getUpdatedTime().setText(ClientDateUtil.toISOString(info.getUpdateTime()));

        forceReveal();
    }

    @Override
    public void onHideRequest(boolean autoClose, boolean ok) {

    }

    @Override
    public void onHide(boolean autoClose, boolean ok) {

    }

    public interface InfoDocumentView extends View {

        HasText getDocType();

        HasText getDocUuid();

        HasText getDocName();

        HasText getCreatedUser();

        HasText getCreatedTime();

        HasText getUpdatedUser();

        HasText getUpdatedTime();
    }

    @ProxyCodeSplit
    public interface InfoDocumentProxy extends Proxy<InfoDocumentPresenter> {
    }
}
