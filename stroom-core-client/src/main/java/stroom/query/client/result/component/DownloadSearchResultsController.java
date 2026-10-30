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

package stroom.query.client.result.component;

import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Download Options dialog ([DownloadSearchResults]) of a table's results, kept by its table so
/// that the options chosen are kept between downloads.
public class DownloadSearchResultsController extends Controller {

    private final DownloadSearchResults view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public DownloadSearchResultsController(final EventBus eventBus, final DownloadSearchResults view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the dialog; its owner keeps it.
    ///
    /// @param showDownloadAll Whether Download All Tables is offered, e.g. for a dashboard.
    /// @param onOk            Run on OK, to download with the options chosen.
    public void show(final boolean showDownloadAll, final Runnable onOk) {
        view.setShowDownloadAll(showDownloadAll);
        ControllerDialog.show(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Download Options")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.run();
                    }
                    e.hide();
                })
                .fire();
    }

    /// @return The file type chosen.
    public DownloadSearchResultFileType getFileType() {
        return view.getFileType();
    }

    /// @return Whether all the dashboard's tables go in the file.
    public boolean isDownloadAllTables() {
        return view.downloadAllTables();
    }

    /// @return Whether only a sample of the results goes in the file.
    public boolean isSample() {
        return view.isSample();
    }

    /// @return The percentage of the results in a sample.
    public int getPercent() {
        return view.getPercent();
    }
}
