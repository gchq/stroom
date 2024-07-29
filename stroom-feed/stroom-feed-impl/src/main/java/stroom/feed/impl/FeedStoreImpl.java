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
 *
 */

package stroom.feed.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.api.UniqueNameUtil;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.DocumentTypeGroup;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class FeedStoreImpl implements FeedStore {

    public static final DocumentType DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.DATA_PROCESSING,
            FeedDoc.DOCUMENT_TYPE,
            FeedDoc.DOCUMENT_TYPE,
            FeedDoc.ICON);
    private final Store<FeedDoc> store;
    private final FeedNameValidator feedNameValidator;
    private final SecurityContext securityContext;

    @Inject
    public FeedStoreImpl(final StoreFactory storeFactory,
                         final FeedNameValidator feedNameValidator,
                         final FeedSerialiser serialiser,
                         final SecurityContext securityContext) {
        this.store = storeFactory.createStore(serialiser, FeedDoc.DOCUMENT_TYPE, FeedDoc.class);
        this.feedNameValidator = feedNameValidator;
        this.securityContext = securityContext;
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

        // Double check the feed wasn't created elsewhere at the same time.
        if (checkDuplicateName(name, created.getUuid())) {
            // Delete the newly created document as the name is duplicated.

            // Delete as a processing user to ensure we are allowed to delete the item as documents do not have
            // permissions added to them until after they are created in the store.
            securityContext.asProcessingUser(() -> store.deleteDocument(created.getUuid()));
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        return created;
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
    public DocRef moveDocument(final String uuid) {
        return store.moveDocument(uuid);
    }

    @Override
    public DocRef renameDocument(final String uuid, final String name) {
        feedNameValidator.validateName(name);

        // Check a feed doesn't already exist with this name.
        if (checkDuplicateName(name, uuid)) {
            throw new EntityServiceException("A feed named '" + name + "' already exists");
        }

        return store.renameDocument(uuid, name);
    }

    @Override
    public void deleteDocument(final String uuid) {
        store.deleteDocument(uuid);
    }

    @Override
    public DocRefInfo info(String uuid) {
        return store.info(uuid);
    }

    @Override
    public DocumentType getDocumentType() {
        return DOCUMENT_TYPE;
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

        return store.importDocument(newDocRef, dataMap, importState, importSettings);
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
        return FeedDoc.DOCUMENT_TYPE;
    }

    @Override
    public Set<DocRef> findAssociatedNonExplorerDocRefs(DocRef docRef) {
        return null;
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF ImportExportActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public List<DocRef> findByNames(final List<String> names,
                                    final boolean allowWildCards,
                                    final boolean isCaseSensitive) {
        return store.findByNames(names, allowWildCards, isCaseSensitive);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }

    @Override
    public List<DocRef> list() {
        return store.list();
    }

    private String createUniqueName(final String name) {
        final Set<String> existingNames = list()
                .stream()
                .map(DocRef::getName)
                .collect(Collectors.toSet());

        return UniqueNameUtil.getCopyName(name, existingNames, "COPY", "_");
    }

    private boolean checkDuplicateName(final String name, final String whitelistUuid) {
        final List<DocRef> list = list();
        for (final DocRef docRef : list) {
            if (name.equals(docRef.getName()) &&
                    (whitelistUuid == null || !whitelistUuid.equals(docRef.getUuid()))) {
                return true;
            }
        }
        return false;
    }
}
