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
import stroom.security.impl.event.AddPermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.RemovePermissionEvent;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Set;

@Singleton
public class DocumentOwnerPermissionsCache implements PermissionChangeEvent.Handler, Clearable {

    static final String CACHE_NAME = "Document Owner Permissions Cache";

    private final LoadingStroomCache<String, Set<String>> cache;

    @Inject
    public DocumentOwnerPermissionsCache(final CacheManager cacheManager,
                                         final DocumentPermissionDao documentPermissionDao,
                                         final Provider<AuthorisationConfig> authorisationConfigProvider) {
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> authorisationConfigProvider.get().getUserDocumentPermissionsCache(),
                documentPermissionDao::getDocumentOwnerUuids);
    }

    /**
     * @param documentUuid The UUID of the document to check ownership of.
     * @return A set of stroom user UUIDs who have Owner permission on the document
     * with the passed document UUID
     */
    Set<String> get(final String documentUuid) {
        Objects.requireNonNull(documentUuid, "No documentUuid provided");
        return cache.get(documentUuid);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (cache != null) {
            if (event instanceof final AddPermissionEvent addPermissionEvent) {
                if (addPermissionEvent.getDocumentUuid() != null) {
                    cache.invalidate(addPermissionEvent.getDocumentUuid());
                }
            } else if (event instanceof final RemovePermissionEvent removePermissionEvent) {
                if (removePermissionEvent.getDocumentUuid() != null) {
                    cache.invalidate(removePermissionEvent.getDocumentUuid());
                }
            } else if (event instanceof ClearDocumentPermissionsEvent) {
                cache.clear();
            }
        }
    }
}
