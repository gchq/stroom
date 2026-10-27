/*
 * Copyright 2024 Crown Copyright
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

package stroom.security.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A document's Permissions tab ([DocumentUserPermissionsTabController]), for a document still
/// shown by a `DocTabPresenter`.
///
/// @param <E> The document's type.
public class DocumentUserPermissionsTabProvider<E> implements TabProvider<E> {

    private final ControllerTabProvider<DocumentUserPermissionsTabController, E> tab;

    /// Made by GIN.
    ///
    /// @param eventBus            The application's event bus.
    /// @param permissionsProvider Makes the tab's content when it is first shown.
    @Inject
    public DocumentUserPermissionsTabProvider(final EventBus eventBus,
                                              final Provider<DocumentUserPermissionsTabController>
                                                      permissionsProvider) {
        tab = new ControllerTabProvider<>(eventBus,
                permissionsProvider::get,
                (permissions, docRef, document) -> permissions.setDocRef(docRef));
    }

    @Override
    public MyPresenterWidget<?> getPresenter() {
        return tab.getPresenter();
    }

    @Override
    public void read(final DocRef docRef, final E document) {
        tab.read(docRef, document);
    }

    @Override
    public E write(final E document) {
        return tab.write(document);
    }

    @Override
    public void onClose() {
        tab.onClose();
    }
}
