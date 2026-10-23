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
import stroom.entity.client.presenter.CopyDocumentPresenter;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.entity.client.presenter.MoveDocumentPresenter;
import stroom.entity.client.presenter.NameDocumentPresenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The document dialogs (create, copy, move, rename, info), made when first asked for. Hears the
/// app-wide events that open them, for the life of the app.
public class DocumentDialogScreens {

    /// Adds the handlers of the events that open the screens.
    ///
    /// @param eventBus The application's event bus.
    /// @param copyDocumentPresenterProvider Makes the CopyDocumentPresenter.
    /// @param createDocumentPresenterProvider Makes the CreateDocumentPresenter.
    /// @param infoDocumentPresenterProvider Makes the InfoDocumentPresenter.
    /// @param moveDocumentPresenterProvider Makes the MoveDocumentPresenter.
    /// @param nameDocumentPresenterProvider Makes the NameDocumentPresenter.
    @Inject
    public DocumentDialogScreens(final EventBus eventBus,
                                 final Provider<CopyDocumentPresenter> copyDocumentPresenterProvider,
                                 final Provider<CreateDocumentPresenter> createDocumentPresenterProvider,
                                 final Provider<InfoDocumentPresenter> infoDocumentPresenterProvider,
                                 final Provider<MoveDocumentPresenter> moveDocumentPresenterProvider,
                                 final Provider<NameDocumentPresenter> nameDocumentPresenterProvider) {
        eventBus.addHandler(ShowCopyDocumentDialogEvent.getType(), event ->
                copyDocumentPresenterProvider.get().onCopy(event));
        eventBus.addHandler(ShowCreateDocumentDialogEvent.getType(), event ->
                createDocumentPresenterProvider.get().onCreate(event));
        eventBus.addHandler(ShowInfoDocumentDialogEvent.getType(), event ->
                infoDocumentPresenterProvider.get().onCreate(event));
        eventBus.addHandler(ShowMoveDocumentDialogEvent.getType(), event ->
                moveDocumentPresenterProvider.get().onMove(event));
        eventBus.addHandler(ShowRenameDocumentDialogEvent.getType(), event ->
                nameDocumentPresenterProvider.get().onRename(event));
    }
}
