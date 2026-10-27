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

package stroom.data.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.DataTypeUiManager;
import stroom.data.shared.DataResource;
import stroom.data.shared.StreamTypeNames;
import stroom.data.shared.UploadDataRequest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.form.client.FileChooserController;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Uploads a file of data to a feed ([DataUpload]), made for one use: it loads the feed, for its
/// stream type, then shows the dialog. OK uploads the file and, once the upload is confirmed,
/// closes.
public class DataUploadController extends Controller {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

    private final DataUpload view;
    private final DataTypeUiManager dataTypeUiManager;
    private final FeedClient feedClient;
    private final RestFactory restFactory;
    private final FileChooserController fileChooser;
    private DocRef feedRef;
    private Runnable onUploaded;
    private HidePopupRequestEvent currentHideRequest;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param restFactory                   Uploads the data.
    /// @param feedClient                    Loads the feed.
    /// @param dataTypeUiManager             Lists the stream types.
    /// @param fileChooser                   The file chooser's controller.
    /// @param dateTimeBoxControllerProvider Makes the effective date box's controller.
    @Inject
    public DataUploadController(final EventBus eventBus,
                                final DataUpload view,
                                final RestFactory restFactory,
                                final FeedClient feedClient,
                                final DataTypeUiManager dataTypeUiManager,
                                final FileChooserController fileChooser,
                                final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.dataTypeUiManager = dataTypeUiManager;
        this.feedClient = feedClient;
        this.restFactory = restFactory;
        this.fileChooser = own(fileChooser);
        make(dateTimeBoxControllerProvider).attach(view.getEffectiveBox());
        fileChooser.attach(view.getFileChooser());
        fileChooser.setTaskMonitorFactory(taskMonitor(), "Uploading Data");
    }

    /// Loads the feed and shows the dialog. The opener owns the dialog until it is hidden; if the
    /// feed can't be loaded, it says why and the dialog isn't shown.
    ///
    /// @param opener     What shows it.
    /// @param feedRef    The feed to upload to.
    /// @param onUploaded Called once the data is uploaded and the user has closed the message
    ///                   saying so.
    public void show(final HasControllerNode opener, final DocRef feedRef, final Runnable onUploaded) {
        this.feedRef = feedRef;
        this.onUploaded = onUploaded;
        feedClient.load(feedRef,
                guard(feedDoc -> showDialog(opener, feedDoc)),
                error -> {
                    // Never shown, so there is no dialog to go back to
                    AlertEvent.fireError(this, "Error fetching feed: " + error.getMessage(), null);
                    dispose();
                },
                taskMonitor());
    }

    private void showDialog(final HasControllerNode opener, final FeedDoc feedDoc) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(430, 480))
                .caption("Upload")
                .onShow(e -> onShow(feedDoc))
                .onHideRequest(e -> {
                    currentHideRequest = e;
                    if (e.isOk()) {
                        if (valid()) {
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
        dataTypeUiManager.getTypes(guard(list -> {
            if (NullSafe.hasItems(list)) {
                String streamType = feedDoc.getStreamType();
                if (streamType == null || !list.contains(streamType)) {
                    // Fallback default value
                    streamType = StreamTypeNames.RAW_EVENTS;
                }
                view.setTypes(list, streamType);
            }
        }), taskMonitor());

        view.focus();
    }

    private boolean valid() {
        if (feedRef == null) {
            AlertEvent.fireWarn(this, "Feed not set!", null);
            return false;
        }
        if (view.getType() == null) {
            AlertEvent.fireWarn(this, "Stream Type not set!", null);
            return false;
        }
        if (NullSafe.isEmptyString(fileChooser.getFilename())) {
            AlertEvent.fireWarn(this, "File not set!", null);
            return false;
        }
        return true;
    }

    private void submit() {
        fileChooser.upload(ImportUtil.getImportFileURL(), this::onUploaded, this::error);
    }

    private void onUploaded(final ResourceKey resourceKey) {
        final UploadDataRequest request = new UploadDataRequest(
                resourceKey,
                feedRef.getName(),
                view.getType(),
                view.getEffectiveDate(),
                view.getMetaData(),
                fileChooser.getFilename());

        restFactory
                .create(DATA_RESOURCE)
                .method(res -> res.upload(request))
                .onSuccess(result ->
                        AlertEvent.fireInfo(this, "Uploaded file", () -> {
                            onUploaded.run();
                            currentHideRequest.hide();
                        }))
                .onFailure(throwable -> error("Error uploading file: " + throwable.getMessage()))
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
    }

    private void error(final String message) {
        AlertEvent.fireError(this, message, currentHideRequest::reset);
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }
}
