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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapEventStoreResource;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class FloorMapEventStoreResourceImpl
        implements FloorMapEventStoreResource, FetchWithUuid<FloorMapEventStoreDoc> {

    private final Provider<FloorMapEventStoreStore> storeProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    FloorMapEventStoreResourceImpl(
            final Provider<FloorMapEventStoreStore> storeProvider,
            final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.storeProvider = storeProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public FloorMapEventStoreDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(storeProvider.get(), getDocRef(uuid));
    }

    @Override
    public FloorMapEventStoreDoc update(final String uuid, final FloorMapEventStoreDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(storeProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(FloorMapEventStoreDoc.TYPE)
                .build();
    }
}
