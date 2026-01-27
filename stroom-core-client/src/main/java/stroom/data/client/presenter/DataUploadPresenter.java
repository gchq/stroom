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

package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.view.FileData;
import stroom.data.client.view.FileData.Status;
import stroom.data.shared.DataResource;
import stroom.data.shared.StreamTypeNames;
import stroom.data.shared.UploadDataRequest;
import stroom.dispatch.client.AbstractSubmitCompleteHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.item.client.SelectionBox;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.form.client.CustomFileUpload;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class DataUploadPresenter
        extends MyPresenterWidget<DataUploadPresenter.DataUploadView> {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

    private DocRef feedRef;
    private MetaPresenter metaPresenter;
    private final DataTypeUiManager dataTypeUiManager;
    private final RestFactory restFactory;
    private final FeedClient feedClient;
    private HidePopupRequestEvent currentHideRequest;

    @Inject
    public DataUploadPresenter(final EventBus eventBus,
                               final DataUploadView view,
                               final RestFactory restFactory,
                               final FeedClient feedClient,
                               final DataTypeUiManager dataTypeUiManager) {
        super(eventBus, view);
        this.dataTypeUiManager = dataTypeUiManager;
        this.restFactory = restFactory;
        this.feedClient = feedClient;

        view.getFileUpload().setAction(ImportUtil.getImportFileURL());
        view.getFileUpload().setEncoding(FormPanel.ENCODING_MULTIPART);
        view.getFileUpload().setMethod(FormPanel.METHOD_POST);

        final AbstractSubmitCompleteHandler submitCompleteHandler = new AbstractSubmitCompleteHandler("Uploading Data",
                this) {
            @Override
            public void onSubmit(final SubmitEvent event) {
                if (!valid()) {
                    event.cancel();
                    currentHideRequest.reset();
                } else {
                    super.onSubmit(event);
                }
            }

            @Override
            protected void onSuccess(final ResourceKey resourceKey) {
                final String fileName = getView().getFileUpload().getFilename();
                final Long effectiveMs = getView().getEffectiveDate();
                final UploadDataRequest request = new UploadDataRequest(
                        resourceKey,
                        feedRef.getName(),
                        getView().getType().getValue(),
                        effectiveMs,
                        getView().getMetaData(),
                        fileName);

                restFactory
                        .create(DATA_RESOURCE)
                        .method(res -> res.upload(request))
                        .onSuccess(result ->
                                AlertEvent.fireInfo(DataUploadPresenter.this.metaPresenter, "Uploaded file",
                                        () -> {
                                            metaPresenter.refresh();
                                            currentHideRequest.hide();
                                        }))
                        .onFailure(throwable -> error("Error uploading file: " + throwable.getMessage()))
                        .taskMonitorFactory(DataUploadPresenter.this)
                        .exec();
            }

            @Override
            protected void onFailure(final String message) {
                error(message);
            }
        };

        registerHandler(getView().getFileUpload().addSubmitHandler(submitCompleteHandler));
        registerHandler(getView().getFileUpload().addSubmitCompleteHandler(submitCompleteHandler));
    }

    public boolean valid() {
        if (feedRef == null) {
            AlertEvent.fireWarn(this, "Feed not set!", null);
            return false;
        }
        if (getView().getType().getValue() == null) {
            AlertEvent.fireWarn(this, "Stream Type not set!", null);
            return false;
        }

        final String fileName = getView().getFileUpload().getFilename();
        if (NullSafe.isEmptyString(fileName)) {
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
        getView().getFileUpload().submit();
    }

    public void show(final MetaPresenter streamPresenter, final DocRef feedRef) {
        this.metaPresenter = streamPresenter;
        this.feedRef = feedRef;
        feedClient.load(feedRef,
                this::fireShowPopup,
                throwable -> error("Error fetching feed: " + throwable.getMessage()),
                DataUploadPresenter.this);
    }

    private void fireShowPopup(final FeedDoc feedDoc) {
        final PopupSize popupSize = PopupSize.resizable(430, 480);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Upload")
                .onShow(e -> onShow(feedDoc))
                .onHideRequest(e -> {
                    currentHideRequest = e;
                    if (e.isOk()) {
                        if (valid()) {
//                            // Disable popup buttons as we are submitting.
//                            disableButtons();
                            submit();
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void onShow(final FeedDoc feedDoc) {
        dataTypeUiManager.getTypes(list -> {
            getView().getType().clear();
            if (NullSafe.hasItems(list)) {
                getView().getType().addItems(list);
                String streamType = feedDoc.getStreamType();

                if (streamType == null || !list.contains(streamType)) {
                    // Fallback default value
                    streamType = StreamTypeNames.RAW_EVENTS;
                }
                getView().getType().setValue(streamType);
            }
        }, this);

        getView().focus();
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, currentHideRequest::reset);
    }


    // --------------------------------------------------------------------------------


    public interface DataUploadView extends View, Focus {

        void setType(final String type);

        SelectionBox<String> getType();

        Long getEffectiveDate();

        String getMetaData();

        CustomFileUpload getFileUpload();
    }
}
