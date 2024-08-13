/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.PermissionChangeRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.BitSet;
import java.util.List;

@Singleton
public class UserDocumentCreatePermissionsCache implements PermissionChangeEvent.Handler, Clearable {

    private static final String CACHE_NAME = "User Document Create Permissions Cache";

    private static final BitSet EMPTY = new BitSet(0);

    private final Provider<DocumentPermissionDao> documentPermissionDaoProvider;
    private final Provider<DocTypeIdDao> docTypeIdDaoProvider;
    private final LoadingStroomCache<UserDocKey, BitSet> createPermissionsCache;

    @Inject
    public UserDocumentCreatePermissionsCache(final CacheManager cacheManager,
                                              final Provider<DocumentPermissionDao> documentPermissionDaoProvider,
                                              final Provider<DocTypeIdDao> docTypeIdDaoProvider,
                                              final Provider<AuthorisationConfig> authorisationConfigProvider) {
        this.documentPermissionDaoProvider = documentPermissionDaoProvider;
        this.docTypeIdDaoProvider = docTypeIdDaoProvider;
        createPermissionsCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserDocumentPermissionsCache(),
                this::create);
    }

    private BitSet create(final UserDocKey key) {
        final List<Integer> list = documentPermissionDaoProvider.get()
                .getDocumentUserCreatePermissions(key.docUuid, key.userUuid);
        if (list.isEmpty()) {
            return EMPTY;
        }

        final int max = list.stream().mapToInt(v -> v).max().orElseThrow();
        // Note that the bit set is created after we fetch the permissions just to be sure that the max id is correct.
        final BitSet bitSet = new BitSet(max);
        list.forEach(i -> bitSet.set(i, true));
        return bitSet;
    }

    boolean hasDocumentCreatePermission(final UserRef userRef,
                                        final DocRef folderRef,
                                        final String documentType) {
        final UserDocKey userDocKey = new UserDocKey(userRef.getUuid(), folderRef.getUuid());
        final BitSet bitSet = createPermissionsCache.get(userDocKey);
        if (bitSet != null) {
            final int documentTypeId = docTypeIdDaoProvider.get().getOrCreateId(documentType);
            if (bitSet.size() > documentTypeId) {
                return bitSet.get(documentTypeId);
            }
        }
        return false;
    }

    @Override
    public void clear() {
        createPermissionsCache.clear();
    }

    @Override
    public void onChange(final PermissionChangeRequest request) {
        if (createPermissionsCache != null) {
            if (request instanceof final SingleDocumentPermissionChangeRequest
                    singleDocumentPermissionChangeRequest) {
                final AbstractDocumentPermissionsChange req = singleDocumentPermissionChangeRequest.getChange();
                final DocRef docRef = singleDocumentPermissionChangeRequest.getDocRef();

                if (request instanceof final AddDocumentCreatePermission change) {
                    final UserDocKey key = new UserDocKey(change.getUserRef().getUuid(), docRef.getUuid());
                    createPermissionsCache.invalidate(key);
                } else if (request instanceof final RemoveDocumentCreatePermission change) {
                    final UserDocKey key = new UserDocKey(change.getUserRef().getUuid(), docRef.getUuid());
                    createPermissionsCache.invalidate(key);
                } else if (request instanceof final AddAllDocumentCreatePermissions change) {
                    final UserDocKey key = new UserDocKey(change.getUserRef().getUuid(), docRef.getUuid());
                    createPermissionsCache.invalidate(key);
                } else if (request instanceof final RemoveAllDocumentCreatePermissions change) {
                    final UserDocKey key = new UserDocKey(change.getUserRef().getUuid(), docRef.getUuid());
                    createPermissionsCache.invalidate(key);
                } else if (request instanceof final AddAllPermissionsFrom change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final SetAllPermissionsFrom change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final RemoveAllPermissions change) {
                    createPermissionsCache.clear();
                }

            } else if (request instanceof final BulkDocumentPermissionChangeRequest
                    bulkDocumentPermissionChangeRequest) {
                final AbstractDocumentPermissionsChange req = bulkDocumentPermissionChangeRequest.getChange();

                if (request instanceof final AddDocumentCreatePermission change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final RemoveDocumentCreatePermission change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final AddAllDocumentCreatePermissions change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final RemoveAllDocumentCreatePermissions change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final AddAllPermissionsFrom change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final SetAllPermissionsFrom change) {
                    createPermissionsCache.clear();
                } else if (request instanceof final RemoveAllPermissions change) {
                    createPermissionsCache.clear();
                }
            }
        }
    }

    private record UserDocKey(String userUuid, String docUuid) {

    }
}
