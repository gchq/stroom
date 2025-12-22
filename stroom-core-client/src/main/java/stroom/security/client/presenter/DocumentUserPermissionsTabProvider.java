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

package stroom.security.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.AbstractTabProvider;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class DocumentUserPermissionsTabProvider<E> extends AbstractTabProvider<E, DocumentUserPermissionsPresenter> {

    private final Provider<DocumentUserPermissionsPresenter> documentUserPermissionsPresenterProvider;

    @Inject
    public DocumentUserPermissionsTabProvider(final EventBus eventBus,
                                              final Provider<DocumentUserPermissionsPresenter>
                                                      documentUserPermissionsPresenterProvider) {
        super(eventBus);
        this.documentUserPermissionsPresenterProvider = documentUserPermissionsPresenterProvider;
    }

    @Override
    protected final DocumentUserPermissionsPresenter createPresenter() {
        return documentUserPermissionsPresenterProvider.get();
    }

    @Override
    public void onRead(final DocumentUserPermissionsPresenter presenter,
                       final DocRef docRef,
                       final E document,
                       final boolean readOnly) {
        presenter.setDocRef(docRef);
    }
}
