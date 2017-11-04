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

package stroom.index.server;

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.springframework.stereotype.Component;
import stroom.cache.Loader;
import stroom.entity.shared.DocRef;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.shared.IndexFields;
import stroom.query.shared.IndexFieldsMap;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
public class IndexConfigCacheImpl implements IndexConfigCache {
    private static final int MAX_CACHE_ENTRIES = 100;

    private final Cache<DocRef, IndexConfig> cache;

    @Inject
    IndexConfigCacheImpl(final CentralCacheManager cacheManager,
                         final IndexService indexService) {
        final Loader<DocRef, IndexConfig> loader = new Loader<DocRef, IndexConfig>() {
            @Override
            public IndexConfig load(final DocRef key) throws Exception {
                final Index loaded = indexService.loadByUuid(key.getUuid());
                if (loaded == null) {
                    throw new NullPointerException("No index can be found for: " + key);
                }

                // Create a map of index fields keyed by name.
                final IndexFields indexFields = loaded.getIndexFieldsObject();
                if (indexFields == null || indexFields.getIndexFields() == null || indexFields.getIndexFields().size() == 0) {
                    throw new IndexException("No index fields have been set for: " + key);
                }

                final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(indexFields);
                return new IndexConfig(loaded, indexFields, indexFieldsMap);
            }
        };

        final CacheConfiguration<DocRef, IndexConfig> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(DocRef.class, IndexConfig.class,
                ResourcePoolsBuilder.heap(MAX_CACHE_ENTRIES))
                .withExpiry(Expirations.timeToIdleExpiration(Duration.of(10, TimeUnit.MINUTES)))
                .withLoaderWriter(loader)
                .build();

        cache = cacheManager.createCache("Index Config Cache", cacheConfiguration);
    }

    @Override
    public IndexConfig get(final DocRef key) {
        return cache.get(key);
    }

    @Override
    public void remove(final DocRef key) {
        cache.remove(key);
    }
}
