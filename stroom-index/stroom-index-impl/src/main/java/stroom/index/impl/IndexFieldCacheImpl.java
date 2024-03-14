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
import stroom.index.shared.IndexFieldCache;
import stroom.index.shared.IndexFieldProvider;
import stroom.util.shared.Clearable;

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

    @Inject
    IndexFieldCacheImpl(final CacheManager cacheManager,
                        final Provider<IndexConfig> indexConfigProvider,
                        final Set<IndexFieldProvider> indexFieldProviders) {
        for (final IndexFieldProvider provider : indexFieldProviders) {
            providers.put(provider.getType(), provider);
        }
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> indexConfigProvider.get().getIndexStructureCache(),
                this::create);
    }

    private IndexField create(final Key key) {
        if (key == null || key.docRef == null || key.docRef.getType() == null) {
            throw new NullPointerException("Null key supplied");
        }

        final IndexFieldProvider provider = providers.get(key.docRef.getType());
        if (provider == null) {
            throw new NullPointerException("No provider can be found for: " + key.docRef.getType());
        }

        return provider.getIndexField(key.docRef, key.fieldName);
    }

    @Override
    public IndexField get(final DocRef docRef, final String fieldName) {
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
