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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapResource;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

/// REST resource implementation for [FloorMapDoc] CRUD operations.
///
/// Delegates document read and update operations to [FloorMapStore]
/// via [DocumentResourceHelper] for consistent permission checking
/// and event logging.
@AutoLogged
class FloorMapResourceImpl implements FloorMapResource {

    private final Provider<FloorMapStore> floorMapStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    FloorMapResourceImpl(final Provider<FloorMapStore> floorMapStoreProvider,
                         final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.floorMapStoreProvider = floorMapStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    /// Fetches a floor map document by its UUID.
    ///
    /// @param uuid the UUID of the floor map document to fetch
    /// @return the floor map document
    @Override
    public FloorMapDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(floorMapStoreProvider.get(), getDocRef(uuid));
    }

    /// Updates an existing floor map document.
    ///
    /// @param uuid the UUID of the floor map document to update
    /// @param doc  the updated floor map document
    /// @return the updated floor map document after persistence
    /// @throws EntityServiceException if the document UUID does not match the path UUID
    @Override
    public FloorMapDoc update(final String uuid, final FloorMapDoc doc) {
        checkUuidsMatch(uuid, doc);
        return documentResourceHelperProvider.get().update(floorMapStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(FloorMapDoc.TYPE)
                .build();
    }

    private void checkUuidsMatch(final String uuid, final FloorMapDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
    }
}
