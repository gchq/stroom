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

package stroom.entity.client;

import stroom.document.client.event.ShowCopyDocumentDialogEvent;
import stroom.document.client.event.ShowCreateDocumentDialogEvent;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.document.client.event.ShowMoveDocumentDialogEvent;
import stroom.document.client.event.ShowRenameDocumentDialogEvent;
import stroom.entity.client.component.InfoDocumentController;
import stroom.entity.client.component.RenameDocumentController;
import stroom.entity.client.presenter.CopyDocumentPresenter;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.entity.client.presenter.MoveDocumentPresenter;
import stroom.explorer.shared.ExplorerNode;
import stroom.widget.component.client.AppRoot;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// The document dialogs (create, copy, move, rename, info), made when first asked for. Hears the
/// app-wide events that open them, for the life of the app.
public class DocumentDialogScreens {

    private InfoDocumentController shownInfo;

    /// Adds the handlers of the events that open the screens.
    ///
    /// @param eventBus The application's event bus.
    /// @param copyDocumentPresenterProvider Makes the CopyDocumentPresenter.
    /// @param createDocumentPresenterProvider Makes the CreateDocumentPresenter.
    /// @param infoDocumentControllerProvider Makes a document's Info dialog.
    /// @param moveDocumentPresenterProvider Makes the MoveDocumentPresenter.
    /// @param renameDocumentControllerProvider Makes a document's Rename dialog.
    /// @param appRoot The app, which owns each Rename and Info dialog while it is shown.
    @Inject
    public DocumentDialogScreens(final EventBus eventBus,
                                 final Provider<CopyDocumentPresenter> copyDocumentPresenterProvider,
                                 final Provider<CreateDocumentPresenter> createDocumentPresenterProvider,
                                 final Provider<InfoDocumentController> infoDocumentControllerProvider,
                                 final Provider<MoveDocumentPresenter> moveDocumentPresenterProvider,
                                 final Provider<RenameDocumentController> renameDocumentControllerProvider,
                                 final AppRoot appRoot) {
        eventBus.addHandler(ShowCopyDocumentDialogEvent.getType(), event ->
                copyDocumentPresenterProvider.get().onCopy(event));
        eventBus.addHandler(ShowCreateDocumentDialogEvent.getType(), event ->
                createDocumentPresenterProvider.get().onCreate(event));
        // The Info dialog isn't modal: Info on another document while it is shown shows that one
        eventBus.addHandler(ShowInfoDocumentDialogEvent.getType(), event -> {
            if (shownInfo != null && !shownInfo.isDisposed()) {
                shownInfo.read(event);
            } else {
                shownInfo = infoDocumentControllerProvider.get();
                shownInfo.show(appRoot, event);
            }
        });
        eventBus.addHandler(ShowMoveDocumentDialogEvent.getType(), event ->
                moveDocumentPresenterProvider.get().onMove(event));
        eventBus.addHandler(ShowRenameDocumentDialogEvent.getType(), event ->
                renameEach(new ArrayList<>(event.getExplorerNodeList()), renameDocumentControllerProvider, appRoot));
    }

    // Renames the documents one after another, each in a dialog of its own
    private static void renameEach(final List<ExplorerNode> documents,
                                   final Provider<RenameDocumentController> renameDocumentControllerProvider,
                                   final AppRoot appRoot) {
        if (!documents.isEmpty()) {
            final ExplorerNode document = documents.remove(0);
            renameDocumentControllerProvider.get().rename(appRoot, document, () ->
                    renameEach(documents, renameDocumentControllerProvider, appRoot));
        }
    }
}
