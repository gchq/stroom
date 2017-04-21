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

import com.google.gwt.user.client.ui.FormPanel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.AbstractSubmitCompleteHandler;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.importexport.shared.ImportConfigConfirmationAction;
import stroom.util.shared.ResourceKey;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class ImportConfigPresenter
        extends MyPresenter<ImportConfigPresenter.ImportConfigView, ImportConfigPresenter.ImportProxy>
        implements ImportConfigEvent.Handler {
    public interface ImportConfigView extends View {
        FormPanel getForm();
    }

    @ProxyCodeSplit
    public interface ImportProxy extends Proxy<ImportConfigPresenter> {
    }

    private final ClientDispatchAsync dispatcher;

    @Inject
    public ImportConfigPresenter(final EventBus eventBus, final ImportConfigView view, final ImportProxy proxy,
                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, view, proxy);
        this.dispatcher = dispatcher;

        view.getForm().setAction(dispatcher.getImportFileURL());
        view.getForm().setEncoding(FormPanel.ENCODING_MULTIPART);
        view.getForm().setMethod(FormPanel.METHOD_POST);
    }

    @Override
    protected void onBind() {
        super.onBind();

        final AbstractSubmitCompleteHandler submitCompleteHandler = new AbstractSubmitCompleteHandler("Import", this) {
            @Override
            protected void onSuccess(final ResourceKey resourceKey) {
                dispatcher.exec(new ImportConfigConfirmationAction(resourceKey))
                        .onSuccess(result -> {
                            hide();
                            ImportConfigConfirmEvent.fire(ImportConfigPresenter.this, resourceKey, result);
                        })
                        .onFailure(caught -> error(caught.getMessage()));
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
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    // Disable popup buttons as we are submitting.
                    disableButtons();
                    getView().getForm().submit();
                } else {
                    hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        EnablePopupEvent.fire(this, this);
        final PopupSize popupSize = new PopupSize(350, 98, 350, 98, 2000, 98, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Import", popupUiHandlers);
    }

    private void hide() {
        HidePopupEvent.fire(this, this, false, true);
        enableButtons();
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, () -> enableButtons());
    }

    private void disableButtons() {
        DisablePopupEvent.fire(this, this);
    }

    private void enableButtons() {
        EnablePopupEvent.fire(this, this);
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
}
