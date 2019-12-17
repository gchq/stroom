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

package stroom.security.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.security.api.DocumentPermissionCache;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

@Singleton
class DocumentPermissionCacheImpl implements DocumentPermissionCache, Clearable {
    private static final String CACHE_NAME = "Document Permission Cache";

    private final SecurityContext securityContext;
    private final ICache<DocumentPermission, Boolean> cache;

    @Inject
    DocumentPermissionCacheImpl(final CacheManager cacheManager,
                                final SecurityContext securityContext,
                                final AuthorisationConfig authorisationConfig) {
        this.securityContext = securityContext;
        cache = cacheManager.create(CACHE_NAME, authorisationConfig::getDocumentPermissionCache, this::create);
    }

    private boolean create(final DocumentPermission k) {
        return securityContext.insecureResult(() ->
                securityContext.hasDocumentPermission(k.documentType, k.documentUuid, k.permission));
    }

    @Override
    public boolean hasDocumentPermission(final String documentType, final String documentUuid, final String permission) {
        final DocumentPermission documentPermission = new DocumentPermission(securityContext.getUserId(), documentType, documentUuid, permission);
        return cache.get(documentPermission);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class DocumentPermission {
        private final String userId;
        private final String documentType;
        private final String documentUuid;
        private final String permission;

        DocumentPermission(final String userId, final String documentType, final String documentUuid, final String permission) {
            this.userId = userId;
            this.documentType = documentType;
            this.documentUuid = documentUuid;
            this.permission = permission;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final DocumentPermission that = (DocumentPermission) o;
            return Objects.equals(userId, that.userId) &&
                    Objects.equals(documentType, that.documentType) &&
                    Objects.equals(documentUuid, that.documentUuid) &&
                    Objects.equals(permission, that.permission);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, documentType, documentUuid, permission);
        }
    }
}
