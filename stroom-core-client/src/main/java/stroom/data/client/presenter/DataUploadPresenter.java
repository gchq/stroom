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

package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.view.FileData;
import stroom.data.client.view.FileData.Status;
import stroom.data.shared.DataResource;
import stroom.data.shared.StreamTypeNames;
import stroom.data.shared.UploadDataRequest;
import stroom.data.shared.UploadFeedSource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.item.client.SelectionBox;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.form.client.CustomFileUpload;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public class DataUploadPresenter
        extends MyPresenterWidget<DataUploadPresenter.DataUploadView> {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

    private final DataTypeUiManager dataTypeUiManager;
    private final FeedClient feedClient;
    private final DocSelectionBoxPresenter feedSelectionPresenter;
    private final List<String> streamTypes = new ArrayList<>();
    private DocRef initialFeedRef;
    private Runnable onUploaded;
    private HidePopupRequestEvent currentHideRequest;

    @Inject
    public DataUploadPresenter(final EventBus eventBus,
                               final DataUploadView view,
                               final RestFactory restFactory,
                               final FeedClient feedClient,
                               final DataTypeUiManager dataTypeUiManager,
                               final DocSelectionBoxPresenter feedSelectionPresenter) {
        super(eventBus, view);
        this.dataTypeUiManager = dataTypeUiManager;
        this.feedClient = feedClient;
        this.feedSelectionPresenter = feedSelectionPresenter;

        feedSelectionPresenter.setIncludedTypes(FeedDoc.TYPE);
        feedSelectionPresenter.setRequiredPermissions(DocumentPermission.EDIT);
        view.setFeedView(feedSelectionPresenter.getView());

        view.getFileUpload().setAction(ImportUtil.getImportFileURL());

        view.getFileUpload()
                .onSuccess(resourceKey -> {
                    final String fileName = getView().getFileUpload().getFilename();
                    final Long effectiveMs = getView().getEffectiveDate();
                    final UploadDataRequest request = new UploadDataRequest(
                            resourceKey,
                            feedSelectionPresenter.getSelectedEntityReference().getName(),
                            getView().getType().getValue(),
                            effectiveMs,
                            getView().getMetaData(),
                            fileName,
                            getView().getFeedSource());

                    //noinspection unused
                    restFactory
                            .create(DATA_RESOURCE)
                            .method(res -> res.upload(request))
                            .onSuccess(result ->
                                    AlertEvent.fireInfo(DataUploadPresenter.this, "Uploaded file",
                                            () -> {
                                                NullSafe.run(onUploaded);
                                                currentHideRequest.hide();
                                            }))
                            .onFailure(throwable -> error("Error uploading file: " + throwable.getMessage()))
                            .taskMonitorFactory(DataUploadPresenter.this)
                            .exec();
                })
                .onFailure(this::error)
                .taskMonitorFactory(this, "Uploading Data");
    }

    @Override
    protected void onBind() {
        super.onBind();
        // Default the stream type to the one set on the feed the user picks.
        registerHandler(feedSelectionPresenter.addDataSelectionHandler(event ->
                setTypeFromFeed(event.getSelectedItem())));
    }

    /// Checks the dialog has everything needed to upload, showing a warning if not.
    ///
    /// @return True if the upload can go ahead.
    public boolean valid() {
        if (feedSelectionPresenter.getSelectedEntityReference() == null) {
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

    /// Shows the upload dialog.
    ///
    /// @param feedRef    The feed to select when the dialog opens, or null to let the user
    ///                   pick one, e.g. when uploading from the main menu.
    /// @param onUploaded Called after a successful upload, or null if nothing needs to happen.
    public void show(final DocRef feedRef, final Runnable onUploaded) {
        this.initialFeedRef = feedRef;
        this.onUploaded = onUploaded;
        feedSelectionPresenter.setSelectedEntityReference(feedRef, true);
        fireShowPopup();
    }

    private void fireShowPopup() {
        final PopupSize popupSize = PopupSize.resizable(500, 620);

        //noinspection unused
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Upload Data")
                .onShow(e -> onShow())
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

    private void onShow() {
        dataTypeUiManager.getTypes(list -> {
            streamTypes.clear();
            getView().getType().clear();
            if (NullSafe.hasItems(list)) {
                streamTypes.addAll(list);
                getView().getType().addItems(list);
                getView().getType().setValue(StreamTypeNames.RAW_EVENTS);
                setTypeFromFeed(initialFeedRef);
            }
        }, this);

        if (initialFeedRef == null) {
            feedSelectionPresenter.focus();
        } else {
            getView().focus();
        }
    }

    private void setTypeFromFeed(final DocRef feedRef) {
        if (feedRef != null) {
            feedClient.load(feedRef,
                    feedDoc -> {
                        String streamType = feedDoc.getStreamType();
                        if (streamType == null || !streamTypes.contains(streamType)) {
                            // Fallback default value
                            streamType = StreamTypeNames.RAW_EVENTS;
                        }
                        getView().getType().setValue(streamType);
                    },
                    throwable -> AlertEvent.fireError(
                            this,
                            "Error fetching feed: " + throwable.getMessage(),
                            null),
                    this);
        }
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

        /// Sets the view of the feed picker.
        ///
        /// @param view The feed picker's view.
        void setFeedView(View view);

        /// @return Whether every stream goes into the selected feed, or into the feed named in
        /// its own `.meta`/`.manifest` entry.
        UploadFeedSource getFeedSource();
    }
}
