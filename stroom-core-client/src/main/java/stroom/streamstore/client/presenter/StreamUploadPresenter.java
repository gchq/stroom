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

package stroom.streamstore.client.presenter;

import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.AbstractSubmitCompleteHandler;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.EntityItemListBox;
import stroom.entity.shared.DocRef;
import stroom.streamstore.client.view.FileData;
import stroom.streamstore.client.view.FileData.Status;
import stroom.streamstore.shared.UploadDataAction;
import stroom.util.shared.ResourceKey;
import stroom.widget.popup.client.event.DisablePopupEvent;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class StreamUploadPresenter extends MyPresenterWidget<StreamUploadPresenter.DataUploadView> {
    private DocRef feed;
    private StreamPresenter streamPresenter;

    @Inject
    public StreamUploadPresenter(final EventBus eventBus, final DataUploadView view,
                                 final ClientDispatchAsync dispatcher) {
        super(eventBus, view);

        view.getForm().setAction(dispatcher.getImportFileURL());
        view.getForm().setEncoding(FormPanel.ENCODING_MULTIPART);
        view.getForm().setMethod(FormPanel.METHOD_POST);

        final AbstractSubmitCompleteHandler submitCompleteHandler = new AbstractSubmitCompleteHandler("Uploading Data",
                this) {
            @Override
            public void onSubmit(final SubmitEvent event) {
                if (!valid()) {
                    event.cancel();
                } else {
                    super.onSubmit(event);
                }
            }

            @Override
            protected void onSuccess(final ResourceKey resourceKey) {
                final String fileName = getView().getFileUpload().getFilename();
                final Long effectiveMs = getView().getEffectiveDate();
                final UploadDataAction action = new UploadDataAction(resourceKey, feed,
                        getView().getStreamType().getSelectedItem(), effectiveMs, getView().getMetaData(), fileName);

                dispatcher.exec(action)
                        .onSuccess(result -> {
                            hide();
                            AlertEvent.fireInfo(StreamUploadPresenter.this.streamPresenter, "Uploaded file", null);
                            streamPresenter.refresh();
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

    public boolean valid() {
        if (feed == null) {
            AlertEvent.fireWarn(this, "Feed not set!", null);
            return false;
        }
        if (getView().getStreamType().getSelectedItem() == null) {
            AlertEvent.fireWarn(this, "Stream Type not set!", null);
            return false;
        }

        final String fileName = getView().getFileUpload().getFilename();
        if (fileName.trim().length() == 0) {
            AlertEvent.fireWarn(this, "File not set!", null);
            return false;
        }

        final FileData fileData = new FileData();
        fileData.setFileName(fileName);
        fileData.setEffectiveMs(getView().getEffectiveDate());
        fileData.setStatus(Status.UPLOADING);

        return true;
    }

    private void submit() {
        getView().getForm().submit();
    }

    public void show(final StreamPresenter streamPresenter, final DocRef feed) {
        this.streamPresenter = streamPresenter;
        this.feed = feed;

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    if (valid()) {
                        // Disable popup buttons as we are submitting.
                        disableButtons();
                        submit();
                    }
                } else {
                    hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                // Do nothing.
            }
        };

        final PopupSize popupSize = new PopupSize(500, 330, 400, 330, 1024, 1024, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Upload", popupUiHandlers);
    }

    private void hide() {
        HidePopupEvent.fire(this, this);
        enableButtons();
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, this::enableButtons);
    }

    private void disableButtons() {
        DisablePopupEvent.fire(this, this);
    }

    private void enableButtons() {
        EnablePopupEvent.fire(this, this);
    }

    public interface DataUploadView extends View {
        FormPanel getForm();

        EntityItemListBox getStreamType();

        Long getEffectiveDate();

        String getMetaData();

        FileUpload getFileUpload();
    }
}
