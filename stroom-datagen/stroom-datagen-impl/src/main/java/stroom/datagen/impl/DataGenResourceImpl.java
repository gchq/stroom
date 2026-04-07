/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.datagen.impl;

import stroom.datagen.shared.DataGenDoc;
import stroom.datagen.shared.DataGenResource;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class DataGenResourceImpl implements DataGenResource {

    private final Provider<DataGenStore> dataGenStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    DataGenResourceImpl(final Provider<DataGenStore> dataGenStoreProvider,
                        final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.dataGenStoreProvider = dataGenStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public DataGenDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(dataGenStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public DataGenDoc update(final String uuid, final DataGenDoc doc) {
        checkUuidsMatch(uuid, doc);
        return documentResourceHelperProvider.get().update(dataGenStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(DataGenDoc.TYPE)
                .build();
    }

    private void checkUuidsMatch(final String uuid, final DataGenDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
    }
}
