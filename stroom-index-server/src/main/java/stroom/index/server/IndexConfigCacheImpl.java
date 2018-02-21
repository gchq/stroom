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

package stroom.index.server;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.index.shared.Index;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.DocRef;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class IndexConfigCacheImpl implements IndexConfigCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final LoadingCache<DocRef, IndexConfig> cache;

    @Inject
    @SuppressWarnings("unchecked")
    IndexConfigCacheImpl(final CacheManager cacheManager,
                         final IndexService indexService) {
        final CacheLoader<DocRef, IndexConfig> cacheLoader = CacheLoader.from(k -> {
            if (k == null) {
                throw new NullPointerException("Null key supplied");
            }

            final Index loaded = indexService.loadByUuid(k.getUuid());
            if (loaded == null) {
                throw new NullPointerException("No index can be found for: " + k);
            }

            // Create a map of index fields keyed by name.
            final IndexFields indexFields = loaded.getIndexFieldsObject();
            if (indexFields == null || indexFields.getIndexFields() == null || indexFields.getIndexFields().size() == 0) {
                throw new IndexException("No index fields have been set for: " + k);
            }

            final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexFields);
            return new IndexConfig(loaded, indexFields, indexFieldsMap);
        });

        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Index Config Cache", cacheBuilder, cache);
    }

    @Override
    public IndexConfig get(final DocRef key) {
        return cache.getUnchecked(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }
}
