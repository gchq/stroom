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

package stroom.importexport.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.AbstractSubmitCompleteHandler;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.ResourceKey;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.ArrayList;
import java.util.List;

public class ImportConfigPresenter
        extends MyPresenter<ImportConfigPresenter.ImportConfigView, ImportConfigPresenter.ImportProxy>
        implements ImportConfigEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final RestFactory restFactory;

    @Inject
    public ImportConfigPresenter(final EventBus eventBus, final ImportConfigView view, final ImportProxy proxy,
                                 final RestFactory restFactory) {
        super(eventBus, view, proxy);
        this.restFactory = restFactory;

        view.getForm().setAction(restFactory.getImportFileURL());
        view.getForm().setEncoding(FormPanel.ENCODING_MULTIPART);
        view.getForm().setMethod(FormPanel.METHOD_POST);
    }

    @Override
    protected void onBind() {
        super.onBind();

        final AbstractSubmitCompleteHandler submitCompleteHandler = new AbstractSubmitCompleteHandler(
                "Import",
                this) {

            @Override
            protected void onSuccess(final ResourceKey resourceKey) {
                final Rest<List<ImportState>> rest = restFactory.create();
                rest
                        .onSuccess(result -> {
                            if (result.isEmpty()) {
                                warning("The import package contains nothing that can be imported into " +
                                        "this version of Stroom.");
                            } else {
                                HidePopupEvent.builder(ImportConfigPresenter.this).fire();
                                enableButtons();
                                ImportConfigConfirmEvent.fire(ImportConfigPresenter.this, resourceKey, result);
                            }
                        })
                        .onFailure(caught -> error(caught.getMessage()))
                        .call(CONTENT_RESOURCE)
                        .confirmImport(new ImportConfigRequest(resourceKey, new ArrayList<>()));
            }

            @Override
            protected void onFailure(final String message) {
                error(message);
            }
        };

        registerHandler(getView().getForm().addSubmitHandler(submitCompleteHandler));
        registerHandler(getView().getForm().addSubmitCompleteHandler(submitCompleteHandler));
    }

    private void show() {
        EnablePopupEvent.builder(this).fire();
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Import")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        // Disable popup buttons as we are submitting.
                        disableButtons();
                        getView().getForm().submit();
                    } else {
                        e.hide();
                        enableButtons();
                    }
                })
                .fire();
    }

    private void warning(final String message) {
        AlertEvent.fireWarn(this, message, this::enableButtons);
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, this::enableButtons);
    }

    private void disableButtons() {
        DisablePopupEvent.builder(this).fire();
    }

    private void enableButtons() {
        EnablePopupEvent.builder(this).fire();
    }

    @ProxyEvent
    @Override
    public void onImport(final ImportConfigEvent event) {
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        show();
    }

    public interface ImportConfigView extends View, Focus {

        FormPanel getForm();
    }

    @ProxyCodeSplit
    public interface ImportProxy extends Proxy<ImportConfigPresenter> {

    }
}
