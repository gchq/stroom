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

package stroom.documentation.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.documentation.shared.DocumentationDoc;
import stroom.documentation.shared.DocumentationResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.resource.api.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@AutoLogged
class DocumentationResourceImpl implements DocumentationResource, FetchWithUuid<DocumentationDoc> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentationResourceImpl.class);

    private final Provider<DocumentationStore> documentationStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    DocumentationResourceImpl(final Provider<DocumentationStore> documentationStoreProvider,
                              final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                              final Provider<ResourceStore> resourceStoreProvider) {
        this.documentationStoreProvider = documentationStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    public DocumentationDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get()
                .read(documentationStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public DocumentationDoc update(final String uuid, final DocumentationDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(documentationStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(DocumentationDoc.TYPE)
                .build();
    }

    @Override
    public ResourceGeneration download(final DocRef dictionaryRef) {
        // Get dictionary.
        final DocumentationDoc documentationDoc = documentationStoreProvider.get().readDocument(dictionaryRef);
        if (documentationDoc == null) {
            throw new EntityServiceException("Unable to find dictionary");
        }

        final ResourceKey resourceKey = resourceStoreProvider.get().createTempFile("dictionary.txt");
        final Path tempFile = resourceStoreProvider.get().getTempFile(resourceKey);
        try {
            Files.writeString(tempFile, documentationDoc.getData(), StreamUtil.DEFAULT_CHARSET);
        } catch (final IOException e) {
            LOGGER.error("Unable to download Dictionary", e);
            throw new UncheckedIOException(e);
        }
        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }
}
