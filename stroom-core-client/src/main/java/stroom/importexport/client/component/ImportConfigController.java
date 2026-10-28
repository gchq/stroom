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

package stroom.importexport.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.importexport.client.event.ImportConfigConfirmEvent;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.importexport.shared.ContentResource;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportSettings;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceKey;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.form.client.FileChooserController;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;

/// The Import dialog ([ImportConfig]), made for one use: uploads the export file chosen, and asks
/// for the Confirm Import dialog for what it holds.
public class ImportConfigController extends Controller {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final ImportConfig view;
    private final RestFactory restFactory;
    private final FileChooserController fileChooser;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Reads what the uploaded file holds.
    /// @param fileChooser Uploads the file chosen.
    @Inject
    public ImportConfigController(final EventBus eventBus,
                                  final ImportConfig view,
                                  final RestFactory restFactory,
                                  final FileChooserController fileChooser) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.fileChooser = own(fileChooser);
        initWidget(view);
        fileChooser.attach(view.getFileChooser());
        fileChooser.setTaskMonitorFactory(() -> taskMonitor().createTaskMonitor(), "Import");
    }

    /// Shows the dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    public void show(final HasControllerNode opener) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizableX())
                .caption("Import")
                .onShow(e -> view.focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e) {
        if (!e.isOk()) {
            e.hide();
        } else if (NullSafe.isNonBlankString(fileChooser.getFilename())) {
            fileChooser.upload(ImportUtil.getImportFileURL(),
                    resourceKey -> onUploaded(resourceKey, e),
                    message -> AlertEvent.fireError(this, message, e::reset));
        } else {
            AlertEvent.fireError(this, "You must select a file to import.", e::reset);
            e.reset();
        }
    }

    private void onUploaded(final ResourceKey resourceKey, final HidePopupRequestEvent e) {
        restFactory
                .create(CONTENT_RESOURCE)
                .method(res -> res.importContent(new ImportConfigRequest(resourceKey,
                        ImportSettings.createConfirmation(),
                        new ArrayList<>())))
                .onSuccess(response -> {
                    if (response.getConfirmList().isEmpty()) {
                        AlertEvent.fireWarn(this,
                                "The import package contains nothing that can be imported into " +
                                "this version of Stroom.",
                                e::reset);
                    } else {
                        e.hide();
                        ImportConfigConfirmEvent.fire(this, response);
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(this, caught.getMessage(), e::reset))
                .owner(this)
                .exec();
    }

}
