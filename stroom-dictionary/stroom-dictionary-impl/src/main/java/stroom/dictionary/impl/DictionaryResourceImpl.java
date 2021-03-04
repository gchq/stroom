/*
 * Copyright 2017 Crown Copyright
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

package stroom.dictionary.impl;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.DictionaryResource;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class DictionaryResourceImpl implements DictionaryResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DictionaryResourceImpl.class);

    private final Provider<DictionaryStore> dictionaryStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    DictionaryResourceImpl(final Provider<DictionaryStore> dictionaryStoreProvider,
                           final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                           final Provider<ResourceStore> resourceStoreProvider,
                           final Provider<SecurityContext> securityContext) {
        this.dictionaryStoreProvider = dictionaryStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    public DictionaryDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(dictionaryStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public DictionaryDoc update(final String uuid, final DictionaryDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(dictionaryStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(DictionaryDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    public ResourceGeneration download(final DocRef dictionaryRef) {
        // Get dictionary.
        final DictionaryDoc dictionary = dictionaryStoreProvider.get().readDocument(dictionaryRef);
        if (dictionary == null) {
            throw new EntityServiceException("Unable to find dictionary");
        }

        final ResourceKey resourceKey = resourceStoreProvider.get().createTempFile("dictionary.txt");
        final Path file = resourceStoreProvider.get().getTempFile(resourceKey);
        try {
            Files.writeString(file, dictionary.getData(), StreamUtil.DEFAULT_CHARSET);
        } catch (final IOException e) {
            LOGGER.error("Unable to download Dictionary", e);
            throw new UncheckedIOException(e);
        }
        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }
}
