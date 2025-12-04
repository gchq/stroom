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

package stroom.pipeline.textconverter;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterResource;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class TextConverterResourceImpl implements TextConverterResource {

    private final Provider<TextConverterStore> textConverterStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    TextConverterResourceImpl(final Provider<TextConverterStore> textConverterStoreProvider,
                              final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.textConverterStoreProvider = textConverterStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public TextConverterDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(textConverterStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public TextConverterDoc update(final String uuid, final TextConverterDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(textConverterStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(TextConverterDoc.TYPE)
                .build();
    }
}
