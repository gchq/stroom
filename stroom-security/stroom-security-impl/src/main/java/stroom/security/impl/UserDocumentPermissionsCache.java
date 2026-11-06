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
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class UserDocumentPermissionsCache implements PermissionChangeEvent.Handler, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UserDocumentPermissionsCache.class);

    private static final String CACHE_NAME = "User Document Permissions Cache";

    private final LoadingStroomCache<UserRef, UserDocumentPermissions> cache;
    private final Provider<DocumentPermissionDao> documentPermissionDaoProvider;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final Provider<DocumentPermissionDao> documentPermissionDaoProvider,
                                        final Provider<AuthorisationConfig> authorisationConfigProvider) {
        this.documentPermissionDaoProvider = documentPermissionDaoProvider;
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserDocumentPermissionsCache(),
                this::load);
    }

    boolean hasDocumentPermission(final UserRef userRef,
                                  final DocRef docRef,
                                  final DocumentPermission permission) {
        return cache.get(userRef)
                .hasDocumentPermission(docRef, permission);
    }

    private UserDocumentPermissions load(final UserRef userRef) {
        final UserDocumentPermissions perms = documentPermissionDaoProvider.get().getPermissionsForUser(
                userRef.getUuid());
        LOGGER.debug("load() - userRef: {}, perms: {}", userRef, perms);
        return perms;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(@NonNull final PermissionChangeEvent event) {
        final UserRef userRef = event.getUserRef();
        if (userRef != null) {
            LOGGER.debug("onChange() - Invalidating userRef: {}", userRef);
            cache.invalidate(userRef);
        } else {
            // No user so have no choice but to clear the whole cache

            // Capture the users in the cache prior to clearing, so we can build reload them
            final Set<UserRef> usersInCache = new HashSet<>(cache.keySet());

            LOGGER.debug("onChange() - Clearing cache");
            cache.clear();
            // Manually re-load all the users that were in the cache, to save
            // the cache from having to re-load each one individually.
            if (!usersInCache.isEmpty()) {
                final Map<UserRef, UserDocumentPermissions> permissionsForUsers = documentPermissionDaoProvider.get()
                        .getPermissionsForUsers(usersInCache);
                permissionsForUsers.forEach(cache::put);
                LOGGER.debug(() -> LogUtil.message(
                        "onChange() - Loaded {} entries into the cache", permissionsForUsers.size()));
            }
        }
    }
}
