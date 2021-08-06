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

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.preferences.client.DateTimeFormatter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class InfoDocumentPresenter
        extends MyPresenter<InfoDocumentPresenter.InfoDocumentView, InfoDocumentPresenter.InfoDocumentProxy>
        implements ShowInfoDocumentDialogEvent.Handler {

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public InfoDocumentPresenter(final EventBus eventBus,
                                 final InfoDocumentView view,
                                 final InfoDocumentProxy proxy,
                                 final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view, proxy);
        this.dateTimeFormatter = dateTimeFormatter;
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(450, 300);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Info")
                .onShow(e -> getView().focus())
                .fire();
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowInfoDocumentDialogEvent event) {
        final DocRefInfo info = event.getInfo();
        final DocRef docRef = info.getDocRef();

        final StringBuilder sb = new StringBuilder();
        if (info.getOtherInfo() != null) {
            sb.append(info.getOtherInfo());
            sb.append("\n");
        }

        sb.append("UUID: ");
        sb.append(docRef.getUuid());
        sb.append("\nType: ");
        sb.append(docRef.getType());
        sb.append("\nName: ");
        sb.append(docRef.getName());
        if (info.getCreateUser() != null) {
            sb.append("\nCreated By: ");
            sb.append(info.getCreateUser());
        }
        if (info.getCreateTime() != null) {
            sb.append("\nCreated On: ");
            sb.append(dateTimeFormatter.format(info.getCreateTime()));
        }
        if (info.getUpdateUser() != null) {
            sb.append("\nUpdated By: ");
            sb.append(info.getUpdateUser());
        }
        if (info.getUpdateTime() != null) {
            sb.append("\nUpdated On: ");
            sb.append(dateTimeFormatter.format(info.getUpdateTime()));
        }

        getView().setInfo(sb.toString());

        forceReveal();
    }

    public interface InfoDocumentView extends View, Focus {

        void setInfo(String info);
    }

    @ProxyCodeSplit
    public interface InfoDocumentProxy extends Proxy<InfoDocumentPresenter> {

    }
}
