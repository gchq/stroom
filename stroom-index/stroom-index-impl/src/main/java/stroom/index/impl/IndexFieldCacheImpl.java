/*
 * Copyright 2024 Crown Copyright
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

package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.IndexFieldMap;
import stroom.query.common.v2.IndexFieldProviders;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
public class IndexFieldCacheImpl implements IndexFieldCache, Clearable {

    private static final String CACHE_NAME = "Index Field Cache";

    private final IndexFieldProviders indexFieldProviders;
    private final LoadingStroomCache<Key, IndexFieldMap> cache;
    private final SecurityContext securityContext;

    @Inject
    IndexFieldCacheImpl(final CacheManager cacheManager,
                        final Provider<IndexConfig> indexConfigProvider,
                        final IndexFieldProviders indexFieldProviders,
                        final SecurityContext securityContext) {
        this.indexFieldProviders = indexFieldProviders;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexConfigProvider.get().getIndexFieldCache(),
                this::create);
    }

    private IndexFieldMap create(final Key key) {
        return securityContext.asProcessingUserResult(() ->
                indexFieldProviders.getIndexFields(key.docRef, key.fieldName));
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
        final IndexFieldMap indexFieldMap = cache.get(key);

        // Attempt to get an IndexField matching the user's input. This may throw if there are multiple
        // fields with the same name (ignoring case) and none match exactly with fieldName.
        if (indexFieldMap != null) {
            return indexFieldMap.getMatchingField(key.fieldName);
        } else {
            return null;
        }
    }

    @Override
    public void clear() {
        cache.clear();
    }


    // --------------------------------------------------------------------------------


    // Pkg private for testing
    static class Key {

        private final DocRef docRef;
        // field names are case-insensitive in stroom but may not be in the index provider
        private final CIKey fieldName;

        private int hash = 0;
        /**
         * Set to true if the hash has been calculated and found to be zero,
         * to distinguish from the default value for hash.
         */
        private boolean hashIsZero = false;

        public Key(final DocRef docRef, final String fieldName) {
            this.docRef = docRef;
            this.fieldName = CIKey.of(fieldName);
        }

        public DocRef getDocRef() {
            return docRef;
        }

        public String getFieldName() {
            return fieldName.get();
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final Key key = (Key) object;
            return Objects.equals(docRef, key.docRef)
                    && Objects.equals(fieldName, key.fieldName);
        }

        @Override
        public int hashCode() {
            int hash = this.hash;
            // Lazily cache the hash
            if (hash == 0 && !hashIsZero) {
                // Case-insensitive hash
                hash = Objects.hash(docRef, fieldName);
                if (hash == 0) {
                    hashIsZero = true;
                } else {
                    this.hash = hash;
                }
            }
            return hash;
        }

        @Override
        public String toString() {
            return "'" + fieldName + "' - " + docRef;
        }
    }
}
