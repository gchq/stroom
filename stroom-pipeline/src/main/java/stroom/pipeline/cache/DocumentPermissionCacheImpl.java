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

package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.pipeline.PipelineConfig;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.Clearable;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
class DocumentPermissionCacheImpl implements DocumentPermissionCache, Clearable {

    private static final String CACHE_NAME = "Document Permission Cache";

    private final SecurityContext securityContext;
    private final LoadingStroomCache<DocumentPermission, Boolean> cache;

    @Inject
    DocumentPermissionCacheImpl(final CacheManager cacheManager,
                                final SecurityContext securityContext,
                                final Provider<PipelineConfig> pipelineConfigProvider) {
        this.securityContext = securityContext;

        // We have no change handlers due to the complexity of the number of things that can affect this
        // cache, so keep the time short and expire after write, not access.
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getDocumentPermissionCache(),
                this::create);
    }

    private boolean create(final DocumentPermission documentPermission) {
        return securityContext.asUserResult(documentPermission.userIdentity, () ->
                securityContext.hasDocumentPermission(
                        documentPermission.documentUuid,
                        documentPermission.permission));
    }

    @Override
    public boolean canUseDocument(final String documentUuid) {
        return cache.get(new DocumentPermission(
                securityContext.getUserIdentity(),
                documentUuid,
                DocumentPermissionNames.USE));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class DocumentPermission {

        private final UserIdentity userIdentity;
        private final String documentUuid;
        private final String permission;

        DocumentPermission(final UserIdentity userIdentity, final String documentUuid, final String permission) {
            this.userIdentity = userIdentity;
            this.documentUuid = documentUuid;
            this.permission = permission;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final DocumentPermission that = (DocumentPermission) o;
            return Objects.equals(userIdentity, that.userIdentity) &&
                    Objects.equals(documentUuid, that.documentUuid) &&
                    Objects.equals(permission, that.permission);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userIdentity, documentUuid, permission);
        }
    }
}
