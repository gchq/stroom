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
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemovePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.security.shared.BulkDocumentPermissionChangeRequest;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.PermissionChangeRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class UserDocumentPermissionsCache implements PermissionChangeEvent.Handler, Clearable {

    private static final String CACHE_NAME = "User Document Permissions Cache";

    private final LoadingStroomCache<UserRef, UserDocumentPermissions> cache;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final Provider<DocumentPermissionDao> documentPermissionDaoProvider,
                                        final Provider<AuthorisationConfig> authorisationConfigProvider) {
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserDocumentPermissionsCache(),
                userRef -> documentPermissionDaoProvider.get().getPermissionsForUser(userRef.getUuid()));
    }

    boolean hasDocumentPermission(final UserRef userRef,
                                  final DocRef docRef,
                                  final DocumentPermission permission) {
        final UserDocumentPermissions userDocumentPermissions = get(userRef);
        if (userDocumentPermissions != null) {
            return userDocumentPermissions.hasDocumentPermission(docRef, permission);
        }
        return false;
    }

    private UserDocumentPermissions get(final UserRef userRef) {
        return cache.get(userRef);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final PermissionChangeRequest request) {
        if (cache != null) {
            AbstractDocumentPermissionsChange req = null;

            if (request instanceof final SingleDocumentPermissionChangeRequest change) {
                req = change.getChange();
            } else if (request instanceof final BulkDocumentPermissionChangeRequest change) {
                req = change.getChange();
            }

            if (req != null) {
                if (req instanceof final SetPermission change) {
                    cache.invalidate(change.getUserRef());
                } else if (req instanceof final RemovePermission change) {
                    cache.invalidate(change.getUserRef());
                } else if (req instanceof final AddAllPermissionsFrom change) {
                    cache.clear();
                } else if (req instanceof final SetAllPermissionsFrom change) {
                    cache.clear();
                } else if (req instanceof final RemoveAllPermissions change) {
                    cache.clear();
                }
            }
        }
    }
}
