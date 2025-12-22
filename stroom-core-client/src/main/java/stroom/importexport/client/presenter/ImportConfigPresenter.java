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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.AbstractSubmitCompleteHandler;
import stroom.dispatch.client.RestFactory;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.client.event.ImportConfigEvent;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportSettings;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.StringUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
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

public class ImportConfigPresenter
        extends MyPresenter<ImportConfigPresenter.ImportConfigView, ImportConfigPresenter.ImportProxy>
        implements ImportConfigEvent.Handler {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final RestFactory restFactory;
    private HidePopupRequestEvent currentHidePopupRequestEvent;

    @Inject
    public ImportConfigPresenter(final EventBus eventBus, final ImportConfigView view, final ImportProxy proxy,
                                 final RestFactory restFactory) {
        super(eventBus, view, proxy);
        this.restFactory = restFactory;

        view.getForm().setAction(ImportUtil.getImportFileURL());
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
                restFactory
                        .create(CONTENT_RESOURCE)
                        .method(res -> res.importContent(new ImportConfigRequest(resourceKey,
                                ImportSettings.createConfirmation(),
                                new ArrayList<>())))
                        .onSuccess(response -> {
                            if (response.getConfirmList().isEmpty()) {
                                warning("The import package contains nothing that can be imported into " +
                                        "this version of Stroom.");
                            } else {
                                currentHidePopupRequestEvent.hide();
                                ImportConfigConfirmEvent.fire(ImportConfigPresenter.this, response);
                            }
                        })
                        .onFailure(caught -> error(caught.getMessage()))
                        .taskMonitorFactory(ImportConfigPresenter.this)
                        .exec();
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
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Import")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    currentHidePopupRequestEvent = e;
                    if (e.isOk()) {
                        final String filename = getView().getFilename();
                        if (!StringUtil.isBlank(filename)) {
                            getView().getForm().submit();
                        } else {
                            error("You must select a file to import.");
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void warning(final String message) {
        AlertEvent.fireWarn(this, message, currentHidePopupRequestEvent::reset);
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, currentHidePopupRequestEvent::reset);
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

        String getFilename();
    }

    @ProxyCodeSplit
    public interface ImportProxy extends Proxy<ImportConfigPresenter> {

    }
}
