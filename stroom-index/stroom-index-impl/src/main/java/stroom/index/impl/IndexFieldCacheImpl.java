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
 *
 */

package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
public class IndexFieldCacheImpl implements IndexFieldCache, Clearable {

    private static final String CACHE_NAME = "Index Field Cache";

    private final Map<String, IndexFieldProvider> providers = new HashMap<>();
    private final LoadingStroomCache<Key, IndexField> cache;
    private final SecurityContext securityContext;

    @Inject
    IndexFieldCacheImpl(final CacheManager cacheManager,
                        final Provider<IndexConfig> indexConfigProvider,
                        final Set<IndexFieldProvider> indexFieldProviders,
                        final SecurityContext securityContext) {
        this.securityContext = securityContext;
        for (final IndexFieldProvider provider : indexFieldProviders) {
            providers.put(provider.getType(), provider);
        }
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexConfigProvider.get().getIndexFieldCache(),
                this::create);
    }

    private IndexField create(final Key key) {
        return securityContext.asProcessingUserResult(() -> {
            final IndexFieldProvider provider = providers.get(key.docRef.getType());
            if (provider == null) {
                throw new NullPointerException("No provider can be found for: " + key.docRef.getType());
            }

            return provider.getIndexField(key.docRef, key.fieldName);
        });
    }

    @Override
    public IndexField get(final DocRef docRef, final String fieldName) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");
        Objects.requireNonNull(docRef.getType(), "Null DocRef type supplied");
        Objects.requireNonNull(fieldName, "Null field name supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermissionNames.USE)) {
            throw new PermissionException(
                    securityContext.getUserIdentityForAudit(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        final Key key = new Key(docRef, fieldName);
        return cache.get(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class Key {

        private final DocRef docRef;
        private final String fieldName;

        public Key(final DocRef docRef, final String fieldName) {
            this.docRef = docRef;
            this.fieldName = fieldName;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equals(docRef, key.docRef) && Objects.equals(fieldName, key.fieldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(docRef, fieldName);
        }
    }
}
