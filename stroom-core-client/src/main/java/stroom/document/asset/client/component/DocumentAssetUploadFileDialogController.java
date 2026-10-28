/*
 * Copyright 2025 Crown Copyright
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

package stroom.document.asset.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.document.asset.client.DocumentAssetAddFileCallback;
import stroom.importexport.client.presenter.ImportUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.form.client.FileChooserController;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Uploads a file into a document's assets ([DocumentAssetUploadFileDialog]) in a dialog made for
/// one use, and gives it to the document under a name that is free.
public class DocumentAssetUploadFileDialogController extends Controller {

    private static final int DIALOG_WIDTH = 430;
    private static final int DIALOG_HEIGHT = 300;

    private final DocumentAssetUploadFileDialog view;
    private final FileChooserController fileChooser;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param fileChooser Uploads the file chosen.
    @Inject
    public DocumentAssetUploadFileDialogController(final EventBus eventBus,
                                                   final DocumentAssetUploadFileDialog view,
                                                   final FileChooserController fileChooser) {
        super(eventBus);
        this.view = view;
        this.fileChooser = own(fileChooser);
        initWidget(view);
        fileChooser.attach(view.getFileChooser());
        // The dialog's task monitor, else Stroom's own busy indicator
        fileChooser.setTaskMonitorFactory(taskMonitor(), "Uploading file");
    }

    /// Asks for a file in a dialog, owned by `opener` until it is closed, and on OK uploads it and
    /// gives it to `addFileCallback`.
    ///
    /// @param opener                     What shows it.
    /// @param addFileCallback            Given the uploaded file.
    /// @param parentFolderItem           The folder the file goes in, or null for the root.
    /// @param path                       The folder's path, shown in the dialog.
    /// @param illegalAssetNameCharacters The characters removed from the file's name.
    public void show(final HasControllerNode opener,
                     final DocumentAssetAddFileCallback addFileCallback,
                     final DocumentAssetTreeItem parentFolderItem,
                     final String path,
                     final String illegalAssetNameCharacters) {
        view.setPath(path);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Add File")
                .modal(true)
                .onHideRequest(e -> {
                    if (!e.isOk()) {
                        e.hide();
                    } else if (NullSafe.isBlankString(fileChooser.getFilename())) {
                        AlertEvent.fireWarn(this, "File not set", null);
                        e.reset();
                    } else {
                        fileChooser.upload(ImportUtil.getImportFileURL(),
                                resourceKey -> {
                                    final String fileName = addFileCallback.getNonClashingLabel(
                                            parentFolderItem,
                                            removeIllegalCharacters(fileChooser.getFilename(),
                                                    illegalAssetNameCharacters),
                                            null);
                                    addFileCallback.addUploadedFile(parentFolderItem, fileName, resourceKey);
                                    e.hide();
                                },
                                message -> AlertEvent.fireError(this, message, e::reset));
                    }
                })
                .fire();
    }

    /// Removes any illegal characters from a file's name. The upload has already removed the path
    /// the browser gives the file, e.g. `C:\fakepath\actual-filename.ext`.
    ///
    /// @param uploadFilename             The name of the uploaded file.
    /// @param illegalAssetNameCharacters The characters to remove, or null for none.
    /// @return The name without them.
    static String removeIllegalCharacters(final String uploadFilename,
                                          final String illegalAssetNameCharacters) {
        String filename = uploadFilename;
        if (illegalAssetNameCharacters != null) {
            for (int i = 0; i < illegalAssetNameCharacters.length(); ++i) {
                final CharSequence c = illegalAssetNameCharacters.subSequence(i, i + 1);
                filename = filename.replace(c, "");
            }
        }
        return filename;
    }
}
