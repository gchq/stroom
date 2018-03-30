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

package stroom.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.entity.shared.Clearable;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
class DocumentPermissionCacheImpl implements DocumentPermissionCache, Clearable {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final SecurityContext securityContext;
    private final LoadingCache<DocumentPermission, Boolean> cache;

    @Inject
    @SuppressWarnings("unchecked")
    DocumentPermissionCacheImpl(final CacheManager cacheManager,
                                final SecurityContext securityContext,
                                final Security security) {
        this.securityContext = securityContext;

        final CacheLoader<DocumentPermission, Boolean> cacheLoader = CacheLoader.from(k ->
                security.insecureResult(() ->
                        securityContext.hasDocumentPermission(k.documentType, k.documentUuid, k.permission)));
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Document Permission Cache", cacheBuilder, cache);
    }


    @Override
    public boolean hasDocumentPermission(final String documentType, final String documentUuid, final String permission) {
        final DocumentPermission documentPermission = new DocumentPermission(securityContext.getUserId(), documentType, documentUuid, permission);
        return cache.getUnchecked(documentPermission);
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
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

            if (userId != null ? !userId.equals(that.userId) : that.userId != null) return false;
            if (documentType != null ? !documentType.equals(that.documentType) : that.documentType != null)
                return false;
            if (documentUuid != null ? !documentUuid.equals(that.documentUuid) : that.documentUuid != null)
                return false;
            return permission != null ? permission.equals(that.permission) : that.permission == null;
        }

        @Override
        public int hashCode() {
            int result = userId != null ? userId.hashCode() : 0;
            result = 31 * result + (documentType != null ? documentType.hashCode() : 0);
            result = 31 * result + (documentUuid != null ? documentUuid.hashCode() : 0);
            result = 31 * result + (permission != null ? permission.hashCode() : 0);
            return result;
        }
    }
}
