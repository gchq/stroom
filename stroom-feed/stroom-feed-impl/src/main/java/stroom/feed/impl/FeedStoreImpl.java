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

package stroom.feed.impl;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class FeedStoreImpl
        extends AbstractDocumentStore<FeedDoc>
        implements FeedStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedStoreImpl.class);

    private static final List<String> SUPPORTED_ENCODINGS;

    static {
        final List<String> list = new ArrayList<>();
        list.add("UTF-8");
        list.add("UTF-16LE");
        list.add("UTF-16BE");
        list.add("UTF-32LE");
        list.add("UTF-32BE");
        list.add("ASCII");
        list.addAll(Charset.availableCharsets().keySet());
        SUPPORTED_ENCODINGS = Collections.unmodifiableList(list);
    }

    private final FeedNameValidator feedNameValidator;
    private final SecurityContext securityContext;
    private final FeedSerialiser serialiser;
    private final Provider<FsVolumeGroupService> fsVolumeGroupServiceProvider;

    @Inject
    public FeedStoreImpl(final StoreFactory storeFactory,
                         final FeedNameValidator feedNameValidator,
                         final FeedSerialiser serialiser,
                         final SecurityContext securityContext,
                         final Provider<FsVolumeGroupService> fsVolumeGroupServiceProvider) {
        super(storeFactory,
                serialiser,
                FeedDoc.TYPE,
                FeedDoc::builder,
                FeedDoc::copy);
        this.feedNameValidator = feedNameValidator;
        this.securityContext = securityContext;
        this.serialiser = serialiser;
        this.fsVolumeGroupServiceProvider = fsVolumeGroupServiceProvider;
    }

    @Override
    public DocRef createDocument(final String name) {
        feedNameValidator.validateName(name);

        // Check a feed doesn't already exist with this name.
        if (checkDuplicateName(name, null)) {
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        final DocRef created = getStore().createDocument(name);

        // Double-check the feed wasn't created elsewhere at the same time.
        if (checkDuplicateName(name, created)) {
            // Delete the newly created document as the name is duplicated.

            // Delete as a processing user to ensure we are allowed to delete the item as documents do not have
            // permissions added to them until after they are created in the store.
            securityContext.asProcessingUser(() -> getStore().deleteDocument(created));
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        } else {
            return created;
        }
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        // Check a feed doesn't already exist with this name.
        if (!makeNameUnique && checkDuplicateName(name, null)) {
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        final String newName = createUniqueName(name);
        return getStore().copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        feedNameValidator.validateName(name);

        // Check a feed doesn't already exist with this name.
        if (checkDuplicateName(name, docRef)) {
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        return super.renameDocument(docRef, name);
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        DocRef newDocRef = docRef;

        if (ImportState.State.NEW.equals(importState.getState())) {
            final String newName = createUniqueName(docRef.getName());
            newDocRef = new DocRef(docRef.getType(), docRef.getUuid(), newName);
        }

        // If the imported feed's vol grp doesn't exist in this env use our default
        // or null it out
        ImportExportDocument effectiveDocument = importExportDocument;
        try {
            final FeedDoc feedDoc = serialiser.read(importExportDocument);

            final String volumeGroup = feedDoc.getVolumeGroup();
            if (volumeGroup != null) {
                final FsVolumeGroupService fsVolumeGroupService = fsVolumeGroupServiceProvider.get();
                final List<String> allVolumeGroups = fsVolumeGroupService.getNames();
                if (!allVolumeGroups.contains(volumeGroup)) {
                    LOGGER.debug("Volume group '{}' in imported feed {} is not a valid volume group",
                            volumeGroup, docRef);
                    final FeedDoc.Builder builder = feedDoc.copy();
                    fsVolumeGroupService.getDefaultVolumeGroup()
                            .ifPresentOrElse(
                                    builder::volumeGroup,
                                    () -> builder.volumeGroup(null));

                    effectiveDocument = serialiser.write(builder.build());
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising feed {}: {}",
                    docRef, e.getMessage()), e);
        }

        return getStore().importDocument(newDocRef, effectiveDocument, importState, importSettings);
    }

    @Override
    public List<String> fetchSupportedEncodings() {
        return SUPPORTED_ENCODINGS;
    }

    private String createUniqueName(final String name) {
        final Set<String> existingNames = list()
                .stream()
                .map(DocRef::getName)
                .collect(Collectors.toSet());

        return UniqueNameUtil.getCopyName(name, existingNames, "COPY", "_");
    }

    private boolean checkDuplicateName(final String name, final DocRef whitelistUuid) {
        final List<DocRef> list = list();
        for (final DocRef docRef : list) {
            if (name.equals(docRef.getName()) &&
                (whitelistUuid == null || !whitelistUuid.equals(docRef))) {
                return true;
            }
        }
        return false;
    }
}
