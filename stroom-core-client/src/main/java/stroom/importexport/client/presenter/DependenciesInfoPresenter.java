/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.importexport.client.presenter;

import stroom.docref.DocRef;
import stroom.importexport.client.event.ShowDependenciesInfoDialogEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import javax.inject.Inject;

public class DependenciesInfoPresenter extends MyPresenter<DependenciesInfoPresenter.DependenciesInfoView,
        DependenciesInfoPresenter.DependenciesInfoProxy> implements ShowDependenciesInfoDialogEvent.Handler {

    @Inject
    public DependenciesInfoPresenter(final EventBus eventBus,
                                     final DependenciesInfoView view,
                                     final DependenciesInfoProxy proxy) {
        super(eventBus, view, proxy);
    }

    @Override
    protected void revealInParent() {
        final PopupSize popupSize = PopupSize.resizable(400, 200);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption("Dependency Information")
                .fire();
    }

    @ProxyEvent
    @Override
    public void onShow(final ShowDependenciesInfoDialogEvent event) {
        final DocRef docRef = event.getDocRef();
        final StringBuilder sb = new StringBuilder();

        sb.append("Type: ");
        sb.append(docRef.getType());
        sb.append("\nUUID: ");
        sb.append(docRef.getUuid());
        sb.append("\nName: ");
        sb.append(docRef.getName());

        getView().setInfo(sb.toString());
        forceReveal();
    }

    @ProxyCodeSplit
    public interface DependenciesInfoProxy extends Proxy<DependenciesInfoPresenter> {

    }

    public interface DependenciesInfoView extends View {
        void setInfo(String info);
    }
}
