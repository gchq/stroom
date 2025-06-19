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

package stroom.state.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.AuditFieldFilter;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.state.shared.StateDoc;
import stroom.state.shared.StateType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.Message;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class StateDocStoreImpl implements StateDocStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateDocStoreImpl.class);

    private final Store<StateDoc> store;
    private final Provider<CqlSessionCache> cqlSessionCacheProvider;
    private final SecurityContext securityContext;

    @Inject
    public StateDocStoreImpl(
            final StoreFactory storeFactory,
            final StateDocSerialiser serialiser,
            final Provider<CqlSessionCache> cqlSessionCacheProvider,
            final SecurityContext securityContext) {
        this.store = storeFactory.createStore(serialiser, StateDoc.TYPE, StateDoc.class);
        this.cqlSessionCacheProvider = cqlSessionCacheProvider;
        this.securityContext = securityContext;
    }

    ////////////////////////////////////////////////////////////////////////
    // START OF ExplorerActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public DocRef createDocument(final String name) {
        validateName(name);

        final DocRef created = store.createDocument(name);

        // Double-check the feed wasn't created elsewhere at the same time.
        if (checkDuplicateName(name, created)) {
            // Delete the newly created document as the name is duplicated.

            // Delete as a processing user to ensure we are allowed to delete the item as documents do not have
            // permissions added to them until after they are created in the store.
            securityContext.asProcessingUser(() -> store.deleteDocument(created));
            throwNameException(name);
        }

        // Set the default keyspace.
        final StateDoc doc = store.readDocument(created);
        doc.setStateType(StateType.TEMPORAL_STATE);
        doc.setRetainForever(true);
        store.writeDocument(doc);

        return created;
    }

    private void validateName(final String name) {
        if (!ScyllaDbNameValidator.isValidName(name)) {
            throw new EntityServiceException("The state store name must match the pattern '" +
                    ScyllaDbNameValidator.getPattern() +
                    "'");
        }
    }

    private void throwNameException(final String name) {
        throw new EntityServiceException("A state store named '" + name + "' already exists");
    }

    private boolean checkDuplicateName(final String name, final DocRef whitelistDocRef) {
        final List<DocRef> list = list();
        for (final DocRef docRef : list) {
            if (name.equals(docRef.getName()) &&
                    (whitelistDocRef == null || !whitelistDocRef.equals(docRef))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DocRef copyDocument(final DocRef docRef,
                               final String name,
                               final boolean makeNameUnique,
                               final Set<String> existingNames) {
        String newName = name;
        if (makeNameUnique) {
            newName = createUniqueName(name, getExistingNames());
        } else if (checkDuplicateName(name, null)) {
            throwNameException(name);
        }

        return store.copyDocument(docRef.getUuid(), newName);
    }

    private Set<String> getExistingNames() {
        return list()
                .stream()
                .map(DocRef::getName)
                .collect(Collectors.toSet());
    }

    static String createUniqueName(final String name, final Set<String> existingNames) {
        // Get a numbered suffix.
        final char[] chars = name.toCharArray();
        int index = -1;
        for (int i = chars.length - 1; i >= 0; i--) {
            final char c = chars[i];
            if (!Character.isDigit(c)) {
                index = i + 1;
                break;
            }
        }

        String prefix = name.substring(0, index);
        String suffix = name.substring(index);
        int num = 2;
        if (!suffix.isEmpty()) {
            num = Integer.parseInt(suffix) + 1;
        }

        for (int i = num; i < 10000; i++) {
            suffix = String.valueOf(i);
            final int maxPrefixLength = 48 - suffix.length();
            if (prefix.length() > maxPrefixLength) {
                prefix = prefix.substring(0, maxPrefixLength);
            }
            final String copyName = prefix + suffix;
            if (!existingNames.contains(copyName)) {
                return copyName;
            }
        }

        throw new EntityServiceException("Unable to make unique name for state store.");
    }

    @Override
    public DocRef moveDocument(final DocRef docRef) {
        return store.moveDocument(docRef);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        validateName(name);

        // Check a state store doesn't already exist with this name.
        if (checkDuplicateName(name, docRef)) {
            throw new EntityServiceException("A state store named '" + name + "' already exists");
        }

        return store.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        // Drop the associated ScyllaDB table before deleting the document.
        final StateDoc doc = readDocument(docRef);
        if (doc != null) {
            try {
                final CqlSessionCache sessionCache = cqlSessionCacheProvider.get();
                final CqlSession session = sessionCache.get(doc.getScyllaDbRef());
                ScyllaDbUtil.dropTable(session, doc.getName());
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }

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
    // START OF DocumentActionHandler
    ////////////////////////////////////////////////////////////////////////

    @Override
    public StateDoc readDocument(final DocRef docRef) {
        return store.readDocument(docRef);
    }

    @Override
    public StateDoc writeDocument(final StateDoc document) {
        validateName(document.getName());
        return store.writeDocument(document);
    }

    ////////////////////////////////////////////////////////////////////////
    // END OF DocumentActionHandler
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
        return store.importDocument(docRef, dataMap, importState, importSettings);
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
    public List<DocRef> list() {
        return store.list();
    }

    @Override
    public List<DocRef> findByNames(final List<String> name, final boolean allowWildCards) {
        return store.findByNames(name, allowWildCards);
    }

    @Override
    public Map<String, String> getIndexableData(final DocRef docRef) {
        return store.getIndexableData(docRef);
    }
}
