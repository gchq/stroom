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

package stroom.view.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;
import stroom.view.shared.ViewResource;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class ViewResourceImpl implements ViewResource {

    private final Provider<ViewStore> viewStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    ViewResourceImpl(final Provider<ViewStore> viewStoreProvider,
                     final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                     final Provider<SecurityContext> securityContextProvider) {
        this.viewStoreProvider = viewStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.securityContextProvider = securityContextProvider;
    }

    @Override
    public ViewDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(viewStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public ViewDoc update(final String uuid, final ViewDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(viewStoreProvider.get(), doc);
    }

    @Override
    public List<DocRef> list() {
        return securityContextProvider.get().useAsReadResult(() ->
                viewStoreProvider.get()
                        .list()
                        .stream()
                        .toList());
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(ViewDoc.TYPE)
                .build();
    }
}
