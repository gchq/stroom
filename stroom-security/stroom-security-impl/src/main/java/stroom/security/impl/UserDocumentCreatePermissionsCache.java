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
import stroom.security.impl.event.AddDocumentCreatePermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.RemoveDocumentCreatePermissionEvent;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

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
                .getDocumentCreatePermissions(key.docUuid, key.userUuid);
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
    public void onChange(final PermissionChangeEvent event) {
        if (createPermissionsCache != null) {
            if (event instanceof final AddDocumentCreatePermissionEvent addPermissionEvent) {
                final UserDocKey userDocKey = new UserDocKey(
                        addPermissionEvent.getUserRef().getUuid(),
                        addPermissionEvent.getFolderRef().getUuid());
                createPermissionsCache.invalidate(userDocKey);

            } else if (event instanceof final RemoveDocumentCreatePermissionEvent removePermissionEvent) {
                final UserDocKey userDocKey = new UserDocKey(
                        removePermissionEvent.getUserRef().getUuid(),
                        removePermissionEvent.getFolderRef().getUuid());
                createPermissionsCache.invalidate(userDocKey);

            } else if (event instanceof final ClearDocumentPermissionsEvent clearDocumentPermissionsEvent) {
                createPermissionsCache.forEach((key, userDocumentPermissions) -> {
                    if (userDocumentPermissions != null) {
                        if (Objects.equals(key.docUuid, clearDocumentPermissionsEvent.getDocRef().getUuid())) {
                            createPermissionsCache.invalidate(key);
                        }
                    }
                });
            }
        }
    }

    private record UserDocKey(String userUuid, String docUuid) {

    }
}
