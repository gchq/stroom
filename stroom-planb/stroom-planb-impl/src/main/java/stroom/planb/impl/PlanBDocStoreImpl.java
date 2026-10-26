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

package stroom.planb.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class PlanBDocStoreImpl
        extends AbstractDocumentStore<PlanBDoc>
        implements PlanBDocStore {

    private final SecurityContext securityContext;
    private final Provider<ClusterLockService> clusterLockServiceProvider;
    private final PlanBStoreNames planBStoreNames;

    @Inject
    public PlanBDocStoreImpl(
            final StoreFactory storeFactory,
            final PlanBDocSerialiser serialiser,
            final SecurityContext securityContext,
            final Provider<ClusterLockService> clusterLockServiceProvider,
            final PlanBStoreNames planBStoreNames) {
        super(storeFactory,
                securityContext,
                serialiser,
                PlanBDoc.TYPE,
                PlanBDoc::builder,
                PlanBDoc::copy);
        this.securityContext = securityContext;
        this.clusterLockServiceProvider = clusterLockServiceProvider;
        this.planBStoreNames = planBStoreNames;
    }

    /**
     * Create a state store, defaulting its state type.
     * <p>
     * The default is applied to the document SKELETON rather than by reading the new document back and
     * writing it again. A second write is authorised against a document whose permissions are not
     * attached until after this method returns ({@code ExplorerServiceImpl.create} calls
     * {@code createNode} afterwards), so it succeeds only because {@code hasDocumentPermission} lets
     * an administrator through before consulting any permission row — a non-admin creating a state
     * store is refused.
     */
    @Override
    public DocRef createDocument(final String name) {
        validateName(name);

        final DocRef created = getStore().createDocument(name,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) -> PlanBDoc
                        .builder()
                        .uuid(uuid)
                        .name(docName)
                        .version(version)
                        .createTimeMs(createTime)
                        .updateTimeMs(updateTime)
                        .createUser(createUser)
                        .updateUser(updateUser)
                        .stateType(StateType.TEMPORAL_STATE)
                        .build());

        // Double-check no state store with this name was created elsewhere at the same time.
        if (checkDuplicateName(name, created)) {
            // Delete the newly created document as the key is duplicated. getStore() is the
            // deliberately unchecked handle, which is what undoing our own create needs: the document
            // has no permissions yet, and the authority to remove it is that we just made it.
            getStore().deleteDocument(created);
            throwNameException(name);
        }

        return created;
    }

    private void validateName(final String name) {
        if (!PlanBNameValidator.isValidName(name)) {
            throw new EntityServiceException("The state store key must match the pattern '" +
                                             PlanBNameValidator.getPattern() +
                                             "'");
        }
    }

    private void throwNameException(final String name) {
        throw new EntityServiceException(nameTakenMessage(name));
    }

    private static String nameTakenMessage(final String name) {
        return "A state store or another Plan B store named '" + name + "' already exists";
    }

    /// Whether a store other than `whitelistDocRef` already has this name, among state stores and
    /// every other Plan B store type - see [PlanBStoreNames].
    ///
    /// State stores are listed through [#getStore()], the unchecked handle, rather than [#list()],
    /// which leaves out the stores the caller cannot see: a clash with one of those breaks name
    /// resolution just the same.
    private boolean checkDuplicateName(final String name, final DocRef whitelistDocRef) {
        final List<DocRef> docRefs = getStore().list();
        for (final DocRef docRef : docRefs) {
            if (name.equals(docRef.getName()) &&
                (whitelistDocRef == null || !whitelistDocRef.equals(docRef))) {
                return true;
            }
        }
        return planBStoreNames.isTakenByAnotherType(name, PlanBDoc.TYPE);
    }

    /// Imports a state store, refusing a new one whose name another Plan B store already has.
    ///
    /// An existing store keeps its own name on import, so only a new one can bring a clashing name in.
    /// It is refused with an error against its import item rather than imported, because Plan B would
    /// then be unable to resolve either store.
    @Override
    public DocRef importDocument(final DocRef docRef,
                                 final ImportExportDocument importExportDocument,
                                 final ImportState importState,
                                 final ImportSettings importSettings) {
        final String name = NullSafe.get(docRef, DocRef::getName);
        if (name != null && !getStore().exists(docRef) && checkDuplicateName(name, docRef)) {
            importState.addMessage(Severity.ERROR, nameTakenMessage(name) + ", so this one cannot be imported");
            return docRef;
        }
        return super.importDocument(docRef, importExportDocument, importState, importSettings);
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

        // Copy reads the source document, so it needs VIEW on it. This override reaches
        // getStore() directly, which is the unchecked handle, so the check the base applies is
        // applied here.
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        return getStore().copyDocument(docRef.getUuid(), newName);
    }

    // Every Plan B store's name, whether or not the caller can see the store - see checkDuplicateName.
    private Set<String> getExistingNames() {
        final Set<String> names = getStore().list()
                .stream()
                .map(DocRef::getName)
                .collect(Collectors.toCollection(HashSet::new));
        names.addAll(planBStoreNames.namesOfOtherTypes(PlanBDoc.TYPE));
        return names;
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

        throw new EntityServiceException("Unable to make unique key for state store.");
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        validateName(name);

        // Check a state store doesn't already exist with this key.
        if (checkDuplicateName(name, docRef)) {
            throwNameException(name);
        }

        return super.renameDocument(docRef, name);
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
        super.deleteDocument(docRef);
        if (docRef != null && docRef.getUuid() != null) {
            try {
                clusterLockServiceProvider.get().deleteLocks(PlanBConstants.getMergeLockPrefix(docRef.getUuid()));
            } catch (final Exception e) {
                // Ignore lock deletion failures on document delete to avoid failing document delete itself
            }
        }
    }

    @Override
    public PlanBDoc writeDocument(final PlanBDoc document) {
        validateName(document.getName());
        validateSettings(document);

        return super.writeDocument(document);
    }

    private void validateSettings(final PlanBDoc document) {
        final String error = AbstractPlanBSettings.validationError(
                NullSafe.get(document, PlanBDoc::getSettings));
        if (error != null) {
            throw new EntityServiceException(error);
        }
    }
}
