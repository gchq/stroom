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

package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.BitSet;

@Singleton
public class UserDocumentCreatePermissionsCache implements PermissionChangeEvent.Handler, Clearable {

    private static final String CACHE_NAME = "User Document Create Permissions Cache";

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
        return documentPermissionDaoProvider.get()
                .getDocumentUserCreatePermissionsBitSet(key.docUuid, key.userUuid);
    }

    boolean hasDocumentCreatePermission(final UserRef userRef,
                                        final DocRef folderRef,
                                        final String documentType) {
        final UserDocKey userDocKey = new UserDocKey(userRef.getUuid(), folderRef.getUuid());
        final BitSet bitSet = createPermissionsCache.get(userDocKey);
        final int documentTypeId = docTypeIdDaoProvider.get().getOrCreateId(documentType);
        if (bitSet.size() > documentTypeId) {
            return bitSet.get(documentTypeId);
        }
        return false;
    }

    @Override
    public void clear() {
        createPermissionsCache.clear();
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (createPermissionsCache != null) {
            if (event.getUserRef() != null && event.getDocRef() != null) {
                createPermissionsCache.invalidate(new UserDocKey(
                        event.getUserRef().getUuid(),
                        event.getDocRef().getUuid()));
            } else if (event.getUserRef() != null) {
                createPermissionsCache.keySet().forEach(key -> {
                    if (key.userUuid().equals(event.getUserRef().getUuid())) {
                        createPermissionsCache.invalidate(key);
                    }
                });
            } else if (event.getDocRef() != null) {
                createPermissionsCache.keySet().forEach(key -> {
                    if (key.docUuid().equals(event.getDocRef().getUuid())) {
                        createPermissionsCache.invalidate(key);
                    }
                });
            } else {
                createPermissionsCache.clear();
            }
        }
    }

    private record UserDocKey(String userUuid, String docUuid) {

    }
}
