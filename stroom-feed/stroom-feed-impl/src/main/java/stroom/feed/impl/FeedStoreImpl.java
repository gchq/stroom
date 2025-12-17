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
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class FeedStoreImpl implements FeedStore {

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

    private final Store<FeedDoc> store;
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
        this.fsVolumeGroupServiceProvider = fsVolumeGroupServiceProvider;
        this.store = storeFactory.createStore(serialiser, FeedDoc.TYPE, FeedDoc::builder);
        this.feedNameValidator = feedNameValidator;
        this.securityContext = securityContext;
        this.serialiser = serialiser;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        feedNameValidator.validateName(name);

        // Check a feed doesn't already exist with this name.
        if (checkDuplicateName(name, null)) {
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        final DocRef created = store.createDocument(name);

        // Double-check the feed wasn't created elsewhere at the same time.
        if (checkDuplicateName(name, created)) {
            // Delete the newly created document as the name is duplicated.

            // Delete as a processing user to ensure we are allowed to delete the item as documents do not have
            // permissions added to them until after they are created in the store.
            securityContext.asProcessingUser(() -> store.deleteDocument(created));
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
        return store.copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        feedNameValidator.validateName(name);

        // Check a feed doesn't already exist with this name.
        if (checkDuplicateName(name, docRef)) {
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        store.deleteDocument(docRef);
    }

    @Override
    public DocRefInfo info(final DocRef docRef) {
        return store.info(docRef);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Map<DocRef, Set<DocRef>> getDependencies() {
        return store.getDependencies(null);
    }

    @Override
    public Set<DocRef> getDependencies(final DocRef docRef) {
        return store.getDependencies(docRef, null);
    }

    @Override
    public void remapDependencies(final DocRef docRef,
                                  final Map<DocRef, DocRef> remappings) {
        store.remapDependencies(docRef, remappings, null);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF HasDependencies
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public FeedDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public FeedDoc writeDocument(final FeedDoc document) {
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////
    // START OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public Set<DocRef> listDocuments() {
        return store.listDocuments();
    }

    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final Map<String, byte[]> dataMap,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        DocRef newDocRef = docRef;

        if (ImportState.State.NEW.equals(importState.getState())) {
            final String newName = createUniqueName(docRef.getName());
            newDocRef = new DocRef(docRef.getType(), docRef.getUuid(), newName);
        }

        // If the imported feed's vol grp doesn't exist in this env use our default
        // or null it out
        Map<String, byte[]> effectiveDataMap = dataMap;
        try {
            final FeedDoc feedDoc = serialiser.read(dataMap);

            final String volumeGroup = feedDoc.getVolumeGroup();
            if (volumeGroup != null) {
                final FsVolumeGroupService fsVolumeGroupService = fsVolumeGroupServiceProvider.get();
                final List<String> allVolumeGroups = fsVolumeGroupService.getNames();
                if (!allVolumeGroups.contains(volumeGroup)) {
                    LOGGER.debug("Volume group '{}' in imported feed {} is not a valid volume group",
                            volumeGroup, docRef);
                    fsVolumeGroupService.getDefaultVolumeGroup()
                            .ifPresentOrElse(
                                    feedDoc::setVolumeGroup,
                                    () -> feedDoc.setVolumeGroup(null));

                    effectiveDataMap = serialiser.write(feedDoc);
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error de-serialising feed {}: {}",
                    docRef, e.getMessage()), e);
        }

        return store.importDocument(newDocRef, effectiveDataMap, importState, importSettings);
    }

    @Override
    public Map<String, byte[]> exportDocument(final DocRef docRef,
                                              final boolean omitAuditFields,
                                              final List<Message> messageList) {
        if (omitAuditFields) {
            return store.exportDocument(docRef, messageList, new AuditFieldFilter<>());
        }
        return store.exportDocument(docRef, messageList, d -> d);
    }

    @Override
    public String getType() {
        return store.getType();
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(final DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
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
