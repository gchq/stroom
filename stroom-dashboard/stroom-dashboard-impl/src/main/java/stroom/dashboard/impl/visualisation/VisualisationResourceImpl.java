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

package stroom.dashboard.impl.visualisation;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.EntityServiceException;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.visualisation.shared.VisualisationResource;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class VisualisationResourceImpl implements VisualisationResource {

    private final Provider<VisualisationStore> visualisationStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    VisualisationResourceImpl(final Provider<VisualisationStore> visualisationStoreProvider,
                              final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.visualisationStoreProvider = visualisationStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public VisualisationDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(visualisationStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public VisualisationDoc update(final String uuid, final VisualisationDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(visualisationStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(VisualisationDoc.TYPE)
                .build();
    }

}
