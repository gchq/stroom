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
import stroom.datagen.shared.DataGenDoc.Builder;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Set;

@Singleton
class DataGenStoreImpl
        extends AbstractDocumentStore<DataGenDoc>
        implements DataGenStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataGenStoreImpl.class);

    private final SecurityContext securityContext;
    private final Provider<DataGenProcessors> dataGenProcessorsProvider;

    @Inject
    DataGenStoreImpl(final StoreFactory storeFactory,
                     final DataGenSerialiser serialiser,
                     final SecurityContext securityContext,
                     final Provider<DataGenProcessors> dataGenProcessorsProvider) {
        super(storeFactory,
                serialiser,
                DataGenDoc.TYPE,
                DataGenDoc::builder,
                DataGenDoc::copy);
        this.securityContext = securityContext;
        this.dataGenProcessorsProvider = dataGenProcessorsProvider;
    }

    @Override
    public DocRef createDocument(final String name) {
        final DocRef docRef = getStore().createDocument(name);

        // Read and write as a processing user to ensure we are allowed as documents do not have permissions added to
        // them until after they are created in the store.
        securityContext.asProcessingUser(() -> {
            final DataGenDoc dataGenDoc = getStore().readDocument(docRef);
            getStore().writeDocument(dataGenDoc);
        });
        return docRef;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        final String newName = UniqueNameUtil.getCopyName(name, makeNameUnique, existingNames);
        final DataGenDoc document = getStore().readDocument(docRef);
        return getStore().createDocument(newName,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) -> {
                    final Builder builder = document
                            .copy()
                            .uuid(uuid)
                            .name(docName)
                            .version(version)
                            .createTimeMs(createTime)
                            .updateTimeMs(updateTime)
                            .createUser(createUser)
                            .updateUser(updateUser);

                    return builder.build();
                });
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        deleteProcessorFilter(docRef);
        super.deleteDocument(docRef);
    }

    private void deleteProcessorFilter(final DocRef docRef) {
        try {
            final DataGenDoc dataGenDoc = readDocument(docRef);
            dataGenProcessorsProvider.get().deleteProcessorFilters(dataGenDoc);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }
}
