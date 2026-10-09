/*
 * Copyright 2026 Crown Copyright
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

package stroom.floormap.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.floormap.shared.FloorMapEventExpiry;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.PlanBNameValidator;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.RetentionSettings;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// The document store for [FloorMapEventStoreDoc].
///
/// Mirrors `PlanBDocStoreImpl` where the rules are Plan B's, because the store this document
/// describes *is* a Plan B store and is reached by the same machinery. Two things are worth
/// stating:
///
/// **The name is a pipeline identifier, not a label.** `PlanBFilter` lowercases the map
/// name it is given and `DocFinder.findByName` is case-sensitive, so a store named
/// `Events` can never be resolved by ingest. The only symptom is one line in a stream's error
/// log, which is a poor way to learn it — so the same `PlanBNameValidator` pattern Plan B
/// enforces is enforced here, at creation rather than at first write.
///
/// **`maxStoreSize` is deliberately unchecked.** Lowering it below what is already
/// written cannot take effect, so a guard looks attractive — but `createDocument` persists Plan
/// B's 10 GiB default, and after construction a default nobody chose is indistinguishable from a
/// value someone did. A guard would therefore refuse a user's first, perfectly reasonable, reduction.
/// Plan B does not check it either, and this store is not the place to invent the rule.
@Singleton
public class FloorMapEventStoreStoreImpl
        extends AbstractDocumentStore<FloorMapEventStoreDoc>
        implements FloorMapEventStoreStore {

    private final Provider<ClusterLockService> clusterLockServiceProvider;

    @Inject
    public FloorMapEventStoreStoreImpl(
            final StoreFactory storeFactory,
            final FloorMapEventStoreSerialiser serialiser,
            final SecurityContext securityContext,
            final Provider<ClusterLockService> clusterLockServiceProvider) {
        super(storeFactory,
                securityContext,
                serialiser,
                FloorMapEventStoreDoc.TYPE,
                FloorMapEventStoreDoc::eventStoreBuilder,
                FloorMapEventStoreDoc::copyEventStore);
        this.clusterLockServiceProvider = clusterLockServiceProvider;
    }

    @Override
    public DocRef createDocument(final String name) {
        validateName(name);

        final DocRef created = getStore().createDocument(name,
                (uuid, docName, version, createTime, updateTime, createUser, updateUser) ->
                        FloorMapEventStoreDoc
                                .eventStoreBuilder()
                                .uuid(uuid)
                                .name(docName)
                                .version(version)
                                .createTimeMs(createTime)
                                .updateTimeMs(updateTime)
                                .createUser(createUser)
                                .updateUser(updateUser)
                                .build());

        // Double-check no store with this name was created elsewhere at the same time.
        if (checkDuplicateName(name, created)) {
            // Delete the newly created document as the name is duplicated. getStore() is the
            // deliberately unchecked handle, which is what undoing our own create needs: the document
            // has no permissions yet, and the authority to remove it is that we just made it.
            getStore().deleteDocument(created);
            throwNameException(name);
        }

        return created;
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

        // Copy reads the source document, so it needs VIEW on it. This override reaches getStore()
        // directly, which is the unchecked handle, so the check the base applies is applied here.
        checkDocumentPermission(docRef, DocumentPermission.VIEW);
        return getStore().copyDocument(docRef.getUuid(), newName);
    }

    @Override
    public DocRef renameDocument(final DocRef docRef, final String name) {
        validateName(name);
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
                clusterLockServiceProvider.get()
                        .deleteLocks(PlanBConstants.getMergeLockPrefix(docRef.getUuid()));
            } catch (final Exception e) {
                // Ignore lock deletion failures on document delete to avoid failing the delete itself.
            }
        }
    }

    @Override
    public FloorMapEventStoreDoc writeDocument(final FloorMapEventStoreDoc document) {
        validateName(document.getName());
        validateSettings(document);
        validateExpiryWithinRetention(document);

        return super.writeDocument(document);
    }

    private void validateName(final String name) {
        if (!PlanBNameValidator.isValidName(name)) {
            throw new EntityServiceException("The event store name must match the pattern '" +
                                             PlanBNameValidator.getPattern() +
                                             "'");
        }
    }

    private void throwNameException(final String name) {
        throw new EntityServiceException("An event store named '" + name + "' already exists");
    }

    private boolean checkDuplicateName(final String name, final DocRef whitelistDocRef) {
        for (final DocRef docRef : list()) {
            if (name.equals(docRef.getName()) &&
                (whitelistDocRef == null || !whitelistDocRef.equals(docRef))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getExistingNames() {
        return list()
                .stream()
                .map(DocRef::getName)
                .collect(Collectors.toSet());
    }

    static String createUniqueName(final String name, final Set<String> existingNames) {
        // Split a trailing number off the name so a copy increments it rather than appending to it.
        final char[] chars = name.toCharArray();
        int index = -1;
        for (int i = chars.length - 1; i >= 0; i--) {
            if (!Character.isDigit(chars[i])) {
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

        throw new EntityServiceException("Unable to make a unique name for the event store.");
    }

    private void validateSettings(final FloorMapEventStoreDoc document) {
        final String error = AbstractPlanBSettings.validationError(
                NullSafe.get(document, FloorMapEventStoreDoc::getSettings));
        if (error != null) {
            throw new EntityServiceException(error);
        }
    }

    /// Expiry may not outlive retention.
    ///
    /// Expiry hides an entity whose last event is older than the cutoff; retention *deletes*
    /// data older than its own. Set expiry longer than retention and the map silently under-reports:
    /// an entity idle for longer than retention is dropped even though the expiry rule says to show
    /// it, and the symptom — a missing entity — looks like a data problem rather than a configuration
    /// one. The two settings live on this one document precisely so this can be checked.
    private void validateExpiryWithinRetention(final FloorMapEventStoreDoc document) {
        if (expiryExceedsRetention(document)) {
            final RetentionSettings retention = NullSafe.get(
                    document.getSettings(),
                    AbstractPlanBSettings::getRetention);
            throw new EntityServiceException(
                    "Event expiry (" + document.getEventExpiryOrDefault() +
                    ") is longer than the retention period (" + retention.getDuration() +
                    "). Entities would be dropped from the map before they expire, because the data " +
                    "needed to show them has already been deleted.");
        }
    }

    /// Whether the document asks to show entities for longer than it keeps the data.
    ///
    /// Package-private and free of the store's collaborators so the rule can be tested directly —
    /// it is the one piece of judgement in this class, and the failure it prevents is silent.
    static boolean expiryExceedsRetention(final FloorMapEventStoreDoc document) {
        final RetentionSettings retention = NullSafe.get(
                document.getSettings(),
                AbstractPlanBSettings::getRetention);
        if (retention == null || !retention.isEnabled() || retention.getDuration() == null) {
            return false;
        }

        final long retentionMs = retention.getDuration().getApproxMillis();
        return retentionMs > 0 && FloorMapEventExpiry.millis(document.getEventExpiry()) > retentionMs;
    }

    @Override
    public List<DocRef> list() {
        return super.list();
    }
}
