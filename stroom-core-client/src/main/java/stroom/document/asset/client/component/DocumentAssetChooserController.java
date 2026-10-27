/*
 * Copyright 2026 Crown Copyright
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

import stroom.dispatch.client.RestFactory;
import stroom.docstore.shared.AbstractDoc;
import stroom.document.asset.client.presenter.DocumentAssetPresenterUtils;
import stroom.document.asset.client.presenter.assets.DocumentAssetTreeItem;
import stroom.document.asset.shared.DocumentAssetResource;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// Shows the "Choose Asset" dialog ([DocumentAssetChooser]) for one use, e.g. for an image picker: a
/// document's assets, of which a file is chosen. OK is enabled only while a file is chosen.
///
/// ```java
/// chooserProvider.get().choose(this, document, url -> ...);
/// ```
public class DocumentAssetChooserController extends Controller {

    private static final DocumentAssetResource DOCUMENT_ASSET_RESOURCE = GWT.create(DocumentAssetResource.class);
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 450;

    private final DocumentAssetChooser view;
    private final RestFactory restFactory;
    private AbstractDoc document;
    private DocumentAssetTreeItem selectedItem;

    /// Made by GIN for one use, with the dialog's content.
    ///
    /// @param eventBus    The application's event bus, which the dialog is shown on.
    /// @param view        The dialog's content.
    /// @param restFactory Fetches the document's assets.
    @Inject
    public DocumentAssetChooserController(final EventBus eventBus,
                                          final DocumentAssetChooser view,
                                          final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        // A file chosen can be OK'd; a folder can't
        view.setOnSelect(item -> {
            selectedItem = item != null && item.isLeaf()
                    ? item
                    : null;
            ControllerDialog.setActionEnabled(this, DialogAction.OK, selectedItem != null);
        });
        // Enter on a file, or a double click on one, chooses it, as OK does
        view.setOnOpen(item -> ControllerDialog.pressAction(this, DialogAction.OK));
    }

    /// Shows the dialog with a document's assets, owned by `opener` until it is closed. OK gives the
    /// chosen file's URL to `onOk`.
    ///
    /// @param opener   What shows it.
    /// @param document The document whose assets are shown.
    /// @param onOk     Given the URL of the file chosen when OK is pressed.
    public void choose(final HasControllerNode opener, final AbstractDoc document, final Consumer<String> onOk) {
        this.document = Objects.requireNonNull(document, "document");
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption("Choose Asset")
                .modal(true)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(getSelectedAssetUrl());
                    }
                    e.hide();
                })
                .fire();
        // Nothing is chosen until the assets are shown
        ControllerDialog.setActionEnabled(this, DialogAction.OK, false);
        fetchDraftAssets();
    }

    private void fetchDraftAssets() {
        final String ownerId = document.getUuid();
        restFactory.create(DOCUMENT_ASSET_RESOURCE)
                .method(r -> r.fetchDraftAssets(ownerId))
                .onSuccess(assets -> {
                    view.setAssets(assets.getAssets());
                    selectedItem = null;
                    ControllerDialog.setActionEnabled(this, DialogAction.OK, false);
                })
                .owner(this)
                .taskMonitorFactory(getProvidedTaskMonitor())
                .exec();
    }

    private String getSelectedAssetUrl() {
        if (selectedItem != null && document != null) {
            return DocumentAssetsTabController.ASSET_SERVLET_PATH_PREFIX
                   + document.getUuid()
                   + DocumentAssetPresenterUtils.getItemPath(selectedItem);
        }
        return null;
    }
}
