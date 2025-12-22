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

package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.aws.s3.shared.S3ConfigResource;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
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
public class S3ConfigResourceImpl implements S3ConfigResource, FetchWithUuid<S3ConfigDoc> {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ConfigResourceImpl.class);

    private final Provider<S3ConfigStore> s3ConfigStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    S3ConfigResourceImpl(final Provider<S3ConfigStore> s3ConfigStoreProvider,
                         final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                         final Provider<ResourceStore> resourceStoreProvider) {
        this.s3ConfigStoreProvider = s3ConfigStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    public S3ConfigDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(s3ConfigStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public S3ConfigDoc update(final String uuid, final S3ConfigDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(s3ConfigStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(S3ConfigDoc.TYPE)
                .build();
    }

    @Override
    public ResourceGeneration download(final DocRef s3ConfigDocRef) {
        final S3ConfigDoc s3ConfigDoc = s3ConfigStoreProvider.get().readDocument(s3ConfigDocRef);
        if (s3ConfigDoc == null) {
            throw new EntityServiceException("Unable to find s3 config");
        }

        final ResourceKey resourceKey = resourceStoreProvider.get().createTempFile("s3Config.properties");
        final Path tempFile = resourceStoreProvider.get().getTempFile(resourceKey);
        try {
            Files.writeString(tempFile, s3ConfigDoc.getData(), StreamUtil.DEFAULT_CHARSET);
        } catch (final IOException e) {
            LOGGER.error("Unable to download S3Config", e);
            throw new UncheckedIOException(e);
        }

        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }
}
